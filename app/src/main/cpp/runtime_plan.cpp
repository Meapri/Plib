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

std::string proot_loader_for(const RuntimeReportInput& input) {
    return join_path(input.native_library_dir, "libproot-loader.so");
}

std::string proot_tmp_dir_for(const RuntimeReportInput& input) {
    return join_path(input.app_cache_dir, "proot-tmp");
}

void validate_input(const RuntimeReportInput& input) {
    if (input.program.empty() || input.program.front() != '/') {
        throw std::invalid_argument("program must be an absolute path inside the rootfs");
    }
}

}  // namespace

RuntimeReport build_runtime_report(const RuntimeReportInput& input) {
    return build_runtime_report(input, select_execution_backend(ExecutionBackendKind::PlanOnly));
}

RuntimeReport build_runtime_report(const RuntimeReportInput& input, const ExecutionBackend& backend) {
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
    out << "program: " << input.program << "\n";
    out << "execution backend: " << backend.name << "\n";
    out << "can execute: " << (backend.can_execute ? "yes" : "no") << "\n";
    out << "backend reason: " << backend.reason << "\n\n";
    out << "Runtime policy: rootfs files stay in app-private writable storage; execution enters through packaged native backends.";
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
        {"ALR_BACKEND", "loader"},
        {"HOME", "/root"},
        {"TMPDIR", "/tmp"},
        {"PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"},
    };
    return plan;
}

LoaderLaunchPlan build_proot_launch_plan(const RuntimeReportInput& input) {
    validate_input(input);
    LoaderLaunchPlan plan;
    const std::string rootfs_dir = rootfs_dir_for(input);
    plan.executable = join_path(input.native_library_dir, "libalr_proot.so");
    plan.argv = {
        plan.executable,
        "-R",
        rootfs_dir,
        "-w",
        "/",
        input.program,
    };
    plan.env = {
        {"ALR_PACKAGE", input.package_name},
        {"ALR_ROOTFS", rootfs_dir},
        {"ALR_PROGRAM", input.program},
        {"ALR_BACKEND", "proot"},
        {"PROOT_LOADER", proot_loader_for(input)},
        {"PROOT_TMP_DIR", proot_tmp_dir_for(input)},
        {"PROOT_NO_SECCOMP", "1"},
        {"PROOT_VERBOSE", "-1"},
        {"LD_LIBRARY_PATH", input.native_library_dir},
        {"HOME", "/root"},
        {"TMPDIR", "/tmp"},
        {"PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"},
    };
    return plan;
}

ExecutionBackend select_execution_backend(ExecutionBackendKind kind) {
    switch (kind) {
        case ExecutionBackendKind::PlanOnly:
            return {
                .kind = kind,
                .name = "plan-only",
                .can_execute = false,
                .reason = "writable app-data rootfs binaries are not direct exec entrypoints on modern Android; use a packaged native loader/proot/glibc-loader backend",
            };
        case ExecutionBackendKind::AndroidNativeTestCommand:
            return {
                .kind = kind,
                .name = "android-native-test-command",
                .can_execute = true,
                .reason = "executes only packaged/native-library-dir test commands, not rootfs app-data binaries",
            };
        case ExecutionBackendKind::Proot:
            return {
                .kind = kind,
                .name = "proot",
                .can_execute = true,
                .reason = "packaged PRoot backend validated for static arm64 ELF smoke execution inside app-private rootfs",
            };
        case ExecutionBackendKind::GlibcLoader:
            return {
                .kind = kind,
                .name = "glibc-loader",
                .can_execute = false,
                .reason = "planned backend; requires packaged executable glibc loader and path virtualization strategy",
            };
    }
    throw std::invalid_argument("unknown execution backend");
}

}  // namespace alr
