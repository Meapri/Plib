#include <jni.h>

#include <android/hardware_buffer.h>
#include <android/native_window_jni.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <dlfcn.h>

#include <algorithm>
#include <array>
#include <cctype>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>
#include <unistd.h>

#include "runtime_plan.hpp"

namespace {

#ifndef EGL_NATIVE_BUFFER_ANDROID
#define EGL_NATIVE_BUFFER_ANDROID 0x3140
#endif

#ifndef EGL_IMAGE_PRESERVED_KHR
#define EGL_IMAGE_PRESERVED_KHR 0x30D2
#endif

using EglGetNativeClientBufferAndroidFn = EGLClientBuffer (*)(const AHardwareBuffer*);
using EglCreateImageKhrFn = EGLImageKHR (*)(EGLDisplay, EGLContext, EGLenum, EGLClientBuffer, const EGLint*);
using EglDestroyImageKhrFn = EGLBoolean (*)(EGLDisplay, EGLImageKHR);
using GlEglImageTargetTexture2DOesFn = void (*)(GLenum, GLeglImageOES);

std::string jstring_to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? std::string{} : std::string{chars};
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

std::string join_path(const std::string& left, const std::string& right) {
    if (left.empty()) {
        return right;
    }
    if (left.back() == '/') {
        return left + right;
    }
    return left + "/" + right;
}


std::string egl_error_hex() {
    std::ostringstream out;
    out << "0x" << std::hex << eglGetError();
    return out.str();
}

std::string safe_gl_string(GLenum name) {
    const auto* value = glGetString(name);
    if (value == nullptr) {
        return "missing";
    }
    return reinterpret_cast<const char*>(value);
}

GLuint compile_surface_shader(GLenum type, const char* source, std::ostringstream& out, const char* label) {
    const GLuint shader = glCreateShader(type);
    if (shader == 0) {
        out << "\nsurface triangle shader " << label << "=fail create";
        return 0;
    }
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled != GL_TRUE) {
        out << "\nsurface triangle shader " << label << "=fail compile";
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint create_surface_triangle_program(std::ostringstream& out) {
    static const char* vertex_shader_source =
        "attribute vec2 aPosition;\n"
        "void main() {\n"
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
        "}\n";
    static const char* fragment_shader_source =
        "precision mediump float;\n"
        "uniform vec4 uColor;\n"
        "void main() {\n"
        "  gl_FragColor = uColor;\n"
        "}\n";
    const GLuint vertex_shader = compile_surface_shader(GL_VERTEX_SHADER, vertex_shader_source, out, "vertex");
    if (vertex_shader == 0) return 0;
    const GLuint fragment_shader = compile_surface_shader(GL_FRAGMENT_SHADER, fragment_shader_source, out, "fragment");
    if (fragment_shader == 0) {
        glDeleteShader(vertex_shader);
        return 0;
    }
    const GLuint program = glCreateProgram();
    if (program == 0) {
        out << "\nsurface triangle program=fail create";
        glDeleteShader(vertex_shader);
        glDeleteShader(fragment_shader);
        return 0;
    }
    glAttachShader(program, vertex_shader);
    glAttachShader(program, fragment_shader);
    glBindAttribLocation(program, 0, "aPosition");
    glLinkProgram(program);
    glDeleteShader(vertex_shader);
    glDeleteShader(fragment_shader);
    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        out << "\nsurface triangle program=fail link";
        glDeleteProgram(program);
        return 0;
    }
    out << "\nsurface triangle program=ok";
    return program;
}

std::string lower_copy(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return value;
}

bool renderer_looks_software(const std::string& vendor, const std::string& renderer) {
    const auto combined = lower_copy(vendor + " " + renderer);
    return combined.find("swiftshader") != std::string::npos ||
           combined.find("llvmpipe") != std::string::npos ||
           combined.find("softpipe") != std::string::npos ||
           combined.find("software") != std::string::npos ||
           combined.find("mesa offscreen") != std::string::npos;
}

uint32_t fnv1a32_bytes(const uint8_t* bytes, size_t count) {
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < count; ++i) {
        hash ^= static_cast<uint32_t>(bytes[i]);
        hash *= 16777619u;
    }
    return hash;
}

std::string hex_u32(uint32_t value) {
    std::ostringstream out;
    out << std::hex << value;
    return out.str();
}

const char* vulkan_result_name(VkResult result) {
    switch (result) {
        case VK_SUCCESS: return "VK_SUCCESS";
        case VK_NOT_READY: return "VK_NOT_READY";
        case VK_TIMEOUT: return "VK_TIMEOUT";
        case VK_EVENT_SET: return "VK_EVENT_SET";
        case VK_EVENT_RESET: return "VK_EVENT_RESET";
        case VK_INCOMPLETE: return "VK_INCOMPLETE";
        case VK_ERROR_OUT_OF_HOST_MEMORY: return "VK_ERROR_OUT_OF_HOST_MEMORY";
        case VK_ERROR_OUT_OF_DEVICE_MEMORY: return "VK_ERROR_OUT_OF_DEVICE_MEMORY";
        case VK_ERROR_INITIALIZATION_FAILED: return "VK_ERROR_INITIALIZATION_FAILED";
        case VK_ERROR_DEVICE_LOST: return "VK_ERROR_DEVICE_LOST";
        case VK_ERROR_MEMORY_MAP_FAILED: return "VK_ERROR_MEMORY_MAP_FAILED";
        case VK_ERROR_LAYER_NOT_PRESENT: return "VK_ERROR_LAYER_NOT_PRESENT";
        case VK_ERROR_EXTENSION_NOT_PRESENT: return "VK_ERROR_EXTENSION_NOT_PRESENT";
        case VK_ERROR_FEATURE_NOT_PRESENT: return "VK_ERROR_FEATURE_NOT_PRESENT";
        case VK_ERROR_INCOMPATIBLE_DRIVER: return "VK_ERROR_INCOMPATIBLE_DRIVER";
        case VK_ERROR_TOO_MANY_OBJECTS: return "VK_ERROR_TOO_MANY_OBJECTS";
        case VK_ERROR_FORMAT_NOT_SUPPORTED: return "VK_ERROR_FORMAT_NOT_SUPPORTED";
        default: return "VK_RESULT_OTHER";
    }
}

const char* vulkan_device_type_name(VkPhysicalDeviceType type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return "integrated-gpu";
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: return "discrete-gpu";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: return "virtual-gpu";
        case VK_PHYSICAL_DEVICE_TYPE_CPU: return "cpu";
        default: return "other";
    }
}

const char* vulkan_present_mode_name(VkPresentModeKHR mode) {
    switch (mode) {
        case VK_PRESENT_MODE_IMMEDIATE_KHR: return "immediate";
        case VK_PRESENT_MODE_MAILBOX_KHR: return "mailbox";
        case VK_PRESENT_MODE_FIFO_KHR: return "fifo";
        case VK_PRESENT_MODE_FIFO_RELAXED_KHR: return "fifo-relaxed";
        default: return "unknown";
    }
}

std::string vulkan_api_version_string(uint32_t version) {
    std::ostringstream out;
    out << VK_API_VERSION_MAJOR(version) << "."
        << VK_API_VERSION_MINOR(version) << "."
        << VK_API_VERSION_PATCH(version);
    return out.str();
}

std::string build_host_vulkan_probe_report() {
    std::ostringstream out;
    out << "host vulkan probe=android-vulkan-loader";

    uint32_t instance_extension_count = 0;
    VkResult result = vkEnumerateInstanceExtensionProperties(nullptr, &instance_extension_count, nullptr);
    if (result != VK_SUCCESS) {
        out << "\nvulkan enumerate instance extensions=fail result=" << vulkan_result_name(result);
        return out.str();
    }
    out << "\nvulkan instance extension count=" << instance_extension_count;

    VkApplicationInfo app_info{};
    app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app_info.pApplicationName = "PlibVulkanProbe";
    app_info.applicationVersion = VK_MAKE_VERSION(0, 4, 78);
    app_info.pEngineName = "Plib";
    app_info.engineVersion = VK_MAKE_VERSION(0, 4, 78);
    app_info.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo instance_info{};
    instance_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instance_info.pApplicationInfo = &app_info;

    VkInstance instance = VK_NULL_HANDLE;
    result = vkCreateInstance(&instance_info, nullptr, &instance);
    if (result != VK_SUCCESS) {
        out << "\nvulkan create instance=fail result=" << vulkan_result_name(result);
        return out.str();
    }
    out << "\nvulkan create instance=ok api=1.0";

    uint32_t physical_device_count = 0;
    result = vkEnumeratePhysicalDevices(instance, &physical_device_count, nullptr);
    if (result != VK_SUCCESS || physical_device_count == 0) {
        out << "\nvulkan physical device count=" << physical_device_count;
        out << "\nvulkan enumerate physical devices=" << (result == VK_SUCCESS ? "ok-empty" : vulkan_result_name(result));
        vkDestroyInstance(instance, nullptr);
        return out.str();
    }
    out << "\nvulkan physical device count=" << physical_device_count;

    std::vector<VkPhysicalDevice> devices(physical_device_count);
    result = vkEnumeratePhysicalDevices(instance, &physical_device_count, devices.data());
    if (result != VK_SUCCESS) {
        out << "\nvulkan enumerate physical devices=fail result=" << vulkan_result_name(result);
        vkDestroyInstance(instance, nullptr);
        return out.str();
    }

    VkPhysicalDeviceProperties properties{};
    vkGetPhysicalDeviceProperties(devices[0], &properties);
    VkPhysicalDeviceFeatures features{};
    vkGetPhysicalDeviceFeatures(devices[0], &features);
    out << "\nhost vulkan device=" << properties.deviceName;
    out << "\nhost vulkan api version=" << vulkan_api_version_string(properties.apiVersion);
    out << "\nhost vulkan driver version=" << properties.driverVersion;
    out << "\nhost vulkan vendor id=0x" << std::hex << properties.vendorID << std::dec;
    out << "\nhost vulkan device id=0x" << std::hex << properties.deviceID << std::dec;
    out << "\nhost vulkan device type=" << vulkan_device_type_name(properties.deviceType);
    out << "\nhost vulkan max image dimension 2d=" << properties.limits.maxImageDimension2D;
    out << "\nhost vulkan max memory allocation count=" << properties.limits.maxMemoryAllocationCount;
    out << "\nhost vulkan feature robust buffer access=" << (features.robustBufferAccess ? "true" : "false");
    out << "\nhost vulkan feature geometry shader=" << (features.geometryShader ? "true" : "false");
    out << "\nhost vulkan feature sampler anisotropy=" << (features.samplerAnisotropy ? "true" : "false");

    uint32_t queue_family_count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(devices[0], &queue_family_count, nullptr);
    std::vector<VkQueueFamilyProperties> queue_families(queue_family_count);
    vkGetPhysicalDeviceQueueFamilyProperties(devices[0], &queue_family_count, queue_families.data());
    int graphics_queue_family = -1;
    for (uint32_t index = 0; index < queue_family_count; ++index) {
        if ((queue_families[index].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
            graphics_queue_family = static_cast<int>(index);
            break;
        }
    }
    out << "\nhost vulkan queue family count=" << queue_family_count;
    out << "\nhost vulkan graphics queue family=" << graphics_queue_family;

    if (graphics_queue_family >= 0) {
        const float priority = 1.0F;
        VkDeviceQueueCreateInfo queue_info{};
        queue_info.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queue_info.queueFamilyIndex = static_cast<uint32_t>(graphics_queue_family);
        queue_info.queueCount = 1;
        queue_info.pQueuePriorities = &priority;

        VkDeviceCreateInfo device_info{};
        device_info.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        device_info.queueCreateInfoCount = 1;
        device_info.pQueueCreateInfos = &queue_info;

        VkDevice device = VK_NULL_HANDLE;
        result = vkCreateDevice(devices[0], &device_info, nullptr, &device);
        if (result == VK_SUCCESS) {
            out << "\nvulkan create device=ok";
            vkDestroyDevice(device, nullptr);
        } else {
            out << "\nvulkan create device=fail result=" << vulkan_result_name(result);
        }
    }

    const bool hardware =
        physical_device_count > 0 &&
        graphics_queue_family >= 0 &&
        properties.deviceType != VK_PHYSICAL_DEVICE_TYPE_CPU;
    out << "\nhost vulkan hardware candidate=" << (hardware ? "true" : "false");

    vkDestroyInstance(instance, nullptr);
    return out.str();
}

std::string build_host_gpu_probe_report() {
    std::ostringstream out;
    out << "host gpu probe=android-egl-gles-pbuffer";

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        out << "\negl display=fail error=" << egl_error_hex();
        return out.str();
    }

    EGLint major = 0;
    EGLint minor = 0;
    if (eglInitialize(display, &major, &minor) != EGL_TRUE) {
        out << "\negl initialize=fail error=" << egl_error_hex();
        return out.str();
    }
    out << "\negl initialize=ok version=" << major << "." << minor;

    const EGLint config_attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE,
    };
    EGLConfig config = nullptr;
    EGLint config_count = 0;
    if (eglChooseConfig(display, config_attribs, &config, 1, &config_count) != EGL_TRUE || config_count < 1) {
        out << "\negl choose config=fail error=" << egl_error_hex();
        eglTerminate(display);
        return out.str();
    }
    out << "\negl choose config=ok count=" << config_count;

    const EGLint surface_attribs[] = {
        EGL_WIDTH, 16,
        EGL_HEIGHT, 16,
        EGL_NONE,
    };
    EGLSurface surface = eglCreatePbufferSurface(display, config, surface_attribs);
    if (surface == EGL_NO_SURFACE) {
        out << "\negl pbuffer surface=fail error=" << egl_error_hex();
        eglTerminate(display);
        return out.str();
    }
    out << "\negl pbuffer surface=ok size=16x16";

    const EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE,
    };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attribs);
    if (context == EGL_NO_CONTEXT) {
        out << "\negl context=fail error=" << egl_error_hex();
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return out.str();
    }
    out << "\negl context=ok api=gles2";

    if (eglMakeCurrent(display, surface, surface, context) != EGL_TRUE) {
        out << "\negl make current=fail error=" << egl_error_hex();
        eglDestroyContext(display, context);
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return out.str();
    }
    out << "\negl make current=ok";

    glViewport(0, 0, 16, 16);
    glClearColor(0.125F, 0.25F, 0.5F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT);
    const GLenum gl_error = glGetError();

    const auto vendor = safe_gl_string(GL_VENDOR);
    const auto renderer = safe_gl_string(GL_RENDERER);
    const auto version = safe_gl_string(GL_VERSION);
    const auto shading_language = safe_gl_string(GL_SHADING_LANGUAGE_VERSION);
    const bool software = renderer_looks_software(vendor, renderer);
    out << "\ngl vendor=" << vendor;
    out << "\ngl renderer=" << renderer;
    out << "\ngl version=" << version;
    out << "\ngl shading language=" << shading_language;
    out << "\ngl clear error=0x" << std::hex << gl_error << std::dec;
    out << "\nhost gpu software renderer=" << (software ? "true" : "false");
    out << "\nhost gpu hardware candidate=" << (!software && gl_error == GL_NO_ERROR ? "true" : "false");

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    return out.str();
}


struct SurfaceFrameCommand {
    float red = 0.05F;
    float green = 0.18F;
    float blue = 0.45F;
    std::string tag = "host-default";
};

std::vector<SurfaceFrameCommand> parse_surface_frames(const std::string& encoded) {
    std::vector<SurfaceFrameCommand> frames;
    std::istringstream input(encoded);
    std::string line;
    while (std::getline(input, line)) {
        if (line.empty()) continue;
        std::istringstream parts(line);
        SurfaceFrameCommand cmd;
        if (parts >> cmd.red >> cmd.green >> cmd.blue >> cmd.tag) {
            cmd.red = std::max(0.0F, std::min(1.0F, cmd.red));
            cmd.green = std::max(0.0F, std::min(1.0F, cmd.green));
            cmd.blue = std::max(0.0F, std::min(1.0F, cmd.blue));
            frames.push_back(cmd);
        }
    }
    if (frames.empty()) {
        frames.push_back(SurfaceFrameCommand{});
    }
    return frames;
}

struct VulkanSurfaceClearCommand {
    float red = 0.12F;
    float green = 0.64F;
    float blue = 0.92F;
    float alpha = 1.0F;
    std::string tag = "host-vulkan-clear";
    std::string source = "host-default";
};

float parse_float_token(const std::string& text, const std::string& key, float fallback) {
    const auto start = text.find(key);
    if (start == std::string::npos) return fallback;
    const auto value_start = start + key.size();
    char* end = nullptr;
    const float value = std::strtof(text.c_str() + value_start, &end);
    if (end == text.c_str() + value_start) return fallback;
    return std::max(0.0F, std::min(1.0F, value));
}

std::string parse_string_token(const std::string& text, const std::string& key, const std::string& fallback) {
    const auto start = text.find(key);
    if (start == std::string::npos) return fallback;
    auto value_start = start + key.size();
    auto value_end = text.find(' ', value_start);
    if (value_end == std::string::npos) value_end = text.size();
    if (value_end <= value_start) return fallback;
    return text.substr(value_start, value_end - value_start);
}

VulkanSurfaceClearCommand parse_vulkan_surface_clear_command(const std::string& request) {
    VulkanSurfaceClearCommand command;
    if (request.rfind("ALR_VK_SURFACE_CLEAR_REQUEST ", 0) == 0) {
        command.source = "guest-request";
    }
    command.red = parse_float_token(request, "red=", command.red);
    command.green = parse_float_token(request, "green=", command.green);
    command.blue = parse_float_token(request, "blue=", command.blue);
    command.alpha = parse_float_token(request, "alpha=", command.alpha);
    command.tag = parse_string_token(request, "tag=", command.tag);
    return command;
}

std::string render_to_android_surface_frames(JNIEnv* env, jobject surface_obj, const std::string& encoded_frames) {
    const auto frames = parse_surface_frames(encoded_frames);
    const auto render_started = std::chrono::steady_clock::now();
    std::ostringstream out;
    out << "host gpu surface renderer=android-surface-egl-gles";
    out << "\nsurface frame stream protocol=gui-compositor-clear-color-and-triangle-v5";
    out << "\nsurface requested frames=" << frames.size();
    if (surface_obj == nullptr) {
        out << "\nsurface render=fail reason=null-surface";
        return out.str();
    }
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface_obj);
    if (window == nullptr) {
        out << "\nsurface render=fail reason=ANativeWindow_fromSurface";
        return out.str();
    }
    out << "\nsurface window=ok width=" << ANativeWindow_getWidth(window)
        << " height=" << ANativeWindow_getHeight(window);

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        out << "\nsurface egl display=fail error=" << egl_error_hex();
        ANativeWindow_release(window);
        return out.str();
    }
    EGLint major = 0;
    EGLint minor = 0;
    if (eglInitialize(display, &major, &minor) != EGL_TRUE) {
        out << "\nsurface egl initialize=fail error=" << egl_error_hex();
        ANativeWindow_release(window);
        return out.str();
    }
    out << "\nsurface egl initialize=ok version=" << major << "." << minor;

    const EGLint config_attribs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE,
    };
    EGLConfig config = nullptr;
    EGLint config_count = 0;
    if (eglChooseConfig(display, config_attribs, &config, 1, &config_count) != EGL_TRUE || config_count < 1) {
        out << "\nsurface egl choose config=fail error=" << egl_error_hex();
        eglTerminate(display);
        ANativeWindow_release(window);
        return out.str();
    }
    out << "\nsurface egl choose config=ok count=" << config_count;

    EGLSurface egl_surface = eglCreateWindowSurface(display, config, window, nullptr);
    if (egl_surface == EGL_NO_SURFACE) {
        out << "\nsurface egl window surface=fail error=" << egl_error_hex();
        eglTerminate(display);
        ANativeWindow_release(window);
        return out.str();
    }
    out << "\nsurface egl window surface=ok";

    const EGLint context_attribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attribs);
    if (context == EGL_NO_CONTEXT) {
        out << "\nsurface egl context=fail error=" << egl_error_hex();
        eglDestroySurface(display, egl_surface);
        eglTerminate(display);
        ANativeWindow_release(window);
        return out.str();
    }
    out << "\nsurface egl context=ok api=gles2";

    if (eglMakeCurrent(display, egl_surface, egl_surface, context) != EGL_TRUE) {
        out << "\nsurface egl make current=fail error=" << egl_error_hex();
        eglDestroyContext(display, context);
        eglDestroySurface(display, egl_surface);
        eglTerminate(display);
        ANativeWindow_release(window);
        return out.str();
    }
    out << "\nsurface egl make current=ok";

    const int width = std::max(1, ANativeWindow_getWidth(window));
    const int height = std::max(1, ANativeWindow_getHeight(window));
    glViewport(0, 0, width, height);
    const auto vendor = safe_gl_string(GL_VENDOR);
    const auto renderer = safe_gl_string(GL_RENDERER);
    const bool software = renderer_looks_software(vendor, renderer);
    out << "\nsurface gl vendor=" << vendor;
    out << "\nsurface gl renderer=" << renderer;

    int rendered = 0;
    int wayland_rendered = 0;
    int x11_rendered = 0;
    int gles_shim_rendered = 0;
    int gles_shim_draw_rendered = 0;
    int native_gles_rendered = 0;
    int other_rendered = 0;
    long long gles_shim_elapsed_us = 0;
    long long gles_shim_draw_elapsed_us = 0;
    long long native_gles_elapsed_us = 0;
    GLuint triangle_program = 0;
    GLint triangle_color_location = -1;
    GLenum last_gl_error = GL_NO_ERROR;
    EGLBoolean last_swapped = EGL_FALSE;
    std::string last_tag;
    for (size_t i = 0; i < frames.size(); ++i) {
        const auto& frame = frames[i];
        const auto frame_started = std::chrono::steady_clock::now();
        const bool draw_triangle = frame.tag.rfind("GLES_DRAW-", 0) == 0;
        if (draw_triangle) {
            if (triangle_program == 0) {
                triangle_program = create_surface_triangle_program(out);
                triangle_color_location = triangle_program == 0 ? -1 : glGetUniformLocation(triangle_program, "uColor");
            }
            static const GLfloat vertices[] = {
                0.0F, 0.72F,
                -0.68F, -0.58F,
                0.68F, -0.58F,
            };
            glClearColor(0.03F, 0.04F, 0.06F, 1.0F);
            glClear(GL_COLOR_BUFFER_BIT);
            if (triangle_program != 0 && triangle_color_location >= 0) {
                glUseProgram(triangle_program);
                glUniform4f(triangle_color_location, frame.red, frame.green, frame.blue, 1.0F);
                glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, vertices);
                glEnableVertexAttribArray(0);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                glDisableVertexAttribArray(0);
            }
        } else {
            glClearColor(frame.red, frame.green, frame.blue, 1.0F);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        last_gl_error = glGetError();
        last_swapped = eglSwapBuffers(display, egl_surface);
        const auto frame_elapsed_us = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - frame_started
        ).count();
        out << "\nsurface frame " << (i + 1) << " tag=" << frame.tag
            << " color=" << frame.red << "," << frame.green << "," << frame.blue
            << " gl_error=0x" << std::hex << last_gl_error << std::dec
            << " swap=" << (last_swapped == EGL_TRUE ? "ok" : "fail")
            << " elapsed_us=" << frame_elapsed_us;
        if (last_swapped != EGL_TRUE) {
            out << " error=" << egl_error_hex();
            break;
        }
        if (last_gl_error != GL_NO_ERROR) {
            break;
        }
        ++rendered;
        if (frame.tag.rfind("WAYLAND-", 0) == 0) {
            ++wayland_rendered;
        } else if (frame.tag.rfind("X11-", 0) == 0) {
            ++x11_rendered;
        } else if (frame.tag.rfind("GLES-", 0) == 0) {
            ++gles_shim_rendered;
            gles_shim_elapsed_us += frame_elapsed_us;
        } else if (frame.tag.rfind("GLES_DRAW-", 0) == 0) {
            ++gles_shim_draw_rendered;
            gles_shim_draw_elapsed_us += frame_elapsed_us;
        } else if (frame.tag.rfind("NATIVE_GLES-", 0) == 0) {
            ++native_gles_rendered;
            native_gles_elapsed_us += frame_elapsed_us;
        } else {
            ++other_rendered;
        }
        last_tag = frame.tag;
    }
    const int dropped = static_cast<int>(frames.size()) - rendered;
    const auto render_elapsed_us = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now() - render_started
    ).count();
    out << "\nsurface wayland frames rendered=" << wayland_rendered;
    out << "\nsurface x11 frames rendered=" << x11_rendered;
    out << "\nsurface gles shim frames rendered=" << gles_shim_rendered;
    out << "\nsurface gles shim draw frames rendered=" << gles_shim_draw_rendered;
    out << "\nsurface native gles frames rendered=" << native_gles_rendered;
    out << "\nsurface other frames rendered=" << other_rendered;
    out << "\nsurface gui total frames rendered=" << (wayland_rendered + x11_rendered);
    out << "\nsurface frames rendered=" << rendered;
    out << "\nsurface frames dropped=" << dropped;
    out << "\nsurface render elapsed us=" << render_elapsed_us;
    out << "\nsurface render elapsed ms=" << (render_elapsed_us / 1000);
    out << "\nsurface average frame render us=" << (rendered > 0 ? render_elapsed_us / rendered : 0);
    const long long gles_avg_us = gles_shim_rendered > 0 ? gles_shim_elapsed_us / gles_shim_rendered : 0;
    const long long gles_draw_avg_us = gles_shim_draw_rendered > 0 ? gles_shim_draw_elapsed_us / gles_shim_draw_rendered : 0;
    const long long native_gles_avg_us = native_gles_rendered > 0 ? native_gles_elapsed_us / native_gles_rendered : 0;
    out << "\nsurface gles shim render elapsed us=" << gles_shim_elapsed_us;
    out << "\nsurface gles shim average frame render us=" << gles_avg_us;
    out << "\nsurface gles shim draw render elapsed us=" << gles_shim_draw_elapsed_us;
    out << "\nsurface gles shim draw average frame render us=" << gles_draw_avg_us;
    out << "\nsurface native gles render elapsed us=" << native_gles_elapsed_us;
    out << "\nsurface native gles average frame render us=" << native_gles_avg_us;
    out << "\nsurface gles shim vs native average ratio pct=" << (native_gles_avg_us > 0 ? (gles_avg_us * 100) / native_gles_avg_us : 0);
    out << "\nsurface frame lossless=" << (dropped == 0 ? "true" : "false");
    out << "\nsurface last guest command tag=" << (last_tag.empty() ? "missing" : last_tag);
    out << "\nsurface gl clear error=0x" << std::hex << last_gl_error << std::dec;
    out << "\nsurface egl swap buffers=" << (last_swapped == EGL_TRUE ? "ok" : "fail");
    out << "\nsurface gpu software renderer=" << (software ? "true" : "false");
    out << "\nsurface gpu hardware render=" << (!software && last_gl_error == GL_NO_ERROR && last_swapped == EGL_TRUE ? "true" : "false");
    out << "\nguest gpu ipc bridge hardware render=" << (!software && rendered == static_cast<int>(frames.size()) && rendered > 0 ? "true" : "false");
    out << "\nguest gui gpu compositor hardware render=" << (!software && rendered == static_cast<int>(frames.size()) && rendered > 0 ? "true" : "false");
    out << "\nguest wayland/x11 gui gpu surface hardware render=" << (!software && rendered == static_cast<int>(frames.size()) && rendered > 0 ? "true" : "false");
    out << "\nguest gpu bridge hardware render=" << (!software && rendered > 0 ? "true" : "false");
    out << "\nguest egl swap via android surface=" << (!software && (gles_shim_rendered + gles_shim_draw_rendered) > 0 && last_swapped == EGL_TRUE ? "true" : "false");
    out << "\nguest gles hardware render=" << (!software && (gles_shim_rendered + gles_shim_draw_rendered) > 0 && last_gl_error == GL_NO_ERROR && last_swapped == EGL_TRUE ? "true" : "false");
    out << "\nguest gles draw via android surface=" << (!software && gles_shim_draw_rendered > 0 && last_gl_error == GL_NO_ERROR && last_swapped == EGL_TRUE ? "true" : "false");

    if (triangle_program != 0) {
        glDeleteProgram(triangle_program);
    }
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, egl_surface);
    eglTerminate(display);
    ANativeWindow_release(window);
    return out.str();
}

std::string render_vulkan_clear_to_android_surface(JNIEnv* env, jobject surface_obj, const std::string& clear_request) {
    const auto clear_command = parse_vulkan_surface_clear_command(clear_request);
    const auto render_started = std::chrono::steady_clock::now();
    std::ostringstream out;
    out << "host vulkan surface renderer=android-surface-vulkan-swapchain";
    out << "\nsurface vulkan clear request=" << (clear_request.empty() ? "missing" : clear_request);
    out << "\nsurface vulkan clear request source=" << clear_command.source;
    out << "\nsurface vulkan clear request tag=" << clear_command.tag;
    if (surface_obj == nullptr) {
        out << "\nsurface vulkan render=fail reason=null-surface";
        return out.str();
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface_obj);
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkCommandPool command_pool = VK_NULL_HANDLE;
    VkSemaphore image_available = VK_NULL_HANDLE;
    VkSemaphore render_finished = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    VkPhysicalDevice physical_device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    VkFormat image_format = VK_FORMAT_UNDEFINED;
    uint32_t queue_family_index = 0;
    uint32_t image_index = 0;
    uint32_t swapchain_image_count = 0;
    VkExtent2D extent{};
    VkPresentModeKHR present_mode = VK_PRESENT_MODE_FIFO_KHR;
    VkResult result = VK_SUCCESS;
    bool submitted = false;
    bool presented = false;

    const auto cleanup = [&]() {
        if (device != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(device);
            if (fence != VK_NULL_HANDLE) vkDestroyFence(device, fence, nullptr);
            if (render_finished != VK_NULL_HANDLE) vkDestroySemaphore(device, render_finished, nullptr);
            if (image_available != VK_NULL_HANDLE) vkDestroySemaphore(device, image_available, nullptr);
            if (command_pool != VK_NULL_HANDLE) vkDestroyCommandPool(device, command_pool, nullptr);
            if (swapchain != VK_NULL_HANDLE) vkDestroySwapchainKHR(device, swapchain, nullptr);
            vkDestroyDevice(device, nullptr);
        }
        if (surface != VK_NULL_HANDLE) vkDestroySurfaceKHR(instance, surface, nullptr);
        if (instance != VK_NULL_HANDLE) vkDestroyInstance(instance, nullptr);
        if (window != nullptr) ANativeWindow_release(window);
    };

    if (window == nullptr) {
        out << "\nsurface vulkan render=fail reason=ANativeWindow_fromSurface";
        return out.str();
    }
    out << "\nsurface vulkan window=ok width=" << ANativeWindow_getWidth(window)
        << " height=" << ANativeWindow_getHeight(window);

    VkApplicationInfo app_info{};
    app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app_info.pApplicationName = "PlibVulkanSurfaceClear";
    app_info.applicationVersion = VK_MAKE_VERSION(0, 4, 81);
    app_info.pEngineName = "Plib";
    app_info.engineVersion = VK_MAKE_VERSION(0, 4, 81);
    app_info.apiVersion = VK_API_VERSION_1_0;

    const char* instance_extensions[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME,
    };
    VkInstanceCreateInfo instance_info{};
    instance_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instance_info.pApplicationInfo = &app_info;
    instance_info.enabledExtensionCount = 2;
    instance_info.ppEnabledExtensionNames = instance_extensions;
    result = vkCreateInstance(&instance_info, nullptr, &instance);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create instance=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan create instance=ok";

    VkAndroidSurfaceCreateInfoKHR surface_info{};
    surface_info.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surface_info.window = window;
    result = vkCreateAndroidSurfaceKHR(instance, &surface_info, nullptr, &surface);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create android surface=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan create android surface=ok";

    uint32_t physical_device_count = 0;
    result = vkEnumeratePhysicalDevices(instance, &physical_device_count, nullptr);
    if (result != VK_SUCCESS || physical_device_count == 0) {
        out << "\nsurface vulkan physical device count=" << physical_device_count;
        out << "\nsurface vulkan enumerate physical devices=" << (result == VK_SUCCESS ? "ok-empty" : vulkan_result_name(result));
        cleanup();
        return out.str();
    }
    std::vector<VkPhysicalDevice> devices(physical_device_count);
    result = vkEnumeratePhysicalDevices(instance, &physical_device_count, devices.data());
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan enumerate physical devices=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan physical device count=" << physical_device_count;

    int selected_queue_family = -1;
    VkPhysicalDeviceProperties properties{};
    for (VkPhysicalDevice candidate : devices) {
        uint32_t queue_family_count = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &queue_family_count, nullptr);
        std::vector<VkQueueFamilyProperties> queue_families(queue_family_count);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &queue_family_count, queue_families.data());
        for (uint32_t index = 0; index < queue_family_count; ++index) {
            VkBool32 supported = VK_FALSE;
            vkGetPhysicalDeviceSurfaceSupportKHR(candidate, index, surface, &supported);
            if ((queue_families[index].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0 && supported == VK_TRUE) {
                physical_device = candidate;
                selected_queue_family = static_cast<int>(index);
                break;
            }
        }
        if (physical_device != VK_NULL_HANDLE) break;
    }
    if (physical_device == VK_NULL_HANDLE || selected_queue_family < 0) {
        out << "\nsurface vulkan graphics present queue=fail";
        cleanup();
        return out.str();
    }
    queue_family_index = static_cast<uint32_t>(selected_queue_family);
    vkGetPhysicalDeviceProperties(physical_device, &properties);
    out << "\nsurface vulkan device=" << properties.deviceName;
    out << "\nsurface vulkan api version=" << vulkan_api_version_string(properties.apiVersion);
    out << "\nsurface vulkan device type=" << vulkan_device_type_name(properties.deviceType);
    out << "\nsurface vulkan graphics present queue=" << queue_family_index;

    uint32_t device_extension_count = 0;
    vkEnumerateDeviceExtensionProperties(physical_device, nullptr, &device_extension_count, nullptr);
    std::vector<VkExtensionProperties> device_extensions(device_extension_count);
    vkEnumerateDeviceExtensionProperties(physical_device, nullptr, &device_extension_count, device_extensions.data());
    bool has_swapchain_extension = false;
    for (const auto& extension : device_extensions) {
        if (std::string(extension.extensionName) == VK_KHR_SWAPCHAIN_EXTENSION_NAME) {
            has_swapchain_extension = true;
            break;
        }
    }
    if (!has_swapchain_extension) {
        out << "\nsurface vulkan swapchain extension=missing";
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan swapchain extension=ok";

    const float queue_priority = 1.0F;
    VkDeviceQueueCreateInfo queue_info{};
    queue_info.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queue_info.queueFamilyIndex = queue_family_index;
    queue_info.queueCount = 1;
    queue_info.pQueuePriorities = &queue_priority;
    const char* device_extensions_required[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkDeviceCreateInfo device_info{};
    device_info.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    device_info.queueCreateInfoCount = 1;
    device_info.pQueueCreateInfos = &queue_info;
    device_info.enabledExtensionCount = 1;
    device_info.ppEnabledExtensionNames = device_extensions_required;
    result = vkCreateDevice(physical_device, &device_info, nullptr, &device);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create device=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan create device=ok";
    vkGetDeviceQueue(device, queue_family_index, 0, &queue);

    VkSurfaceCapabilitiesKHR capabilities{};
    result = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical_device, surface, &capabilities);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan capabilities=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan supported usage=0x" << std::hex << capabilities.supportedUsageFlags << std::dec;
    if ((capabilities.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT) == 0) {
        out << "\nsurface vulkan transfer dst support=false";
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan transfer dst support=true";

    uint32_t format_count = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(physical_device, surface, &format_count, nullptr);
    std::vector<VkSurfaceFormatKHR> formats(format_count);
    vkGetPhysicalDeviceSurfaceFormatsKHR(physical_device, surface, &format_count, formats.data());
    if (formats.empty()) {
        out << "\nsurface vulkan formats=fail count=0";
        cleanup();
        return out.str();
    }
    VkSurfaceFormatKHR chosen_format = formats[0];
    if (formats.size() == 1 && formats[0].format == VK_FORMAT_UNDEFINED) {
        chosen_format.format = VK_FORMAT_R8G8B8A8_UNORM;
        chosen_format.colorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    }
    image_format = chosen_format.format;
    out << "\nsurface vulkan format=" << image_format;

    uint32_t present_mode_count = 0;
    vkGetPhysicalDeviceSurfacePresentModesKHR(physical_device, surface, &present_mode_count, nullptr);
    std::vector<VkPresentModeKHR> present_modes(present_mode_count);
    vkGetPhysicalDeviceSurfacePresentModesKHR(physical_device, surface, &present_mode_count, present_modes.data());
    for (VkPresentModeKHR mode : present_modes) {
        if (mode == VK_PRESENT_MODE_MAILBOX_KHR) {
            present_mode = mode;
            break;
        }
    }
    out << "\nsurface vulkan present mode=" << vulkan_present_mode_name(present_mode);

    if (capabilities.currentExtent.width != UINT32_MAX) {
        extent = capabilities.currentExtent;
    } else {
        extent.width = static_cast<uint32_t>(std::max(1, ANativeWindow_getWidth(window)));
        extent.height = static_cast<uint32_t>(std::max(1, ANativeWindow_getHeight(window)));
        extent.width = std::max(capabilities.minImageExtent.width, std::min(capabilities.maxImageExtent.width, extent.width));
        extent.height = std::max(capabilities.minImageExtent.height, std::min(capabilities.maxImageExtent.height, extent.height));
    }
    out << "\nsurface vulkan extent=" << extent.width << "x" << extent.height;

    uint32_t min_image_count = capabilities.minImageCount + 1;
    if (capabilities.maxImageCount > 0 && min_image_count > capabilities.maxImageCount) {
        min_image_count = capabilities.maxImageCount;
    }
    VkSwapchainCreateInfoKHR swapchain_info{};
    swapchain_info.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    swapchain_info.surface = surface;
    swapchain_info.minImageCount = min_image_count;
    swapchain_info.imageFormat = image_format;
    swapchain_info.imageColorSpace = chosen_format.colorSpace;
    swapchain_info.imageExtent = extent;
    swapchain_info.imageArrayLayers = 1;
    swapchain_info.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    swapchain_info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swapchain_info.preTransform = capabilities.currentTransform;
    swapchain_info.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    swapchain_info.presentMode = present_mode;
    swapchain_info.clipped = VK_TRUE;
    result = vkCreateSwapchainKHR(device, &swapchain_info, nullptr, &swapchain);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create swapchain=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan create swapchain=ok";

    result = vkGetSwapchainImagesKHR(device, swapchain, &swapchain_image_count, nullptr);
    if (result != VK_SUCCESS || swapchain_image_count == 0) {
        out << "\nsurface vulkan swapchain image count=" << swapchain_image_count;
        out << "\nsurface vulkan get swapchain images=" << (result == VK_SUCCESS ? "ok-empty" : vulkan_result_name(result));
        cleanup();
        return out.str();
    }
    std::vector<VkImage> images(swapchain_image_count);
    result = vkGetSwapchainImagesKHR(device, swapchain, &swapchain_image_count, images.data());
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan get swapchain images=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan swapchain image count=" << swapchain_image_count;

    VkCommandPoolCreateInfo pool_info{};
    pool_info.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    pool_info.queueFamilyIndex = queue_family_index;
    pool_info.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    result = vkCreateCommandPool(device, &pool_info, nullptr, &command_pool);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create command pool=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }

    VkCommandBuffer command_buffer = VK_NULL_HANDLE;
    VkCommandBufferAllocateInfo command_buffer_info{};
    command_buffer_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    command_buffer_info.commandPool = command_pool;
    command_buffer_info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    command_buffer_info.commandBufferCount = 1;
    result = vkAllocateCommandBuffers(device, &command_buffer_info, &command_buffer);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan allocate command buffer=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }

    VkSemaphoreCreateInfo semaphore_info{};
    semaphore_info.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    result = vkCreateSemaphore(device, &semaphore_info, nullptr, &image_available);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create image semaphore=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    result = vkCreateSemaphore(device, &semaphore_info, nullptr, &render_finished);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create render semaphore=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    VkFenceCreateInfo fence_info{};
    fence_info.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    result = vkCreateFence(device, &fence_info, nullptr, &fence);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan create fence=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }

    result = vkAcquireNextImageKHR(device, swapchain, 1000000000ULL, image_available, VK_NULL_HANDLE, &image_index);
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        out << "\nsurface vulkan acquire image=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan acquire image=ok index=" << image_index;

    VkCommandBufferBeginInfo begin_info{};
    begin_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    result = vkBeginCommandBuffer(command_buffer, &begin_info);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan begin command buffer=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }

    VkImageMemoryBarrier to_transfer{};
    to_transfer.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    to_transfer.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    to_transfer.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    to_transfer.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    to_transfer.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    to_transfer.image = images[image_index];
    to_transfer.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    to_transfer.subresourceRange.levelCount = 1;
    to_transfer.subresourceRange.layerCount = 1;
    to_transfer.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    vkCmdPipelineBarrier(
        command_buffer,
        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT,
        0,
        0,
        nullptr,
        0,
        nullptr,
        1,
        &to_transfer
    );

    const VkClearColorValue clear_color{{clear_command.red, clear_command.green, clear_command.blue, clear_command.alpha}};
    VkImageSubresourceRange clear_range{};
    clear_range.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    clear_range.levelCount = 1;
    clear_range.layerCount = 1;
    vkCmdClearColorImage(command_buffer, images[image_index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &clear_color, 1, &clear_range);

    VkImageMemoryBarrier to_present{};
    to_present.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    to_present.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    to_present.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    to_present.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    to_present.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    to_present.image = images[image_index];
    to_present.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    to_present.subresourceRange.levelCount = 1;
    to_present.subresourceRange.layerCount = 1;
    to_present.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    vkCmdPipelineBarrier(
        command_buffer,
        VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0,
        0,
        nullptr,
        0,
        nullptr,
        1,
        &to_present
    );
    result = vkEndCommandBuffer(command_buffer);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan end command buffer=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan clear command=ok color="
        << clear_command.red << "," << clear_command.green << "," << clear_command.blue << "," << clear_command.alpha;

    const VkPipelineStageFlags wait_stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo submit_info{};
    submit_info.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit_info.waitSemaphoreCount = 1;
    submit_info.pWaitSemaphores = &image_available;
    submit_info.pWaitDstStageMask = &wait_stage;
    submit_info.commandBufferCount = 1;
    submit_info.pCommandBuffers = &command_buffer;
    submit_info.signalSemaphoreCount = 1;
    submit_info.pSignalSemaphores = &render_finished;
    result = vkQueueSubmit(queue, 1, &submit_info, fence);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan queue submit=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    submitted = true;
    result = vkWaitForFences(device, 1, &fence, VK_TRUE, 1000000000ULL);
    if (result != VK_SUCCESS) {
        out << "\nsurface vulkan wait fence=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    out << "\nsurface vulkan queue submit=ok";

    VkPresentInfoKHR present_info{};
    present_info.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    present_info.waitSemaphoreCount = 1;
    present_info.pWaitSemaphores = &render_finished;
    present_info.swapchainCount = 1;
    present_info.pSwapchains = &swapchain;
    present_info.pImageIndices = &image_index;
    result = vkQueuePresentKHR(queue, &present_info);
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        out << "\nsurface vulkan present=fail result=" << vulkan_result_name(result);
        cleanup();
        return out.str();
    }
    presented = true;
    vkQueueWaitIdle(queue);
    const bool hardware = properties.deviceType != VK_PHYSICAL_DEVICE_TYPE_CPU;
    const auto render_elapsed_us = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now() - render_started
    ).count();
    out << "\nsurface vulkan present=ok";
    out << "\nsurface vulkan hardware render=" << (hardware && submitted && presented ? "true" : "false");
    out << "\nsurface vulkan frames rendered=" << (presented ? 1 : 0);
    out << "\nsurface vulkan render elapsed us=" << render_elapsed_us;
    out << "\nandroid host vulkan surface execution=" << (hardware && submitted && presented ? "PASS" : "FAIL");

    cleanup();
    return out.str();
}

std::string probe_android_hardware_buffer_bridge() {
    constexpr uint32_t width = 320;
    constexpr uint32_t height = 180;
    constexpr int buffer_count = 3;
    const auto started = std::chrono::steady_clock::now();
    std::ostringstream out;
    out << "host ahardwarebuffer renderer=android-native-buffer-egl-image";
    out << "\nahardwarebuffer requested buffers=" << buffer_count;
    out << "\nahardwarebuffer requested size=" << width << "x" << height;
    out << "\nahardwarebuffer format=R8G8B8A8_UNORM";
    out << "\nahardwarebuffer usage=cpu-read-write+gpu-sampled+gpu-color-output";

    AHardwareBuffer_Desc request{};
    request.width = width;
    request.height = height;
    request.layers = 1;
    request.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    request.usage =
        AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY |
        AHARDWAREBUFFER_USAGE_CPU_READ_RARELY |
        AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
        AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;

    std::array<AHardwareBuffer*, buffer_count> buffers{};
    std::array<uint32_t, buffer_count> expected_checksums{};
    int allocated = 0;
    int cpu_written = 0;
    int cpu_verified = 0;
    uint32_t total_visible_bytes = 0;

    auto release_buffers = [&]() {
        for (auto* buffer : buffers) {
            if (buffer != nullptr) {
                AHardwareBuffer_release(buffer);
            }
        }
    };

    for (int index = 0; index < buffer_count; ++index) {
        AHardwareBuffer* buffer = nullptr;
        const int allocate_result = AHardwareBuffer_allocate(&request, &buffer);
        if (allocate_result != 0 || buffer == nullptr) {
            out << "\nahardwarebuffer allocate index=" << index << " result=" << allocate_result;
            release_buffers();
            out << "\nahardwarebuffer execution=FAIL";
            return out.str();
        }
        buffers[index] = buffer;
        ++allocated;

        AHardwareBuffer_Desc actual{};
        AHardwareBuffer_describe(buffer, &actual);
        out << "\nahardwarebuffer buffer " << index
            << " width=" << actual.width
            << " height=" << actual.height
            << " layers=" << actual.layers
            << " stride=" << actual.stride
            << " format=" << actual.format
            << " usage=0x" << std::hex << actual.usage << std::dec;
        if (actual.width != width || actual.height != height || actual.layers != 1 || actual.stride < width) {
            out << "\nahardwarebuffer describe valid=false";
            release_buffers();
            out << "\nahardwarebuffer execution=FAIL";
            return out.str();
        }

        void* write_address = nullptr;
        int lock_result = AHardwareBuffer_lock(
            buffer,
            AHARDWAREBUFFER_USAGE_CPU_WRITE_RARELY,
            -1,
            nullptr,
            &write_address
        );
        if (lock_result != 0 || write_address == nullptr) {
            out << "\nahardwarebuffer cpu write lock index=" << index << " result=" << lock_result;
            release_buffers();
            out << "\nahardwarebuffer execution=FAIL";
            return out.str();
        }

        std::vector<uint8_t> compact;
        compact.reserve(static_cast<size_t>(width) * height * 4u);
        const uint8_t red = static_cast<uint8_t>(48 + index * 39);
        const uint8_t green = static_cast<uint8_t>(72 + index * 29);
        const uint8_t blue = static_cast<uint8_t>(160 - index * 31);
        auto* base = static_cast<uint8_t*>(write_address);
        for (uint32_t y = 0; y < height; ++y) {
            auto* row = base + static_cast<size_t>(y) * actual.stride * 4u;
            for (uint32_t x = 0; x < width; ++x) {
                row[x * 4u + 0u] = red;
                row[x * 4u + 1u] = green;
                row[x * 4u + 2u] = blue;
                row[x * 4u + 3u] = 255u;
                compact.push_back(red);
                compact.push_back(green);
                compact.push_back(blue);
                compact.push_back(255u);
            }
        }
        int fence_fd = -1;
        const int unlock_result = AHardwareBuffer_unlock(buffer, &fence_fd);
        if (fence_fd >= 0) {
            close(fence_fd);
        }
        if (unlock_result != 0) {
            out << "\nahardwarebuffer cpu write unlock index=" << index << " result=" << unlock_result;
            release_buffers();
            out << "\nahardwarebuffer execution=FAIL";
            return out.str();
        }
        ++cpu_written;
        expected_checksums[index] = fnv1a32_bytes(compact.data(), compact.size());
        total_visible_bytes += static_cast<uint32_t>(compact.size());

        void* read_address = nullptr;
        lock_result = AHardwareBuffer_lock(
            buffer,
            AHARDWAREBUFFER_USAGE_CPU_READ_RARELY,
            -1,
            nullptr,
            &read_address
        );
        if (lock_result != 0 || read_address == nullptr) {
            out << "\nahardwarebuffer cpu read lock index=" << index << " result=" << lock_result;
            release_buffers();
            out << "\nahardwarebuffer execution=FAIL";
            return out.str();
        }
        std::vector<uint8_t> readback;
        readback.reserve(static_cast<size_t>(width) * height * 4u);
        auto* read_base = static_cast<uint8_t*>(read_address);
        for (uint32_t y = 0; y < height; ++y) {
            auto* row = read_base + static_cast<size_t>(y) * actual.stride * 4u;
            readback.insert(readback.end(), row, row + static_cast<size_t>(width) * 4u);
        }
        fence_fd = -1;
        const int read_unlock_result = AHardwareBuffer_unlock(buffer, &fence_fd);
        if (fence_fd >= 0) {
            close(fence_fd);
        }
        if (read_unlock_result != 0) {
            out << "\nahardwarebuffer cpu read unlock index=" << index << " result=" << read_unlock_result;
            release_buffers();
            out << "\nahardwarebuffer execution=FAIL";
            return out.str();
        }
        const uint32_t read_checksum = fnv1a32_bytes(readback.data(), readback.size());
        const bool verified = read_checksum == expected_checksums[index];
        if (verified) {
            ++cpu_verified;
        }
        out << "\nahardwarebuffer payload index=" << index
            << " bytes=" << readback.size()
            << " checksum=" << hex_u32(read_checksum)
            << " verified=" << (verified ? "true" : "false");
    }

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        out << "\nahardwarebuffer egl display=fail error=" << egl_error_hex();
        release_buffers();
        out << "\nahardwarebuffer execution=FAIL";
        return out.str();
    }
    EGLint major = 0;
    EGLint minor = 0;
    if (eglInitialize(display, &major, &minor) != EGL_TRUE) {
        out << "\nahardwarebuffer egl initialize=fail error=" << egl_error_hex();
        release_buffers();
        out << "\nahardwarebuffer execution=FAIL";
        return out.str();
    }
    out << "\nahardwarebuffer egl initialize=ok version=" << major << "." << minor;

    const EGLint config_attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE,
    };
    EGLConfig config = nullptr;
    EGLint config_count = 0;
    if (eglChooseConfig(display, config_attribs, &config, 1, &config_count) != EGL_TRUE || config_count < 1) {
        out << "\nahardwarebuffer egl choose config=fail error=" << egl_error_hex();
        eglTerminate(display);
        release_buffers();
        out << "\nahardwarebuffer execution=FAIL";
        return out.str();
    }
    out << "\nahardwarebuffer egl choose config=ok count=" << config_count;

    const EGLint surface_attribs[] = {
        EGL_WIDTH, 16,
        EGL_HEIGHT, 16,
        EGL_NONE,
    };
    EGLSurface pbuffer = eglCreatePbufferSurface(display, config, surface_attribs);
    if (pbuffer == EGL_NO_SURFACE) {
        out << "\nahardwarebuffer egl pbuffer=fail error=" << egl_error_hex();
        eglTerminate(display);
        release_buffers();
        out << "\nahardwarebuffer execution=FAIL";
        return out.str();
    }

    const EGLint context_attribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attribs);
    if (context == EGL_NO_CONTEXT) {
        out << "\nahardwarebuffer egl context=fail error=" << egl_error_hex();
        eglDestroySurface(display, pbuffer);
        eglTerminate(display);
        release_buffers();
        out << "\nahardwarebuffer execution=FAIL";
        return out.str();
    }
    if (eglMakeCurrent(display, pbuffer, pbuffer, context) != EGL_TRUE) {
        out << "\nahardwarebuffer egl make current=fail error=" << egl_error_hex();
        eglDestroyContext(display, context);
        eglDestroySurface(display, pbuffer);
        eglTerminate(display);
        release_buffers();
        out << "\nahardwarebuffer execution=FAIL";
        return out.str();
    }

    auto get_native_client_buffer = reinterpret_cast<EglGetNativeClientBufferAndroidFn>(
        eglGetProcAddress("eglGetNativeClientBufferANDROID")
    );
    auto create_image = reinterpret_cast<EglCreateImageKhrFn>(
        eglGetProcAddress("eglCreateImageKHR")
    );
    auto destroy_image = reinterpret_cast<EglDestroyImageKhrFn>(
        eglGetProcAddress("eglDestroyImageKHR")
    );
    auto image_target_texture = reinterpret_cast<GlEglImageTargetTexture2DOesFn>(
        eglGetProcAddress("glEGLImageTargetTexture2DOES")
    );
    const bool import_functions_ready =
        get_native_client_buffer != nullptr &&
        create_image != nullptr &&
        destroy_image != nullptr &&
        image_target_texture != nullptr;
    out << "\nahardwarebuffer egl image functions=" << (import_functions_ready ? "ok" : "missing");
    if (!import_functions_ready) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(display, context);
        eglDestroySurface(display, pbuffer);
        eglTerminate(display);
        release_buffers();
        out << "\nahardwarebuffer execution=FAIL";
        return out.str();
    }

    int egl_imported = 0;
    GLenum last_gl_error = GL_NO_ERROR;
    for (int index = 0; index < buffer_count; ++index) {
        EGLClientBuffer client_buffer = get_native_client_buffer(buffers[index]);
        if (client_buffer == nullptr) {
            out << "\nahardwarebuffer egl client buffer index=" << index << " status=fail";
            continue;
        }
        const EGLint image_attribs[] = {
            EGL_IMAGE_PRESERVED_KHR, EGL_TRUE,
            EGL_NONE,
        };
        EGLImageKHR image = create_image(
            display,
            EGL_NO_CONTEXT,
            EGL_NATIVE_BUFFER_ANDROID,
            client_buffer,
            image_attribs
        );
        if (image == EGL_NO_IMAGE_KHR) {
            out << "\nahardwarebuffer egl image index=" << index << " status=fail error=" << egl_error_hex();
            continue;
        }
        GLuint texture = 0;
        glGenTextures(1, &texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        image_target_texture(GL_TEXTURE_2D, reinterpret_cast<GLeglImageOES>(image));
        last_gl_error = glGetError();
        if (last_gl_error == GL_NO_ERROR) {
            ++egl_imported;
        }
        out << "\nahardwarebuffer egl import index=" << index
            << " texture=" << texture
            << " gl_error=0x" << std::hex << last_gl_error << std::dec
            << " status=" << (last_gl_error == GL_NO_ERROR ? "ok" : "fail");
        glDeleteTextures(1, &texture);
        destroy_image(display, image);
    }

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, pbuffer);
    eglTerminate(display);
    release_buffers();

    const auto elapsed_us = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now() - started
    ).count();
    const bool passed =
        allocated == buffer_count &&
        cpu_written == buffer_count &&
        cpu_verified == buffer_count &&
        egl_imported == buffer_count &&
        last_gl_error == GL_NO_ERROR;
    out << "\nahardwarebuffer allocated buffers=" << allocated;
    out << "\nahardwarebuffer cpu written buffers=" << cpu_written;
    out << "\nahardwarebuffer cpu verified buffers=" << cpu_verified;
    out << "\nahardwarebuffer egl imported buffers=" << egl_imported;
    out << "\nahardwarebuffer visible payload bytes=" << total_visible_bytes;
    out << "\nahardwarebuffer host managed triple buffer=" << (passed ? "true" : "false");
    out << "\nahardwarebuffer egl image import=" << (egl_imported == buffer_count ? "ok" : "fail");
    out << "\nahardwarebuffer render elapsed us=" << elapsed_us;
    out << "\nahardwarebuffer execution=" << (passed ? "PASS" : "FAIL");
    return out.str();
}

void append_dlopen_probe(std::ostringstream& out, const std::string& path, const std::string& label) {
    dlerror();
    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        const char* error = dlerror();
        out << "\ndlopen " << label << "=fail: " << (error == nullptr ? "unknown" : error);
        return;
    }
    out << "\ndlopen " << label << "=ok";
    if (label == "libtalloc.so") {
        using VersionFn = int (*)();
        dlerror();
        auto major = reinterpret_cast<VersionFn>(dlsym(handle, "talloc_version_major"));
        const char* major_error = dlerror();
        dlerror();
        auto minor = reinterpret_cast<VersionFn>(dlsym(handle, "talloc_version_minor"));
        const char* minor_error = dlerror();
        if (major != nullptr && minor != nullptr) {
            out << " version=" << major() << "." << minor();
        } else {
            out << " version_symbol_error="
                << (major_error != nullptr ? major_error : "")
                << (minor_error != nullptr ? minor_error : "");
        }
    }
    dlclose(handle);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeRuntimeReport(
    JNIEnv* env,
    jobject /* thiz */,
    jstring package_name,
    jstring native_library_dir,
    jstring app_files_dir,
    jstring app_cache_dir,
    jstring rootfs_name,
    jstring program) {
    const auto input = alr::RuntimeReportInput{
        .package_name = jstring_to_string(env, package_name),
        .native_library_dir = jstring_to_string(env, native_library_dir),
        .app_files_dir = jstring_to_string(env, app_files_dir),
        .app_cache_dir = jstring_to_string(env, app_cache_dir),
        .rootfs_name = jstring_to_string(env, rootfs_name),
        .program = jstring_to_string(env, program),
    };
    const auto report = alr::build_runtime_report(input, alr::select_execution_backend(alr::ExecutionBackendKind::Proot));
    const auto launch = alr::build_loader_launch_plan(input);
    const auto proot = alr::build_proot_launch_plan(input);
    const auto alr_runtime = alr::build_alr_runtime_launch_plan(input);

    std::ostringstream out;
    out << report.text << "\n\nloader argv:";
    for (const auto& arg : launch.argv) {
        out << "\n  " << arg;
    }
    out << "\n\nloader env:";
    out << "\n  ALR_ROOTFS=" << launch.env.at("ALR_ROOTFS");
    out << "\n  ALR_PROGRAM=" << launch.env.at("ALR_PROGRAM");
    out << "\n  PATH=" << launch.env.at("PATH");
    out << "\n\nproot argv:";
    for (const auto& arg : proot.argv) {
        out << "\n  " << arg;
    }
    out << "\n\nproot env:";
    out << "\n  ALR_ROOTFS=" << proot.env.at("ALR_ROOTFS");
    out << "\n  ALR_PROGRAM=" << proot.env.at("ALR_PROGRAM");
    out << "\n  PROOT_LOADER=" << proot.env.at("PROOT_LOADER");
    out << "\n  PROOT_TMP_DIR=" << proot.env.at("PROOT_TMP_DIR");
    out << "\n  PROOT_NO_SECCOMP=" << proot.env.at("PROOT_NO_SECCOMP");
    out << "\n  PROOT_VERBOSE=" << proot.env.at("PROOT_VERBOSE");
    out << "\n  LD_LIBRARY_PATH=" << proot.env.at("LD_LIBRARY_PATH");
    out << "\n  PATH=" << proot.env.at("PATH");
    out << "\n\nalr runtime argv:";
    for (const auto& arg : alr_runtime.argv) {
        out << "\n  " << arg;
    }
    out << "\n\nalr runtime env:";
    out << "\n  ALR_ROOTFS=" << alr_runtime.env.at("ALR_ROOTFS");
    out << "\n  ALR_PROGRAM=" << alr_runtime.env.at("ALR_PROGRAM");
    out << "\n  ALR_BACKEND=" << alr_runtime.env.at("ALR_BACKEND");
    out << "\n  ALR_HOOK_PATH=" << alr_runtime.env.at("ALR_HOOK_PATH");
    out << "\n  ALR_INTERPOSER_PATH=" << alr_runtime.env.at("ALR_INTERPOSER_PATH");
    out << "\n  ALR_BRIDGE_PATH=" << alr_runtime.env.at("ALR_BRIDGE_PATH");
    out << "\n  ALR_CONFIG_FORMAT=" << alr_runtime.env.at("ALR_CONFIG_FORMAT");
    out << "\n  ALR_FAKE_ROOT=" << alr_runtime.env.at("ALR_FAKE_ROOT");
    out << "\n  ALR_TRACE_PATH=" << alr_runtime.env.at("ALR_TRACE_PATH");
    out << "\n  ALR_TRACE_EXEC=" << alr_runtime.env.at("ALR_TRACE_EXEC");
    out << "\n  PATH=" << alr_runtime.env.at("PATH");
    return env->NewStringUTF(out.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeLibraryProbe(
    JNIEnv* env,
    jobject /* thiz */,
    jstring native_library_dir) {
    const auto dir = jstring_to_string(env, native_library_dir);
    std::ostringstream out;
    append_dlopen_probe(out, join_path(dir, "libtalloc.so"), "libtalloc.so");
    append_dlopen_probe(out, join_path(dir, "libalr_proot.so"), "libalr_proot.so");
    append_dlopen_probe(out, join_path(dir, "libproot-loader.so"), "libproot-loader.so");
    append_dlopen_probe(out, join_path(dir, "libalr_runtime_launcher.so"), "libalr_runtime_launcher.so");
    append_dlopen_probe(out, join_path(dir, "libalr_runtime_hook.so"), "libalr_runtime_hook.so");
    append_dlopen_probe(out, join_path(dir, "libalr_runtime_interposer.so"), "libalr_runtime_interposer.so");
    return env->NewStringUTF(out.str().c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeHostGpuProbe(
    JNIEnv* env,
    jobject /* thiz */) {
    const auto report = build_host_gpu_probe_report();
    return env->NewStringUTF(report.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeHostVulkanProbe(
    JNIEnv* env,
    jobject /* thiz */) {
    const auto report = build_host_vulkan_probe_report();
    return env->NewStringUTF(report.c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeRenderGpuSurfaceFrames(
    JNIEnv* env,
    jobject /* thiz */,
    jobject surface,
    jstring encoded_frames) {
    const auto report = render_to_android_surface_frames(env, surface, jstring_to_string(env, encoded_frames));
    return env->NewStringUTF(report.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeRenderVulkanSurfaceClear(
    JNIEnv* env,
    jobject /* thiz */,
    jobject surface,
    jstring clear_request) {
    const auto report = render_vulkan_clear_to_android_surface(env, surface, jstring_to_string(env, clear_request));
    return env->NewStringUTF(report.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeHostHardwareBufferProbe(
    JNIEnv* env,
    jobject /* thiz */) {
    const auto report = probe_android_hardware_buffer_bridge();
    return env->NewStringUTF(report.c_str());
}
