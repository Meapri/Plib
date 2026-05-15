#pragma once

#include <string>
#include <string_view>
#include <vector>

#include "alr_runtime/alr_config.hpp"
#include "alr_runtime/alr_path.hpp"

namespace alr::runtime {

enum class ExecutableKind {
    Missing,
    Elf,
    Shebang,
    Unsupported,
};

struct ShebangInfo {
    std::string interpreter;
    std::string argument;
};

struct ExecutableResolution {
    PathTranslation translation;
    ExecutableKind kind = ExecutableKind::Missing;
    bool resolved = false;
    bool classified = false;
    ShebangInfo shebang;
    std::string strategy = "plan-only";
    std::string error;
    std::string report;
};

const char* executable_kind_name(ExecutableKind kind);

// Resolve and classify the first executable path that ALR would hand to a
// future guest loader. This is clean-room planning logic only; it deliberately
// does not exec the guest program.
ExecutableResolution resolve_guest_executable(
    const RuntimeConfig& config,
    std::string_view requested_program);

}  // namespace alr::runtime
