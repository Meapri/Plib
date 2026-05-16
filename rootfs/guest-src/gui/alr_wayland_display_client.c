#include <errno.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <sys/uio.h>
#include <unistd.h>

#ifndef SYS_memfd_create
#define SYS_memfd_create 279
#endif

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif

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

static int write_all_bytes(int fd, const unsigned char* bytes, size_t byte_count) {
    size_t written_total = 0;
    while (written_total < byte_count) {
        ssize_t written = write(fd, bytes + written_total, byte_count - written_total);
        if (written <= 0) return 0;
        written_total += (size_t)written;
    }
    return 1;
}

static unsigned char* build_rgba_payload(
    int width,
    int height,
    float red,
    float green,
    float blue,
    size_t* bytes_out,
    uint32_t* checksum_out
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
    *bytes_out = byte_count;
    *checksum_out = fnv1a32(pixels, byte_count);
    return pixels;
}

static int create_memfd_payload(
    const unsigned char* payload,
    size_t payload_bytes,
    const char* name
) {
    int fd = (int)syscall(SYS_memfd_create, name, MFD_CLOEXEC);
    if (fd < 0) return -1;
    if (!write_all_bytes(fd, payload, payload_bytes)) {
        close(fd);
        return -1;
    }
    if (lseek(fd, 0, SEEK_SET) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static int send_fd_preamble(int socket_fd, const int* payload_fds, size_t payload_fd_count) {
    if (payload_fds == 0 || payload_fd_count == 0 || payload_fd_count > 12) return 0;
    char marker = 'F';
    struct iovec iov;
    iov.iov_base = &marker;
    iov.iov_len = 1;
    char control[CMSG_SPACE(sizeof(int) * 12u)];
    memset(control, 0, sizeof(control));
    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    msg.msg_control = control;
    msg.msg_controllen = sizeof(control);
    struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(int) * payload_fd_count);
    memcpy(CMSG_DATA(cmsg), payload_fds, sizeof(int) * payload_fd_count);
    msg.msg_controllen = CMSG_SPACE(sizeof(int) * payload_fd_count);
    return sendmsg(socket_fd, &msg, 0) == 1;
}

static int emit_wayland_wire_line(
    int fd,
    uint32_t object_id,
    uint16_t opcode,
    uint16_t size,
    const char* name,
    const char* args
) {
    char line[512];
    const uint32_t header_word = ((uint32_t)size << 16u) | (uint32_t)opcode;
    snprintf(
        line,
        sizeof(line),
        "ALR_WL_WIRE object=%u opcode=%u size=%u header=0x%08x name=%s args=%s wire=wayland-header-v1 endian=little\n",
        object_id,
        (unsigned int)opcode,
        (unsigned int)size,
        header_word,
        name,
        args
    );
    return write_all(fd, line);
}

static int emit_wayland_wire_subset(
    int fd,
    size_t pool_bytes,
    int frame_count,
    const int* dirty_x,
    const int* dirty_y,
    const int* dirty_w,
    const int* dirty_h
) {
    char args[192];
    if (!emit_wayland_wire_line(fd, 1, 1, 12, "wl_display.get_registry", "new_id=2")) return 0;
    if (!emit_wayland_wire_line(fd, 2, 0, 40, "wl_registry.bind", "name=1 interface=wl_compositor version=4 new_id=3")) return 0;
    if (!emit_wayland_wire_line(fd, 3, 0, 12, "wl_compositor.create_surface", "new_id=10")) return 0;
    if (!emit_wayland_wire_line(fd, 2, 0, 32, "wl_registry.bind", "name=2 interface=wl_shm version=1 new_id=4")) return 0;
    snprintf(args, sizeof(args), "new_id=30 fd=0 size=%zu", pool_bytes);
    if (!emit_wayland_wire_line(fd, 4, 0, 20, "wl_shm.create_pool", args)) return 0;
    if (!emit_wayland_wire_line(fd, 30, 0, 36, "wl_shm_pool.create_buffer", "new_id=20 x=0 y=0 width=320 height=180 stride=1280 format=argb8888")) return 0;
    for (int seq = 0; seq < frame_count; ++seq) {
        if (!emit_wayland_wire_line(fd, 10, 1, 20, "wl_surface.attach", "buffer=20 x=0 y=0")) return 0;
        snprintf(args, sizeof(args), "x=%d y=%d width=%d height=%d", dirty_x[seq], dirty_y[seq], dirty_w[seq], dirty_h[seq]);
        if (!emit_wayland_wire_line(fd, 10, 9, 24, "wl_surface.damage_buffer", args)) return 0;
        snprintf(args, sizeof(args), "seq=%d", seq + 1);
        if (!emit_wayland_wire_line(fd, 10, 6, 8, "wl_surface.commit", args)) return 0;
    }
    return 1;
}

static void put_u32_le(unsigned char* bytes, size_t offset, uint32_t value) {
    bytes[offset + 0u] = (unsigned char)(value & 0xffu);
    bytes[offset + 1u] = (unsigned char)((value >> 8u) & 0xffu);
    bytes[offset + 2u] = (unsigned char)((value >> 16u) & 0xffu);
    bytes[offset + 3u] = (unsigned char)((value >> 24u) & 0xffu);
}

static int append_wayland_binary_request(
    unsigned char* bytes,
    size_t capacity,
    size_t* offset,
    uint32_t object_id,
    uint16_t opcode,
    uint16_t size
) {
    if (size < 8u || (size % 4u) != 0u || *offset + size > capacity) return 0;
    const size_t start = *offset;
    memset(bytes + start, 0, size);
    put_u32_le(bytes, start, object_id);
    put_u32_le(bytes, start + 4u, ((uint32_t)size << 16u) | (uint32_t)opcode);
    *offset += size;
    return 1;
}

static int emit_wayland_binary_subset(int fd, int frame_count) {
    unsigned char bytes[1024];
    size_t offset = 0;
    memset(bytes, 0, sizeof(bytes));
    if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 1, 1, 12)) return 0;
    if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 2, 0, 40)) return 0;
    if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 3, 0, 12)) return 0;
    if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 2, 0, 32)) return 0;
    if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 4, 0, 20)) return 0;
    if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 30, 0, 36)) return 0;
    for (int seq = 0; seq < frame_count; ++seq) {
        if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 10, 1, 20)) return 0;
        if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 10, 9, 24)) return 0;
        if (!append_wayland_binary_request(bytes, sizeof(bytes), &offset, 10, 6, 8)) return 0;
    }
    const int message_count = 6 + (frame_count * 3);
    char header[160];
    snprintf(
        header,
        sizeof(header),
        "ALR_WL_BINARY_STREAM bytes=%zu messages=%d checksum=%08x wire=wayland-binary-v1 endian=little\n",
        offset,
        message_count,
        fnv1a32(bytes, offset)
    );
    return write_all(fd, header) && write_all_bytes(fd, bytes, offset);
}

int main(void) {
    const char* display = env_or_default("WAYLAND_DISPLAY", "wayland-0");
    const char* runtime_dir = env_or_default("XDG_RUNTIME_DIR", "/tmp");
    const char* socket_name = env_or_default("ALR_WAYLAND_DISPLAY_SOCKET", "");
    const char* payload_dir = env_or_default("ALR_WAYLAND_PAYLOAD_DIR", runtime_dir);
    const int width = 320;
    const int height = 180;
    const int stride = width * 4;
    enum { frame_count = 8 };
    const int dirty_x[frame_count] = {0, 80, 160, 0, 80, 160, 0, 80};
    const int dirty_y[frame_count] = {0, 45, 90, 90, 45, 0, 0, 90};
    const int dirty_w[frame_count] = {160, 160, 160, 160, 160, 160, 160, 160};
    const int dirty_h[frame_count] = {90, 90, 90, 90, 90, 90, 90, 90};
    if (!ensure_runtime_dir(payload_dir)) {
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT payload dir failed dir=%s errno=%d\n", payload_dir, errno);
        return 11;
    }
    int fd = connect_unix_socket(socket_name);
    if (fd < 0) {
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT connect failed display=%s socket=%s errno=%d\n", display, socket_name, errno);
        return 2;
    }

    const float payload_reds[frame_count] = {0.19f, 0.36f, 0.52f, 0.65f, 0.22f, 0.44f, 0.74f, 0.30f};
    const float payload_greens[frame_count] = {0.24f, 0.43f, 0.31f, 0.20f, 0.58f, 0.48f, 0.36f, 0.66f};
    const float payload_blues[frame_count] = {0.55f, 0.22f, 0.16f, 0.42f, 0.30f, 0.68f, 0.24f, 0.52f};
    char payload_paths[frame_count][256];
    uint32_t checksums[frame_count];
    size_t payload_bytes[frame_count];
    int memfds[frame_count];
    memset(checksums, 0, sizeof(checksums));
    memset(payload_bytes, 0, sizeof(payload_bytes));
    for (int seq = 0; seq < frame_count; ++seq) {
        memfds[seq] = -1;
    }
    for (int seq = 0; seq < frame_count; ++seq) {
        snprintf(payload_paths[seq], sizeof(payload_paths[seq]), "%s/alr-wl-buffer-20-seq%02d.rgba", payload_dir, seq + 1);
        unsigned char* payload = build_rgba_payload(
            width,
            height,
            payload_reds[seq],
            payload_greens[seq],
            payload_blues[seq],
            &payload_bytes[seq],
            &checksums[seq]
        );
        if (payload == 0) {
            fprintf(stderr, "ALR_WL_DISPLAY_CLIENT payload allocation failed seq=%d\n", seq + 1);
            return 17;
        }
        if (!write_rgba_payload(
                payload_paths[seq],
                width,
                height,
                payload_reds[seq],
                payload_greens[seq],
                payload_blues[seq],
                &checksums[seq],
                &payload_bytes[seq]
            )) {
            free(payload);
            fprintf(stderr, "ALR_WL_DISPLAY_CLIENT payload write failed seq=%d path=%s errno=%d\n", seq + 1, payload_paths[seq], errno);
            return 12;
        }
        char fd_name[64];
        snprintf(fd_name, sizeof(fd_name), "alr-wayland-buffer-20-seq%02d", seq + 1);
        memfds[seq] = create_memfd_payload(payload, payload_bytes[seq], fd_name);
        free(payload);
        if (memfds[seq] < 0) {
            fprintf(stderr, "ALR_WL_DISPLAY_CLIENT memfd failed seq=%d bytes=%zu errno=%d\n", seq + 1, payload_bytes[seq], errno);
            for (int close_seq = 0; close_seq < frame_count; ++close_seq) {
                if (memfds[close_seq] >= 0) close(memfds[close_seq]);
            }
            return 18;
        }
    }
    if (!send_fd_preamble(fd, memfds, frame_count)) {
        for (int seq = 0; seq < frame_count; ++seq) {
            close(memfds[seq]);
        }
        fprintf(stderr, "ALR_WL_DISPLAY_CLIENT fd send failed errno=%d\n", errno);
        return 19;
    }
    for (int seq = 0; seq < frame_count; ++seq) {
        close(memfds[seq]);
    }

    char line[512];
    if (!emit_wayland_binary_subset(fd, frame_count)) return 24;
    snprintf(line, sizeof(line), "ALR_WL_APP_STREAM_BEGIN frames=%d mode=continuous-demo pacing=guest-driven target=v118-simple-gui-demo\n", frame_count);
    if (!write_all(fd, line)) return 25;
    snprintf(line, sizeof(line), "ALR_WL_CONNECT display=%s runtime=%s transport=unix-abstract-wayland\n", display, runtime_dir);
    if (!write_all(fd, line)) return 3;
    if (!write_all(fd, "ALR_WL_REGISTRY global=wl_compositor version=4 id=1\n")) return 4;
    if (!write_all(fd, "ALR_WL_REGISTRY global=wl_shm version=1 id=2\n")) return 5;
    if (!write_all(fd, "ALR_WL_BIND name=wl_compositor id=1 version=4\n")) return 6;
    if (!write_all(fd, "ALR_WL_SURFACE_CREATE id=10 compositor=1\n")) return 7;
    if (!write_all(fd, "ALR_WL_BIND name=wl_shm id=2 version=1\n")) return 8;
    if (!emit_wayland_wire_subset(fd, payload_bytes[0] * frame_count, frame_count, dirty_x, dirty_y, dirty_w, dirty_h)) return 23;
    if (!write_all(fd, "ALR_WL_AHB_BACKING_ADVERTISE version=1 allocator=android-host format=R8G8B8A8_UNORM usage=cpu-read-write+gpu-sampled+gpu-color-output max_buffers=3 dirty_rect=true\n")) return 21;
    snprintf(
        line,
        sizeof(line),
        "ALR_WL_SHM_POOL_CREATE id=30 path=%s bytes=%zu checksum=%08x buffers=%d layout=triple-buffer-file\n",
        payload_paths[0],
        payload_bytes[0],
        checksums[0],
        frame_count
    );
    if (!write_all(fd, line)) return 13;
    for (int seq = 0; seq < frame_count; ++seq) {
        snprintf(
            line,
            sizeof(line),
            "ALR_WL_SHM_POOL_FD id=%d fd_index=%d bytes=%zu checksum=%08x transport=scm-rights-memfd layout=triple-buffer\n",
            31 + seq,
            seq,
            payload_bytes[seq],
            checksums[seq]
        );
        if (!write_all(fd, line)) return 20;
    }
    snprintf(
        line,
        sizeof(line),
        "ALR_WL_BUFFER_CREATE id=20 width=%d height=%d stride=%d format=argb8888 payload=shared-file fd_payload=scm-rights-memfd\n",
        width,
        height,
        stride
    );
    if (!write_all(fd, line)) return 14;
    for (int seq = 1; seq <= frame_count; ++seq) {
        const int index = seq - 1;
        const size_t dirty_bytes = (size_t)dirty_w[index] * (size_t)dirty_h[index] * 4u;
        snprintf(
            line,
            sizeof(line),
            "ALR_WL_DAMAGE surface=10 buffer=20 seq=%d x=%d y=%d width=%d height=%d bytes=%zu type=buffer-damage backing=host-ahardwarebuffer update=partial\n",
            seq,
            dirty_x[index],
            dirty_y[index],
            dirty_w[index],
            dirty_h[index],
            dirty_bytes
        );
        if (!write_all(fd, line)) return 22;
        snprintf(
            line,
            sizeof(line),
            "ALR_WL_BUFFER_ATTACH surface=10 buffer=20 seq=%d path=%s width=%d height=%d stride=%d bytes=%zu checksum=%08x fd_index=%d fd_bytes=%zu fd_checksum=%08x transport=shared-file+scm-rights-memfd layout=triple-buffer backing=host-ahardwarebuffer buffer_slot=%d dirty_x=%d dirty_y=%d dirty_w=%d dirty_h=%d dirty_bytes=%zu update=partial\n",
            seq,
            payload_paths[index],
            width,
            height,
            stride,
            payload_bytes[index],
            checksums[index],
            index,
            payload_bytes[index],
            checksums[index],
            index,
            dirty_x[index],
            dirty_y[index],
            dirty_w[index],
            dirty_h[index],
            dirty_bytes
        );
        if (!write_all(fd, line)) return 15;
        snprintf(
            line,
            sizeof(line),
            "ALR_WL_SURFACE_COMMIT surface=10 buffer=20 seq=%d %.2f %.2f %.2f WAYLAND_DISPLAY-frame-%04d payload=%s bytes=%zu checksum=%08x fd_index=%d fd_bytes=%zu fd_checksum=%08x transport=shared-file+scm-rights-memfd layout=triple-buffer backing=host-ahardwarebuffer buffer_slot=%d dirty_x=%d dirty_y=%d dirty_w=%d dirty_h=%d dirty_bytes=%zu update=partial\n",
            seq,
            payload_reds[index],
            payload_greens[index],
            payload_blues[index],
            seq,
            payload_paths[index],
            payload_bytes[index],
            checksums[index],
            index,
            payload_bytes[index],
            checksums[index],
            index,
            dirty_x[index],
            dirty_y[index],
            dirty_w[index],
            dirty_h[index],
            dirty_bytes
        );
        if (!write_all(fd, line)) return 16;
    }
    snprintf(line, sizeof(line), "ALR_WL_APP_STREAM_END frames=%d commits=%d mode=continuous-demo\n", frame_count, frame_count);
    if (!write_all(fd, line)) return 26;
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
    printf("ALR_WL_DISPLAY_CLIENT ok display=%s commits=%d ack=%s\n", display, frame_count, ack);
    return 0;
}
