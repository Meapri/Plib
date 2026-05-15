#include <jni.h>

#include <sstream>
#include <string>

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

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_dev_chanwoo_androlinux_MainActivity_nativeRuntimeReport(
    JNIEnv* env,
    jobject /* thiz */,
    jstring package_name,
    jstring native_library_dir,
    jstring app_files_dir,
    jstring rootfs_name,
    jstring program) {
    const std::string package_name_s = jstring_to_string(env, package_name);
    const std::string native_library_dir_s = jstring_to_string(env, native_library_dir);
    const std::string app_files_dir_s = jstring_to_string(env, app_files_dir);
    const std::string rootfs_name_s = jstring_to_string(env, rootfs_name);
    const std::string program_s = jstring_to_string(env, program);

    const std::string executable = join_path(native_library_dir_s, "libalr-loader.so");
    const std::string rootfs_dir = join_path(join_path(app_files_dir_s, "rootfs"), rootfs_name_s);

    std::ostringstream out;
    out << "AndroLinux Runtime Lab\n\n";
    out << "package: " << package_name_s << "\n";
    out << "nativeLibraryDir: " << native_library_dir_s << "\n";
    out << "loader executable: " << executable << "\n";
    out << "app files dir: " << app_files_dir_s << "\n";
    out << "rootfs dir: " << rootfs_dir << "\n";
    out << "program: " << program_s << "\n\n";
    out << "PoC 1 policy: execute packaged native loader only; keep rootfs writable data separate.";

    return env->NewStringUTF(out.str().c_str());
}
