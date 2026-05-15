#include <errno.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
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

static uint32_t fnv1a32(const unsigned char* bytes, size_t count) {
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < count; ++i) {
        hash ^= (uint32_t)bytes[i];
        hash *= 16777619u;
    }
    return hash;
}

static int ensure_runtime_dir(const char* runtime_dir) {
    if (runtime_dir == 0 || runtime_dir[0] == 0) return 0;
    (void)mkdir("/tmp", 0777);
    if (mkdir(runtime_dir, 0777) == 0 || errno == EEXIST) return 1;
    return 0;
}

static int write_rgba_payload(
    const char* path,
    int width,
    int height,
    float red,
    float green,
    float blue,
    uint32_t* checksum_out,
    size_t* bytes_out
) {
    const size_t pixel_count = (size_t)width * (size_t)height;
    const size_t byte_count = pixel_count * 4u;
    unsigned char* pixels = (unsigned char*)malloc(byte_count);
    if (pixels == 0) return 0;
    const unsigned char r = (unsigned char)(red * 255.0f);
    const unsigned char g = (unsigned char)(green * 255.0f);
    const unsigned char b = (unsigned char)(blue * 255.0f);
    for (size_t i = 0; i < pixel_count; ++i) {
        pixels[(i * 4u) + 0u] = b;
        pixels[(i * 4u) + 1u] = g;
        pixels[(i * 4u) + 2u] = r;
        pixels[(i * 4u) + 3u] = 255u;
    }
    FILE* file = fopen(path, "wb");
    if (file == 0) {
        free(pixels);
        return 0;
    }
    const size_t written = fwrite(pixels, 1u, byte_count, file);
    const int close_ok = fclose(file) == 0;
    if (written != byte_count || !close_ok) {
        free(pixels);
        return 0;
    }
    *checksum_out = fnv1a32(pixels, byte_count);
    *bytes_out = byte_count;
    free(pixels);
    return 1;
}

int main(void) {
    const char* display = env_or_default("WAYLAND_DISPLAY", "wayland-0");
    const char* runtime_dir = env_or_default("XDG_RUNTIME_DIR", "/tmp");
    const char* socket_name = env_or_default("ALR_WAYLAND_DISPLAY_SOCKET", "");
    const char* payload_dir = env_or_default("ALR_WAYLAND_PAYLOAD_DIR", runtime_dir);
    const int width = 320;
    const int height = 180;
    const int stride = width * 4;
    if (!ensure_runtime_dir(payload_dir)) {
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT payload dir failed dir=%s errno=%d\n", payload_dir, errno);
        return 11;
    }
    int fd = connect_unix_socket(socket_name);
    if (fd < 0) {
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT connect failed display=%s socket=%s errno=%d\n", display, socket_name, errno);
        return 2;
    }

    char line[512];
    snprintf(line, sizeof(line), "ALR_WL_CONNECT display=%s runtime=%s transport=unix-abstract-wayland\n", display, runtime_dir);
    if (!write_all(fd, line)) return 3;
    if (!write_all(fd, "ALR_WL_REGISTRY global=wl_compositor version=4 id=1\n")) return 4;
    if (!write_all(fd, "ALR_WL_REGISTRY global=wl_shm version=1 id=2\n")) return 5;
    if (!write_all(fd, "ALR_WL_BIND name=wl_compositor id=1 version=4\n")) return 6;
    if (!write_all(fd, "ALR_WL_SURFACE_CREATE id=10 compositor=1\n")) return 7;
    if (!write_all(fd, "ALR_WL_BIND name=wl_shm id=2 version=1\n")) return 8;
    const float payload_red = 0.19f;
    const float payload_green = 0.24f;
    const float payload_blue = 0.55f;
    char payload_path[256];
    snprintf(payload_path, sizeof(payload_path), "%s/alr-wl-buffer-20.rgba", payload_dir);
    uint32_t checksum = 0;
    size_t payload_bytes = 0;
    if (!write_rgba_payload(payload_path, width, height, payload_red, payload_green, payload_blue, &checksum, &payload_bytes)) {
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT payload write failed path=%s errno=%d\n", payload_path, errno);
        return 12;
    }
    snprintf(
        line,
        sizeof(line),
        "ALR_WL_SHM_POOL_CREATE id=30 path=%s bytes=%zu checksum=%08x\n",
        payload_path,
        payload_bytes,
        checksum
    );
    if (!write_all(fd, line)) return 13;
    snprintf(
        line,
        sizeof(line),
        "ALR_WL_BUFFER_CREATE id=20 width=%d height=%d stride=%d format=argb8888 payload=shared-file\n",
        width,
        height,
        stride
    );
    if (!write_all(fd, line)) return 14;
    for (int seq = 1; seq <= 3; ++seq) {
        snprintf(
            line,
            sizeof(line),
            "ALR_WL_BUFFER_ATTACH surface=10 buffer=20 seq=%d path=%s width=%d height=%d stride=%d bytes=%zu checksum=%08x transport=shared-file\n",
            seq,
            payload_path,
            width,
            height,
            stride,
            payload_bytes,
            checksum
        );
        if (!write_all(fd, line)) return 15;
        snprintf(
            line,
            sizeof(line),
            "ALR_WL_SURFACE_COMMIT surface=10 buffer=20 seq=%d %.2f %.2f %.2f WAYLAND_DISPLAY-frame-%04d payload=%s bytes=%zu checksum=%08x transport=shared-file\n",
            seq,
            payload_red,
            payload_green,
            payload_blue,
            seq,
            payload_path,
            payload_bytes,
            checksum
        );
        if (!write_all(fd, line)) return 16;
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
