#include <jni.h>

#include <android/native_window_jni.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

#include <dlfcn.h>

#include <algorithm>
#include <cctype>
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
    std::ostringstream out;
    out << "host gpu surface renderer=android-surface-egl-gles";
    out << "\nsurface frame stream protocol=gui-compositor-clear-color-v4";
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
    int other_rendered = 0;
    GLenum last_gl_error = GL_NO_ERROR;
    EGLBoolean last_swapped = EGL_FALSE;
    std::string last_tag;
    for (size_t i = 0; i < frames.size(); ++i) {
        const auto& frame = frames[i];
        glClearColor(frame.red, frame.green, frame.blue, 1.0F);
        glClear(GL_COLOR_BUFFER_BIT);
        last_gl_error = glGetError();
        last_swapped = eglSwapBuffers(display, egl_surface);
        out << "\nsurface frame " << (i + 1) << " tag=" << frame.tag
            << " color=" << frame.red << "," << frame.green << "," << frame.blue
            << " gl_error=0x" << std::hex << last_gl_error << std::dec
            << " swap=" << (last_swapped == EGL_TRUE ? "ok" : "fail");
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
        } else {
            ++other_rendered;
        }
        last_tag = frame.tag;
    }
    const int dropped = static_cast<int>(frames.size()) - rendered;
    out << "\nsurface wayland frames rendered=" << wayland_rendered;
    out << "\nsurface x11 frames rendered=" << x11_rendered;
    out << "\nsurface gles shim frames rendered=" << gles_shim_rendered;
    out << "\nsurface other frames rendered=" << other_rendered;
    out << "\nsurface gui total frames rendered=" << (wayland_rendered + x11_rendered);
    out << "\nsurface frames rendered=" << rendered;
    out << "\nsurface frames dropped=" << dropped;
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
    out << "\nguest egl swap via android surface=" << (!software && gles_shim_rendered > 0 && last_swapped == EGL_TRUE ? "true" : "false");
    out << "\nguest gles hardware render=" << (!software && gles_shim_rendered > 0 && last_gl_error == GL_NO_ERROR && last_swapped == EGL_TRUE ? "true" : "false");

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
Java_dev_chanwoo_androlinux_MainActivity_nativeRenderGpuSurfaceFrames(
    JNIEnv* env,
    jobject /* thiz */,
    jobject surface,
    jstring encoded_frames) {
    const auto report = render_to_android_surface_frames(env, surface, jstring_to_string(env, encoded_frames));
    return env->NewStringUTF(report.c_str());
}
