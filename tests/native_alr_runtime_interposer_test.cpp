#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

#include "../app/src/main/cpp/alr_runtime/alr_interposer.hpp"

namespace {

void require(bool condition, const char* message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

}  // namespace

int main() {
    const auto root = std::filesystem::temp_directory_path() / "alr-interposer-smoke-rootfs";
    std::filesystem::remove_all(root);
    std::filesystem::create_directories(root / "usr" / "bin");
    {
        std::ofstream os(root / "usr" / "bin" / "hello");
        os << "interposer hello\n";
    }

    const auto config = alr::runtime::InterposerConfig{
        .rootfs_dir = root.string(),
        .cwd = "/usr",
    };

    const auto relative = alr::runtime::run_interposer_path_smoke(config, "bin/hello");
    require(relative.translation.guest_path == "/usr/bin/hello", "relative guest path translated");
    require(relative.translation.host_path == (root / "usr" / "bin" / "hello").string(), "relative host path translated");
    require(relative.stated, "relative stat passed");
    require(relative.opened, "relative open passed");
    require(relative.first_bytes.find("interposer hello") != std::string::npos, "relative read captured");
    require(relative.report.find("ALR INTERPOSER LOAD: PASS") != std::string::npos, "interposer load report");
    require(relative.report.find("ALR INTERPOSER STAT PATH: PASS") != std::string::npos, "interposer stat report");
    require(relative.report.find("ALR INTERPOSER OPEN PATH: PASS") != std::string::npos, "interposer open report");

    const auto absolute = alr::runtime::run_interposer_path_smoke(config, "/usr/bin/hello");
    require(absolute.translation.guest_path == "/usr/bin/hello", "absolute guest path translated");
    require(absolute.opened, "absolute open passed");
    require(absolute.stated, "absolute stat passed");

    const auto missing = alr::runtime::run_interposer_path_smoke(config, "/missing");
    require(missing.translation.host_path == (root / "missing").string(), "missing host path translated");
    require(!missing.opened, "missing open failed");
    require(!missing.stated, "missing stat failed");
    require(missing.open_errno != 0, "missing open errno captured");
    require(missing.stat_errno != 0, "missing stat errno captured");
    require(missing.report.find("ALR INTERPOSER OPEN PATH: FAIL") != std::string::npos, "missing open report");
    require(missing.report.find("ALR INTERPOSER STAT PATH: FAIL") != std::string::npos, "missing stat report");

    std::filesystem::remove_all(root);
    std::cout << "alr runtime interposer native test ok\n";
    return EXIT_SUCCESS;
}
