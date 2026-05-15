#include "runtime_plan.hpp"

#include <sstream>
#include <string>

namespace alr {
namespace {

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

RuntimeReport build_runtime_report(const RuntimeReportInput& input) {
    RuntimeReport report;
    report.loader_executable = join_path(input.native_library_dir, "libalr-loader.so");
    report.rootfs_dir = join_path(join_path(input.app_files_dir, "rootfs"), input.rootfs_name);

    std::ostringstream out;
    out << "AndroLinux Runtime Lab\n\n";
    out << "package: " << input.package_name << "\n";
    out << "nativeLibraryDir: " << input.native_library_dir << "\n";
    out << "loader executable: " << report.loader_executable << "\n";
    out << "app files dir: " << input.app_files_dir << "\n";
    out << "rootfs dir: " << report.rootfs_dir << "\n";
    out << "program: " << input.program << "\n\n";
    out << "PoC 1 policy: execute packaged native loader only; keep rootfs writable data separate.";
    report.text = out.str();
    return report;
}

}  // namespace alr
