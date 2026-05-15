#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef uint32_t (*vk_enumerate_instance_version_fn)(uint32_t*);
typedef const char* (*alr_vk_proxy_name_fn)(void);
typedef int (*alr_vk_proxy_request_surface_clear_fn)(const char*, const char*, char*, size_t);

static const char* env_or_default(const char* name, const char* fallback) {
    const char* value = getenv(name);
    return (value == 0 || value[0] == '\0') ? fallback : value;
}

int main(void) {
    const char* host = env_or_default("ALR_VK_BRIDGE_HOST", "127.0.0.1");
    const char* port = env_or_default("ALR_VK_BRIDGE_PORT", "0");
    void* handle = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (handle == 0) {
        fprintf(stderr, "ALR_VK_PROXY_ERROR dlopen %s\n", dlerror());
        return 2;
    }

    vk_enumerate_instance_version_fn vk_enumerate_instance_version =
        (vk_enumerate_instance_version_fn)dlsym(handle, "vkEnumerateInstanceVersion");
    alr_vk_proxy_name_fn proxy_name = (alr_vk_proxy_name_fn)dlsym(handle, "alrVkProxyName");
    alr_vk_proxy_request_surface_clear_fn request_surface_clear =
        (alr_vk_proxy_request_surface_clear_fn)dlsym(handle, "alrVkProxyRequestSurfaceClear");
    if (vk_enumerate_instance_version == 0 || proxy_name == 0 || request_surface_clear == 0) {
        fprintf(stderr, "ALR_VK_PROXY_ERROR missing-symbol\n");
        dlclose(handle);
        return 3;
    }

    uint32_t api_version = 0;
    if (vk_enumerate_instance_version(&api_version) != 0 || api_version == 0) {
        fprintf(stderr, "ALR_VK_PROXY_ERROR enumerate-instance-version\n");
        dlclose(handle);
        return 4;
    }

    printf("alr guest vulkan proxy smoke ok\n");
    printf("ALR_VK_PROXY_LIB name=%s\n", proxy_name());
    printf(
        "ALR_VK_PROXY_STEP vkEnumerateInstanceVersion ok api=%u.%u.%u\n",
        api_version >> 22,
        (api_version >> 12) & 0x3ffu,
        api_version & 0xfffu
    );

    char response[8192];
    int rc = request_surface_clear(host, port, response, sizeof(response));
    fputs(response, stdout);
    if (rc != 0) {
        fprintf(stderr, "ALR_VK_PROXY_ERROR surface-clear rc=%d\n", rc);
        dlclose(handle);
        return 5;
    }
    if (strstr(response, "ALR_VK_DEVICE_RECORD ") == 0 ||
        strstr(response, "ALR_VK_FEATURE_RECORD ") == 0 ||
        strstr(response, "ALR_VK_BINARY_BRIDGE_ACK status=PASS") == 0 ||
        strstr(response, "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") == 0) {
        fprintf(stderr, "ALR_VK_PROXY_ERROR incomplete-response\n");
        dlclose(handle);
        return 6;
    }

    printf("ALR_VK_PROXY_BINARY_BRIDGE ok\n");
    printf("ALR_VK_PROXY_DEVICE_RECORD ok\n");
    printf("ALR_VK_PROXY_FEATURE_RECORD ok\n");
    printf("ALR_VK_PROXY_SURFACE_CLEAR_REQUEST_ACCEPTED ok\n");
    printf("ALR_VK_PROXY_DONE ok\n");
    dlclose(handle);
    return 0;
}
