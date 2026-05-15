#include <cstdlib>
#include <iostream>
#include <string>

#include "../app/src/main/cpp/runtime_plan.hpp"

int main() {
    const auto report = alr::build_runtime_report({
        .package_name = "dev.chanwoo.androlinux",
        .native_library_dir = "/data/app/pkg/lib/arm64",
        .app_files_dir = "/data/user/0/dev.chanwoo.androlinux/files",
        .rootfs_name = "debian-arm64",
        .program = "/bin/bash",
    });

    const std::string text = report.text;
    const bool ok =
        text.find("loader executable: /data/app/pkg/lib/arm64/libalr-loader.so") != std::string::npos &&
        text.find("rootfs dir: /data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64") != std::string::npos &&
        text.find("PoC 1 policy: execute packaged native loader only") != std::string::npos;

    if (!ok) {
        std::cerr << text << "\n";
        return EXIT_FAILURE;
    }
    std::cout << "native core report test ok\n";
    return EXIT_SUCCESS;
}
