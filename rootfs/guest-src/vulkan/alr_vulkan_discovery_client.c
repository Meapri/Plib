#include <arpa/inet.h>
#include <errno.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

static const char* env_or_default(const char* name, const char* fallback) {
    const char* value = getenv(name);
    return (value == 0 || value[0] == '\0') ? fallback : value;
}

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
    if (fd < 0) {
        return -1;
    }

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

int main(void) {
    const char* host = env_or_default("ALR_VK_BRIDGE_HOST", "127.0.0.1");
    const char* transport = env_or_default("ALR_GPU_BRIDGE_TRANSPORT", "tcp-loopback-vulkan");
    int port = parse_port(env_or_default("ALR_VK_BRIDGE_PORT", "0"));
    if (port == 0) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR invalid-port\n");
        return 2;
    }

    printf("alr guest vulkan discovery client ok\n");
    printf("ALR_VK_DISCOVERY_CLIENT kind=android-vulkan-host-bridge\n");
    printf("ALR_VK_DISCOVERY_HELLO host=%s port=%d transport=%s\n", host, port, transport);
    printf("ALR_VK_DISCOVERY_REQUEST instance_device=1 wsi=android-surface-next\n");

    int fd = connect_loopback(host, port);
    if (fd < 0) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR connect errno=%d\n", errno);
        return 3;
    }

    if (!write_all(fd, "ALR_VK_DISCOVERY_HELLO version=1 request=instance-device\n")) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR write-hello errno=%d\n", errno);
        close(fd);
        return 4;
    }
    if (!write_all(fd, "ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.12 green=0.64 blue=0.92 alpha=1.0 tag=guest-vulkan-clear-0001\n")) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR write-clear-request errno=%d\n", errno);
        close(fd);
        return 4;
    }
    shutdown(fd, SHUT_WR);

    char buffer[1024];
    char response[8192];
    size_t response_len = 0;
    memset(response, 0, sizeof(response));
    ssize_t read_count = 0;
    while ((read_count = read(fd, buffer, sizeof(buffer) - 1)) > 0) {
        buffer[read_count] = '\0';
        fputs(buffer, stdout);
        if (response_len < sizeof(response) - 1) {
            size_t copy_len = (size_t)read_count;
            if (copy_len > sizeof(response) - 1 - response_len) {
                copy_len = sizeof(response) - 1 - response_len;
            }
            memcpy(response + response_len, buffer, copy_len);
            response_len += copy_len;
            response[response_len] = '\0';
        }
    }
    close(fd);

    if (strstr(response, "ALR_VK_DISCOVERY_ACK status=PASS") == 0) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR missing-pass-ack\n");
        return 5;
    }
    if (strstr(response, "ALR_VK_DEVICE_RECORD ") == 0) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR missing-device-record\n");
        return 6;
    }
    if (strstr(response, "ALR_VK_FEATURE_RECORD ") == 0) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR missing-feature-record\n");
        return 7;
    }
    if (strstr(response, "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") == 0) {
        fprintf(stderr, "ALR_VK_DISCOVERY_ERROR missing-surface-clear-ack\n");
        return 8;
    }
    printf("ALR_VK_DISCOVERY_DEVICE_RECORD ok\n");
    printf("ALR_VK_DISCOVERY_FEATURE_RECORD ok\n");
    printf("ALR_VK_SURFACE_CLEAR_REQUEST_ACCEPTED ok\n");
    printf("ALR_VK_DISCOVERY_DONE ok\n");
    return 0;
}
