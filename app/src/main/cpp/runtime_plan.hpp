#pragma once

#include <string>

namespace alr {

struct RuntimeReportInput {
    std::string package_name;
    std::string native_library_dir;
    std::string app_files_dir;
    std::string rootfs_name;
    std::string program;
};

struct RuntimeReport {
    std::string loader_executable;
    std::string rootfs_dir;
    std::string text;
};

RuntimeReport build_runtime_report(const RuntimeReportInput& input);

}  // namespace alr
