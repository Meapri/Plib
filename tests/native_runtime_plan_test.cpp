#include <cstdlib>
#include <iostream>
#include <string>

#include "../app/src/main/cpp/runtime_plan.hpp"

int main() {
    const auto input = alr::RuntimeReportInput{
        .package_name = "dev.chanwoo.androlinux",
        .native_library_dir = "/data/app/pkg/lib/arm64",
        .app_files_dir = "/data/user/0/dev.chanwoo.androlinux/files",
        .app_cache_dir = "/data/user/0/dev.chanwoo.androlinux/cache",
        .rootfs_name = "debian-arm64",
        .program = "/bin/hello",
    };
    const auto report = alr::build_runtime_report(input);
    const auto launch = alr::build_loader_launch_plan(input);

    const std::string text = report.text;
    const bool report_ok =
        text.find("loader executable: /data/app/pkg/lib/arm64/libalr-loader.so") != std::string::npos &&
        text.find("rootfs dir: /data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64") != std::string::npos &&
        text.find("PoC 1 policy: execute packaged native loader only") != std::string::npos;

    const bool launch_ok =
        launch.executable == "/data/app/pkg/lib/arm64/libalr-loader.so" &&
        launch.argv.size() == 5 &&
        launch.argv[1] == "--rootfs" &&
        launch.argv[2] == "/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64" &&
        launch.argv[3] == "--program" &&
        launch.argv[4] == "/bin/hello" &&
        launch.env.at("ALR_ROOTFS") == "/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64" &&
        launch.env.at("ALR_PROGRAM") == "/bin/hello";

    if (!report_ok || !launch_ok) {
        std::cerr << text << "\n";
        return EXIT_FAILURE;
    }
    std::cout << "native core report and launch plan test ok\n";
    return EXIT_SUCCESS;
}
