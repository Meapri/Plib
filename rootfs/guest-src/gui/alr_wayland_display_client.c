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

int main(void) {
    const char* display = env_or_default("WAYLAND_DISPLAY", "wayland-0");
    const char* runtime_dir = env_or_default("XDG_RUNTIME_DIR", "/tmp");
    const char* socket_name = env_or_default("ALR_WAYLAND_DISPLAY_SOCKET", "");
    int fd = connect_unix_socket(socket_name);
    if (fd < 0) {
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT connect failed display=%s socket=%s errno=%d\n", display, socket_name, errno);
        return 2;
    }

    char line[256];
    snprintf(line, sizeof(line), "ALR_WL_CONNECT display=%s runtime=%s transport=unix-abstract-wayland\n", display, runtime_dir);
    if (!write_all(fd, line)) return 3;
    if (!write_all(fd, "ALR_WL_REGISTRY global=wl_compositor version=4 id=1\n")) return 4;
    if (!write_all(fd, "ALR_WL_REGISTRY global=wl_shm version=1 id=2\n")) return 5;
    if (!write_all(fd, "ALR_WL_BIND name=wl_compositor id=1 version=4\n")) return 6;
    if (!write_all(fd, "ALR_WL_SURFACE_CREATE id=10 compositor=1\n")) return 7;
    if (!write_all(fd, "ALR_WL_BUFFER_CREATE id=20 width=320 height=180 format=argb8888\n")) return 8;
    for (int seq = 1; seq <= 3; ++seq) {
        float red = 0.12f + (0.07f * seq);
        float green = 0.20f + (0.04f * seq);
        float blue = 0.52f + (0.03f * seq);
        snprintf(
            line,
            sizeof(line),
            "ALR_WL_SURFACE_COMMIT surface=10 buffer=20 seq=%d %.2f %.2f %.2f WAYLAND_DISPLAY-frame-%04d\n",
            seq,
            red,
            green,
            blue,
            seq
        );
        if (!write_all(fd, line)) return 9;
    }
    shutdown(fd, SHUT_WR);

    char ack[256];
    if (!read_ack_line(fd, ack, sizeof(ack))) {
        close(fd);
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT ack timeout display=%s\n", display);
        return 10;
    }
    ack[strcspn(ack, "\r\n")] = 0;
    close(fd);
    printf("alr-wayland-display-client ok\n");
    printf("ALR_WL_DISPLAY_CLIENT ok display=%s commits=3 ack=%s\n", display, ack);
    return 0;
}
