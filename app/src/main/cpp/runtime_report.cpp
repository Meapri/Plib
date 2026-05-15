#include <jni.h>

#include <dlfcn.h>

#include <sstream>
#include <string>

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
    return env->NewStringUTF(out.str().c_str());
}
