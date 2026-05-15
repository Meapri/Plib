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

static int read_text_file(const char* path, char* buffer, size_t buffer_size) {
    if (buffer == 0 || buffer_size == 0) return 0;
    buffer[0] = '\0';
    FILE* file = fopen(path, "rb");
    if (file == 0) return 0;
    size_t read_count = fread(buffer, 1, buffer_size - 1, file);
    buffer[read_count] = '\0';
    fclose(file);
    return read_count > 0;
}

static int extract_json_string(const char* json, const char* key, char* out, size_t out_size) {
    if (json == 0 || key == 0 || out == 0 || out_size == 0) return 0;
    out[0] = '\0';
    const char* key_pos = strstr(json, key);
    if (key_pos == 0) return 0;
    const char* colon = strchr(key_pos, ':');
    if (colon == 0) return 0;
    const char* quote = strchr(colon, '"');
    if (quote == 0) return 0;
    ++quote;
    const char* end = strchr(quote, '"');
    if (end == 0 || end == quote) return 0;
    size_t len = (size_t)(end - quote);
    if (len >= out_size) return 0;
    memcpy(out, quote, len);
    out[len] = '\0';
    return 1;
}

static int select_manifest(char* selected, size_t selected_size, char* manifest_text, size_t manifest_text_size, int* candidate_count) {
    const char* list = getenv("VK_DRIVER_FILES");
    const char* source = "VK_DRIVER_FILES";
    if (list == 0 || list[0] == '\0') {
        list = getenv("VK_ICD_FILENAMES");
        source = "VK_ICD_FILENAMES";
    }
    if (list == 0 || list[0] == '\0') {
        list = "/usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json";
        source = "default";
    }

    char candidates[1024];
    size_t list_len = strlen(list);
    if (list_len >= sizeof(candidates)) return 0;
    memcpy(candidates, list, list_len + 1);

    int seen = 0;
    char* cursor = candidates;
    while (cursor != 0 && *cursor != '\0') {
        char* next = strchr(cursor, ':');
        if (next != 0) {
            *next = '\0';
            ++next;
        }
        if (cursor[0] != '\0') {
            ++seen;
            if (read_text_file(cursor, manifest_text, manifest_text_size)) {
                if (strlen(cursor) >= selected_size) return 0;
                strcpy(selected, cursor);
                if (candidate_count != 0) *candidate_count = seen;
                printf("ALR_VK_LOADER_MANIFEST_SOURCE %s\n", source);
                return 1;
            }
        }
        cursor = next;
    }
    if (candidate_count != 0) *candidate_count = seen;
    return 0;
}

int main(void) {
    const char* host = env_or_default("ALR_VK_BRIDGE_HOST", "127.0.0.1");
    const char* port = env_or_default("ALR_VK_BRIDGE_PORT", "0");

    char manifest_path[512];
    char manifest_text[2048];
    int manifest_count = 0;
    if (!select_manifest(manifest_path, sizeof(manifest_path), manifest_text, sizeof(manifest_text), &manifest_count)) {
        fprintf(stderr, "ALR_VK_LOADER_ERROR no-usable-manifest candidates=%d\n", manifest_count);
        return 2;
    }

    char library_path[256];
    char api_version_text[64];
    if (!extract_json_string(manifest_text, "\"library_path\"", library_path, sizeof(library_path)) ||
        !extract_json_string(manifest_text, "\"api_version\"", api_version_text, sizeof(api_version_text))) {
        fprintf(stderr, "ALR_VK_LOADER_ERROR parse-manifest path=%s\n", manifest_path);
        return 3;
    }

    void* handle = dlopen(library_path, RTLD_NOW | RTLD_LOCAL);
    if (handle == 0) {
        fprintf(stderr, "ALR_VK_LOADER_ERROR dlopen %s: %s\n", library_path, dlerror());
        return 4;
    }

    vk_enumerate_instance_version_fn vk_enumerate_instance_version =
        (vk_enumerate_instance_version_fn)dlsym(handle, "vkEnumerateInstanceVersion");
    alr_vk_proxy_name_fn proxy_name = (alr_vk_proxy_name_fn)dlsym(handle, "alrVkProxyName");
    alr_vk_proxy_request_surface_clear_fn request_surface_clear =
        (alr_vk_proxy_request_surface_clear_fn)dlsym(handle, "alrVkProxyRequestSurfaceClear");
    if (vk_enumerate_instance_version == 0 || proxy_name == 0 || request_surface_clear == 0) {
        fprintf(stderr, "ALR_VK_LOADER_ERROR missing-symbol\n");
        dlclose(handle);
        return 5;
    }

    uint32_t api_version = 0;
    if (vk_enumerate_instance_version(&api_version) != 0 || api_version == 0) {
        fprintf(stderr, "ALR_VK_LOADER_ERROR enumerate-instance-version\n");
        dlclose(handle);
        return 6;
    }

    printf("alr guest vulkaninfo loader smoke ok\n");
    printf("ALR_VK_LOADER_MANIFEST_COUNT %d\n", manifest_count);
    printf("ALR_VK_LOADER_SELECTED_MANIFEST %s\n", manifest_path);
    printf("ALR_VK_LOADER_ICD_LIBRARY_PATH %s\n", library_path);
    printf("ALR_VK_LOADER_ICD_API_VERSION %s\n", api_version_text);
    printf("ALR_VK_LOADER_PROXY_LIB name=%s\n", proxy_name());
    printf(
        "ALR_VK_LOADER_STEP vkEnumerateInstanceVersion ok api=%u.%u.%u\n",
        api_version >> 22,
        (api_version >> 12) & 0x3ffu,
        api_version & 0xfffu
    );

    char response[8192];
    int rc = request_surface_clear(host, port, response, sizeof(response));
    fputs(response, stdout);
    if (rc != 0) {
        fprintf(stderr, "ALR_VK_LOADER_ERROR surface-clear rc=%d\n", rc);
        dlclose(handle);
        return 7;
    }
    if (strstr(response, "ALR_VK_BINARY_BRIDGE_ACK status=PASS") == 0 ||
        strstr(response, "ALR_VK_DEVICE_RECORD ") == 0 ||
        strstr(response, "ALR_VK_FEATURE_RECORD ") == 0 ||
        strstr(response, "ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS") == 0) {
        fprintf(stderr, "ALR_VK_LOADER_ERROR incomplete-response\n");
        dlclose(handle);
        return 8;
    }

    printf("ALR_VK_LOADER_VULKANINFO_INSTANCE_VERSION ok\n");
    printf("ALR_VK_LOADER_VULKANINFO_DEVICE_RECORD ok\n");
    printf("ALR_VK_LOADER_VULKANINFO_FEATURE_RECORD ok\n");
    printf("ALR_VK_LOADER_BINARY_BRIDGE ok\n");
    printf("ALR_VK_LOADER_SURFACE_CLEAR_REQUEST_ACCEPTED ok\n");
    printf("ALR_VK_LOADER_DONE ok\n");
    dlclose(handle);
    return 0;
}
