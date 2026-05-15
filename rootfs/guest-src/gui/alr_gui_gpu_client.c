#include <arpa/inet.h>
#include <errno.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

static const char* env_or_default(const char* name, const char* fallback) {
    const char* value = getenv(name);
    return (value != 0 && value[0] != 0) ? value : fallback;
}

static int env_int_or_default(const char* name, int fallback) {
    const char* value = getenv(name);
    if (value == 0 || value[0] == 0) return fallback;
    int parsed = atoi(value);
    return parsed > 0 ? parsed : fallback;
}

static const char* program_protocol(const char* argv0) {
    const char* env_protocol = getenv("ALR_GUI_BRIDGE_PROTOCOL");
    if (env_protocol != 0 && strcmp(env_protocol, "X11") == 0) return "X11";
    if (env_protocol != 0 && strcmp(env_protocol, "WAYLAND") == 0) return "WAYLAND";
    return (argv0 != 0 && strstr(argv0, "x11") != 0) ? "X11" : "WAYLAND";
}

static int connect_loopback(const char* host, int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    struct sockaddr_in address;
    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = htons((unsigned short)port);
    if (inet_pton(AF_INET, host, &address.sin_addr) != 1) {
        close(fd);
        return -1;
    }
    if (connect(fd, (struct sockaddr*)&address, sizeof(address)) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static int connect_unix_socket(const char* socket_name) {
    if (socket_name == 0 || socket_name[0] == 0) return -1;
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    size_t name_len = strlen(socket_name);
    socklen_t address_len = 0;
    if (socket_name[0] == '@') {
        if (name_len - 1 >= sizeof(address.sun_path)) {
            close(fd);
            return -1;
        }
        address.sun_path[0] = '\0';
        memcpy(address.sun_path + 1, socket_name + 1, name_len - 1);
        address_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + name_len);
    } else {
        if (name_len >= sizeof(address.sun_path)) {
            close(fd);
            return -1;
        }
        memcpy(address.sun_path, socket_name, name_len + 1);
        address_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + name_len + 1);
    }
    if (connect(fd, (struct sockaddr*)&address, address_len) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static int write_all(int fd, const char* text) {
    size_t remaining = strlen(text);
    while (remaining > 0) {
        ssize_t written = write(fd, text, remaining);
        if (written <= 0) return 0;
        text += written;
        remaining -= (size_t)written;
    }
    return 1;
}

static int read_ack_line(int fd, char* buffer, size_t capacity) {
    if (buffer == 0 || capacity < 2) return 0;
    size_t used = 0;
    while (used + 1 < capacity) {
        fd_set read_set;
        FD_ZERO(&read_set);
        FD_SET(fd, &read_set);
        struct timeval timeout;
        timeout.tv_sec = 1;
        timeout.tv_usec = 0;
        int ready = select(fd + 1, &read_set, 0, 0, &timeout);
        if (ready <= 0) break;
        char ch = 0;
        ssize_t count = read(fd, &ch, 1);
        if (count <= 0) break;
        buffer[used++] = ch;
        if (ch == '\n') break;
    }
    buffer[used] = 0;
    return used > 0;
}

int main(int argc, char** argv) {
    const char* protocol = program_protocol(argc > 0 ? argv[0] : "");
    const char* socket_name = getenv("ALR_GUI_BRIDGE_SOCKET");
    const char* transport = (socket_name != 0 && socket_name[0] != 0) ? "unix-abstract-gui" : "tcp-loopback";
    int fd = -1;
    if (socket_name != 0 && socket_name[0] != 0) {
        fd = connect_unix_socket(socket_name);
    } else {
        const char* host = env_or_default("ALR_GUI_BRIDGE_HOST", "127.0.0.1");
        int port = env_int_or_default("ALR_GUI_BRIDGE_PORT", 0);
        fd = connect_loopback(host, port);
    }
    if (fd < 0) {
        fprintf(stderr, "ALR_GUI_IPC_CLIENT connect failed protocol=%s transport=%s errno=%d\n", protocol, transport, errno);
        return 2;
    }

    const int frames = 4;
    char line[256];
    snprintf(line, sizeof(line), "ALR_GUI_IPC_HELLO protocol=%s frames=%d transport=%s\n", protocol, frames, transport);
    if (!write_all(fd, line)) return 3;

    for (int seq = 1; seq <= frames; ++seq) {
        float red = protocol[0] == 'X' ? 0.21f + (0.03f * seq) : 0.05f + (0.04f * seq);
        float green = protocol[0] == 'X' ? 0.12f + (0.05f * seq) : 0.18f + (0.03f * seq);
        float blue = protocol[0] == 'X' ? 0.46f + (0.02f * seq) : 0.45f + (0.02f * seq);
        snprintf(
            line,
            sizeof(line),
            "ALR_GUI_FRAME %s seq=%d %.2f %.2f %.2f %s-frame-%04d\n",
            protocol,
            seq,
            red,
            green,
            blue,
            protocol,
            seq
        );
        if (!write_all(fd, line)) return 4;
    }
    shutdown(fd, SHUT_WR);

    char ack[256];
    if (!read_ack_line(fd, ack, sizeof(ack))) {
        close(fd);
        fprintf(stderr, "ALR_GUI_IPC_CLIENT ack timeout protocol=%s transport=%s\n", protocol, transport);
        return 5;
    }
    ack[strcspn(ack, "\r\n")] = 0;
    close(fd);
    printf("alr-%s-gpu-client ok\n", protocol[0] == 'X' ? "x11" : "wayland");
    printf("ALR_GUI_IPC_CLIENT ok sent=%d transport=%s ack=%s\n", frames, transport, ack);
    return 0;
}
