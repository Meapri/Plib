#include <arpa/inet.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define VK_SUCCESS 0

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
    int port = parse_port(port_text == 0 ? "0" : port_text);
    if (host == 0 || host[0] == '\0' || port == 0) return -2;

    int fd = connect_loopback(host, port);
    if (fd < 0) return -3;

    if (!write_all(fd, "ALR_VK_DISCOVERY_HELLO version=1 request=libvulkan-proxy\n") ||
        !write_all(fd, "ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.33 green=0.22 blue=0.88 alpha=1.0 tag=guest-vulkan-proxy-clear-0001 source=libvulkan-proxy\n")) {
        close(fd);
        return -4;
    }
    shutdown(fd, SHUT_WR);

    char buffer[1024];
    size_t response_len = 0;
    ssize_t read_count = 0;
    while ((read_count = read(fd, buffer, sizeof(buffer) - 1)) > 0) {
        if (response_len < response_size - 1) {
            size_t copy_len = (size_t)read_count;
            if (copy_len > response_size - 1 - response_len) {
                copy_len = response_size - 1 - response_len;
            }
            memcpy(response + response_len, buffer, copy_len);
            response_len += copy_len;
            response[response_len] = '\0';
        }
    }
    close(fd);

    return strstr(response, "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") != 0 ? 0 : -5;
}
