#include "alr_runtime/alr_exec.hpp"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

namespace alr::runtime {
namespace {

constexpr std::size_t kProbeBytes = 256;

std::string errno_message(const char* action) {
    std::ostringstream out;
    out << action << " failed errno=" << errno << " message=" << std::strerror(errno);
    return out.str();
}

std::vector<std::string> split_colon_paths(std::string_view value) {
    std::vector<std::string> paths;
    std::size_t start = 0;
    while (start <= value.size()) {
        const std::size_t colon = value.find(':', start);
        const std::size_t end = colon == std::string_view::npos ? value.size() : colon;
        const std::string_view part = value.substr(start, end - start);
        if (!part.empty() && is_guest_absolute_path(part)) {
            paths.emplace_back(part);
        }
        if (colon == std::string_view::npos) {
            break;
        }
        start = colon + 1;
    }
    return paths;
}

std::string join_guest_path(const std::string& dir, std::string_view name) {
    if (dir == "/") {
        return "/" + std::string(name);
    }
    return dir + "/" + std::string(name);
}

bool is_plain_command_name(std::string_view value) {
    return !value.empty() && value.find('/') == std::string_view::npos;
}

std::vector<std::string> candidate_guest_paths(const RuntimeConfig& config, std::string_view requested) {
    if (requested.empty()) {
        throw std::invalid_argument("requested program must not be empty");
    }
    if (is_guest_absolute_path(requested) || requested.find('/') != std::string_view::npos) {
        return {normalize_guest_path(requested, config.cwd)};
    }
    if (!is_plain_command_name(requested)) {
        throw std::invalid_argument("requested program must be an absolute path or PATH command name");
    }
    const auto path_iter = config.env.find("PATH");
    const std::string path_value = path_iter == config.env.end()
        ? "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        : path_iter->second;
    std::vector<std::string> candidates;
    for (const auto& dir : split_colon_paths(path_value)) {
        candidates.push_back(normalize_guest_path(join_guest_path(dir, requested)));
    }
    return candidates;
}

std::vector<unsigned char> read_prefix(const std::string& host_path) {
    const int fd = ::open(host_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        throw std::runtime_error(errno_message("open executable"));
    }
    std::vector<unsigned char> bytes(kProbeBytes);
    const ssize_t count = ::read(fd, bytes.data(), bytes.size());
    const int saved_errno = errno;
    ::close(fd);
    if (count < 0) {
        errno = saved_errno;
        throw std::runtime_error(errno_message("read executable"));
    }
    bytes.resize(static_cast<std::size_t>(count));
    return bytes;
}

ShebangInfo parse_shebang(const std::vector<unsigned char>& bytes) {
    std::string line;
    for (std::size_t i = 2; i < bytes.size(); ++i) {
        const char c = static_cast<char>(bytes[i]);
        if (c == '\n' || c == '\r') {
            break;
        }
        line.push_back(c);
    }
    std::size_t start = 0;
    while (start < line.size() && (line[start] == ' ' || line[start] == '\t')) {
        ++start;
    }
    std::size_t end = line.size();
    while (end > start && (line[end - 1] == ' ' || line[end - 1] == '\t')) {
        --end;
    }
    const std::string trimmed = line.substr(start, end - start);
    const std::size_t split = trimmed.find_first_of(" \t");
    if (split == std::string::npos) {
        return ShebangInfo{.interpreter = trimmed, .argument = ""};
    }
    std::size_t arg_start = split;
    while (arg_start < trimmed.size() && (trimmed[arg_start] == ' ' || trimmed[arg_start] == '\t')) {
        ++arg_start;
    }
    return ShebangInfo{
        .interpreter = trimmed.substr(0, split),
        .argument = trimmed.substr(arg_start),
    };
}

ExecutableKind classify_bytes(const std::vector<unsigned char>& bytes, ShebangInfo& shebang) {
    // ELF magic: 0x7f 'E' 'L' 'F'.
    if (bytes.size() >= 4 && bytes[0] == 0x7f && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F') {
        return ExecutableKind::Elf;
    }
    if (bytes.size() >= 2 && bytes[0] == '#' && bytes[1] == '!') {
        shebang = parse_shebang(bytes);
        return shebang.interpreter.empty() ? ExecutableKind::Unsupported : ExecutableKind::Shebang;
    }
    return ExecutableKind::Unsupported;
}

std::string build_report(const ExecutableResolution& result) {
    std::ostringstream out;
    out << "ALR EXEC RESOLVE: " << (result.resolved ? "PASS" : "FAIL");
    out << "\nALR EXEC CLASSIFY: " << (result.classified ? "PASS" : "FAIL");
    out << "\nALR EXEC STRATEGY: " << result.strategy;
    out << "\nalr exec guest path=" << result.translation.guest_path;
    out << "\nalr exec host path=" << result.translation.host_path;
    out << "\nalr exec kind=" << executable_kind_name(result.kind);
    if (!result.shebang.interpreter.empty()) {
        out << "\nalr exec shebang interpreter=" << result.shebang.interpreter;
    }
    if (!result.shebang.argument.empty()) {
        out << "\nalr exec shebang argument=" << result.shebang.argument;
    }
    if (!result.error.empty()) {
        out << "\nalr exec error=" << result.error;
    }
    return out.str();
}

}  // namespace

const char* executable_kind_name(ExecutableKind kind) {
    switch (kind) {
        case ExecutableKind::Missing:
            return "missing";
        case ExecutableKind::Elf:
            return "elf";
        case ExecutableKind::Shebang:
            return "shebang";
        case ExecutableKind::Unsupported:
            return "unsupported";
    }
    return "unsupported";
}

ExecutableResolution resolve_guest_executable(
    const RuntimeConfig& config,
    std::string_view requested_program) {
    ExecutableResolution result;
    const auto candidates = candidate_guest_paths(config, requested_program);
    for (const auto& guest_path : candidates) {
        result.translation = translate_rootfs_path(config.rootfs_dir, config.cwd, guest_path);
        struct stat st {};
        if (::stat(result.translation.host_path.c_str(), &st) != 0) {
            result.error = errno_message("stat executable");
            continue;
        }
        if (!S_ISREG(st.st_mode)) {
            result.error = "resolved executable is not a regular file";
            result.kind = ExecutableKind::Unsupported;
            result.resolved = true;
            result.classified = true;
            result.report = build_report(result);
            return result;
        }
        result.resolved = true;
        try {
            auto bytes = read_prefix(result.translation.host_path);
            result.kind = classify_bytes(bytes, result.shebang);
            result.classified = true;
            result.error.clear();
        } catch (const std::exception& exc) {
            result.kind = ExecutableKind::Unsupported;
            result.error = exc.what();
        }
        result.report = build_report(result);
        return result;
    }
    if (result.translation.guest_path.empty() && !candidates.empty()) {
        result.translation = translate_rootfs_path(config.rootfs_dir, config.cwd, candidates.front());
    }
    result.kind = ExecutableKind::Missing;
    result.resolved = false;
    result.classified = false;
    result.report = build_report(result);
    return result;
}

}  // namespace alr::runtime
