#include <jni.h>

#include <android/native_window_jni.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <vulkan/vulkan.h>

#include <dlfcn.h>

#include <algorithm>
#include <cctype>
#include <chrono>
#include <sstream>
#include <string>
#include <vector>

#include "runtime_plan.hpp"

namespace {

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
