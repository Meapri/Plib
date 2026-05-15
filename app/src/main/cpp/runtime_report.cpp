#include <jni.h>

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
    const auto report = alr::build_runtime_report(input);
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
