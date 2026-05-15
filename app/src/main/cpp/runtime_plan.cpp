#include "runtime_plan.hpp"

#include <sstream>
#include <stdexcept>
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

std::string rootfs_dir_for(const RuntimeReportInput& input) {
    return join_path(join_path(input.app_files_dir, "rootfs"), input.rootfs_name);
}

std::string loader_executable_for(const RuntimeReportInput& input) {
    return join_path(input.native_library_dir, "libalr-loader.so");
}

void validate_input(const RuntimeReportInput& input) {
    if (input.program.empty() || input.program.front() != '/') {
        throw std::invalid_argument("program must be an absolute path inside the rootfs");
    }
}

}  // namespace

RuntimeReport build_runtime_report(const RuntimeReportInput& input) {
    validate_input(input);
    RuntimeReport report;
    report.loader_executable = loader_executable_for(input);
    report.rootfs_dir = rootfs_dir_for(input);

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

LoaderLaunchPlan build_loader_launch_plan(const RuntimeReportInput& input) {
    validate_input(input);
    LoaderLaunchPlan plan;
    plan.executable = loader_executable_for(input);
    const std::string rootfs_dir = rootfs_dir_for(input);
    plan.argv = {
        plan.executable,
        "--rootfs",
        rootfs_dir,
        "--program",
        input.program,
    };
    plan.env = {
        {"ALR_PACKAGE", input.package_name},
        {"ALR_ROOTFS", rootfs_dir},
        {"ALR_PROGRAM", input.program},
        {"HOME", "/root"},
        {"TMPDIR", "/tmp"},
        {"PATH", "/bin:/usr/bin:/usr/local/bin"},
    };
    return plan;
}

}  // namespace alr
