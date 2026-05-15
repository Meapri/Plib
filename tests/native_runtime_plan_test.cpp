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
        text.find("ALR RUNTIME LAUNCHER AVAILABLE: PASS") != std::string::npos &&
        text.find("ALR RUNTIME CONFIG BUILD: PASS") != std::string::npos &&
        text.find("ALR RUNTIME DIRECT APP-DATA EXEC POLICY: PASS") != std::string::npos &&
        text.find("ALR HOOK LOAD: PASS") != std::string::npos &&
        text.find("ALR HOOK CONFIG BUILD: PASS") != std::string::npos &&
        text.find("ALR INTERPOSER LOAD: PASS") != std::string::npos &&
        text.find("ALR INTERPOSER CONFIG BUILD: PASS") != std::string::npos &&
        text.find("alr runtime launcher path=/data/app/pkg/lib/arm64/libalr_runtime_launcher.so") != std::string::npos &&
        text.find("alr runtime interposer path=/data/app/pkg/lib/arm64/libalr_runtime_interposer.so") != std::string::npos &&
        text.find("alr runtime guest execution=not-claimed") != std::string::npos &&
        text.find("LOW-OVERHEAD BACKEND PROBE FRAMEWORK: PASS") != std::string::npos &&
        text.find("OPTIONAL RUNTIME BACKEND AVAILABLE: SKIP") != std::string::npos &&
        text.find("optional runtime backend source=none") != std::string::npos &&
        text.find("Runtime policy: rootfs files stay in app-private writable storage") != std::string::npos;

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

    const auto alr_runtime = alr::build_alr_runtime_launch_plan(input);
    const bool alr_runtime_ok =
        alr_runtime.executable == "/data/app/pkg/lib/arm64/libalr_runtime_launcher.so" &&
        alr_runtime.argv.size() == 8 &&
        alr_runtime.argv[1] == "--rootfs" &&
        alr_runtime.argv[2] == "/data/user/0/dev.chanwoo.androlinux/files/rootfs/debian-arm64" &&
        alr_runtime.argv[3] == "--cwd" &&
        alr_runtime.argv[4] == "/" &&
        alr_runtime.argv[5] == "--program" &&
        alr_runtime.argv[6] == "/bin/hello" &&
        alr_runtime.argv[7] == "--dry-run" &&
        alr_runtime.env.at("ALR_BACKEND") == "alr-runtime" &&
        alr_runtime.env.at("ALR_HOOK_PATH") == "/data/app/pkg/lib/arm64/libalr_runtime_hook.so" &&
        alr_runtime.env.at("ALR_INTERPOSER_PATH") == "/data/app/pkg/lib/arm64/libalr_runtime_interposer.so" &&
        alr_runtime.env.at("ALR_BRIDGE_PATH") == "/data/app/pkg/lib/arm64/libalr_runtime_bridge.so" &&
        alr_runtime.env.at("ALR_FAKE_ROOT") == "0";

    if (!alr_runtime_ok) {
        std::cerr << "bad alr runtime launch plan\n";
        return EXIT_FAILURE;
    }

    std::cout << "native core report and launch plan test ok\n";
    return EXIT_SUCCESS;
}
