#include <arpa/inet.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#define VK_SUCCESS 0
#define ALR_VK_PROXY_BINARY_MAX_PAYLOAD 4096

typedef uint32_t VkResult;
typedef void* VkInstance;

static int parse_port(const char* value) {
    char* end = 0;
    long port = strtol(value, &end, 10);
    if (end == value || *end != '\0' || port < 1 || port > 65535) {
        return 0;
    }
    return (int)port;
}

static int connect_loopback(const char* host, int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((unsigned short)port);
    if (inet_pton(AF_INET, host, &addr.sin_addr) != 1) {
        close(fd);
        return -1;
    }
    if (connect(fd, (struct sockaddr*)&addr, sizeof(addr)) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static int connect_unix_socket(const char* socket_name) {
    if (socket_name == 0 || socket_name[0] == '\0') return -1;
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return -1;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    size_t name_len = strlen(socket_name);
    socklen_t addr_len = 0;
    if (socket_name[0] == '@') {
        if (name_len - 1 >= sizeof(addr.sun_path)) {
            close(fd);
            return -1;
        }
        addr.sun_path[0] = '\0';
        memcpy(addr.sun_path + 1, socket_name + 1, name_len - 1);
        addr_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + name_len);
    } else {
        if (name_len >= sizeof(addr.sun_path)) {
            close(fd);
            return -1;
        }
        memcpy(addr.sun_path, socket_name, name_len + 1);
        addr_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + name_len + 1);
    }
    if (connect(fd, (struct sockaddr*)&addr, addr_len) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static int write_all(int fd, const char* text) {
    const char* cursor = text;
    size_t remaining = strlen(text);
    while (remaining > 0) {
        ssize_t written = write(fd, cursor, remaining);
        if (written < 0) {
            if (errno == EINTR) continue;
            return 0;
        }
        cursor += written;
        remaining -= (size_t)written;
    }
    return 1;
}

static int write_all_bytes(int fd, const uint8_t* bytes, size_t size) {
    const uint8_t* cursor = bytes;
    size_t remaining = size;
    while (remaining > 0) {
        ssize_t written = write(fd, cursor, remaining);
        if (written < 0) {
            if (errno == EINTR) continue;
            return 0;
        }
        cursor += written;
        remaining -= (size_t)written;
    }
    return 1;
}

static int read_exact(int fd, uint8_t* bytes, size_t size) {
    size_t read_total = 0;
    while (read_total < size) {
        ssize_t read_count = read(fd, bytes + read_total, size - read_total);
        if (read_count < 0) {
            if (errno == EINTR) continue;
            return 0;
        }
        if (read_count == 0) return 0;
        read_total += (size_t)read_count;
    }
    return 1;
}

static void put_u16le(uint8_t* bytes, uint16_t value) {
    bytes[0] = (uint8_t)(value & 0xffu);
    bytes[1] = (uint8_t)((value >> 8) & 0xffu);
}

static uint16_t get_u16le(const uint8_t* bytes) {
    return (uint16_t)bytes[0] | ((uint16_t)bytes[1] << 8);
}

static int append_response(char* response, size_t response_size, size_t* response_len, const uint8_t* bytes, size_t size) {
    if (*response_len >= response_size - 1) return 1;
    size_t copy_len = size;
    if (copy_len > response_size - 1 - *response_len) {
        copy_len = response_size - 1 - *response_len;
    }
    memcpy(response + *response_len, bytes, copy_len);
    *response_len += copy_len;
    response[*response_len] = '\0';
    return copy_len == size;
}

const char* alrVkProxyName(void) {
    return "alr-guest-libvulkan-proxy-v1";
}

VkResult vkEnumerateInstanceVersion(uint32_t* api_version) {
    if (api_version != 0) {
        *api_version = (1u << 22) | (3u << 12) | 247u;
    }
    return VK_SUCCESS;
}

void* vkGetInstanceProcAddr(VkInstance instance, const char* name) {
    (void)instance;
    if (name == 0) return 0;
    if (strcmp(name, "vkEnumerateInstanceVersion") == 0) {
        return (void*)&vkEnumerateInstanceVersion;
    }
    if (strcmp(name, "vkGetInstanceProcAddr") == 0) {
        return (void*)&vkGetInstanceProcAddr;
    }
    return 0;
}

int alrVkProxyRequestSurfaceClear(const char* host, const char* port_text, char* response, size_t response_size) {
    if (response == 0 || response_size == 0) return -1;
    response[0] = '\0';
    const char* socket_name = getenv("ALR_VK_BRIDGE_SOCKET");
    int fd = -1;
    if (socket_name != 0 && socket_name[0] != '\0') {
        fd = connect_unix_socket(socket_name);
    } else {
        int port = parse_port(port_text == 0 ? "0" : port_text);
        if (host == 0 || host[0] == '\0' || port == 0) return -2;
        fd = connect_loopback(host, port);
    }
    if (fd < 0) return -3;

    const char* tag = "guest-vulkan-proxy-clear-0001";
    const char* source = "libvulkan-proxy";
    const uint16_t tag_len = (uint16_t)strlen(tag);
    const uint16_t source_len = (uint16_t)strlen(source);
    const uint16_t payload_len = (uint16_t)(12u + tag_len + source_len);
    uint8_t request[12 + 12 + 64];
    if (sizeof(request) < 12u + payload_len) {
        close(fd);
        return -4;
    }
    memcpy(request, "ALVB", 4);
    put_u16le(request + 4, 1);
    put_u16le(request + 6, 1);
    put_u16le(request + 8, payload_len);
    put_u16le(request + 10, 0);
    put_u16le(request + 12, 330);
    put_u16le(request + 14, 220);
    put_u16le(request + 16, 880);
    put_u16le(request + 18, 1000);
    put_u16le(request + 20, tag_len);
    put_u16le(request + 22, source_len);
    memcpy(request + 24, tag, tag_len);
    memcpy(request + 24 + tag_len, source, source_len);

    if (!write_all(fd, "ALR_VK_DISCOVERY_HELLO version=1 request=libvulkan-proxy-binary\n") ||
        !write_all_bytes(fd, request, 12u + payload_len)) {
        close(fd);
        return -4;
    }
    shutdown(fd, SHUT_WR);

    uint8_t response_header[12];
    if (!read_exact(fd, response_header, sizeof(response_header))) {
        close(fd);
        return -5;
    }
    if (memcmp(response_header, "ALVR", 4) != 0 || get_u16le(response_header + 4) != 1) {
        close(fd);
        return -6;
    }
    const uint16_t status = get_u16le(response_header + 6);
    const uint16_t response_payload_len = get_u16le(response_header + 8);
    const uint16_t record_count = get_u16le(response_header + 10);
    if (response_payload_len > ALR_VK_PROXY_BINARY_MAX_PAYLOAD) {
        close(fd);
        return -7;
    }
    uint8_t payload[ALR_VK_PROXY_BINARY_MAX_PAYLOAD];
    if (!read_exact(fd, payload, response_payload_len)) {
        close(fd);
        return -8;
    }

    size_t response_len = 0;
    char bridge_line[128];
    int bridge_line_len = snprintf(
        bridge_line,
        sizeof(bridge_line),
        "ALR_VK_BINARY_BRIDGE_ACK status=%s protocol=alr-vk-bin-v1 payload_bytes=%u records=%u\n",
        status == 1 ? "PASS" : "FAIL",
        response_payload_len,
        record_count
    );
    if (bridge_line_len > 0) {
        append_response(response, response_size, &response_len, (const uint8_t*)bridge_line, (size_t)bridge_line_len);
    }
    append_response(response, response_size, &response_len, payload, response_payload_len);
    close(fd);

    return status == 1 && strstr(response, "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") != 0 ? 0 : -9;
}
