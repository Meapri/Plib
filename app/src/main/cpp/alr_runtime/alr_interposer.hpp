#pragma once

#include <cstddef>
#include <string>
#include <string_view>

#include "alr_runtime/alr_path.hpp"

namespace alr::runtime {

struct InterposerConfig {
    std::string rootfs_dir;
    std::string cwd;
};

struct InterposedPathResult {
    PathTranslation translation;
    bool opened = false;
    bool stated = false;
    int open_errno = 0;
    int stat_errno = 0;
    long long size_bytes = -1;
    std::string first_bytes;
    std::string report;
};

// Clean-room interposer scaffold: resolve a guest path through the ALR rootfs
// mapper, then perform host file operations on the translated path. This is
// deliberately host-testable and does not claim complete LD_PRELOAD coverage.
InterposedPathResult run_interposer_path_smoke(
    const InterposerConfig& config,
    std::string_view path,
    std::size_t max_read_bytes = 128);

}  // namespace alr::runtime
