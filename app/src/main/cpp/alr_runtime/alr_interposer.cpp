#include "alr_runtime/alr_interposer.hpp"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cerrno>
#include <sstream>
#include <string>
#include <vector>

namespace alr::runtime {
namespace {

std::string sanitize_first_bytes(const std::vector<char>& bytes, ssize_t count) {
    std::string out;
    for (ssize_t i = 0; i < count; ++i) {
        const unsigned char c = static_cast<unsigned char>(bytes[static_cast<std::size_t>(i)]);
        if (c == '\n' || c == '\r' || c == '\t') {
            out.push_back(' ');
        } else if (c >= 0x20 && c < 0x7f) {
            out.push_back(static_cast<char>(c));
        } else {
            out.push_back('.');
        }
    }
    return out;
}

}  // namespace

InterposedPathResult run_interposer_path_smoke(
    const InterposerConfig& config,
    std::string_view path,
    std::size_t max_read_bytes) {
    InterposedPathResult result;
    result.translation = translate_rootfs_path(config.rootfs_dir, config.cwd, path);

    struct stat st {};
    if (::stat(result.translation.host_path.c_str(), &st) == 0) {
        result.stated = true;
        result.size_bytes = static_cast<long long>(st.st_size);
    } else {
        result.stat_errno = errno;
    }

    const int fd = ::open(result.translation.host_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        result.opened = true;
        std::vector<char> buffer(max_read_bytes == 0 ? 1 : max_read_bytes);
        const ssize_t count = ::read(fd, buffer.data(), buffer.size());
        if (count >= 0) {
            result.first_bytes = sanitize_first_bytes(buffer, count);
        }
        ::close(fd);
    } else {
        result.open_errno = errno;
    }

    std::ostringstream report;
    report << "ALR INTERPOSER LOAD: PASS";
    report << "\nALR INTERPOSER MODE: translated-open-stat-smoke";
    report << "\nALR INTERPOSER GUEST PATH: " << result.translation.guest_path;
    report << "\nALR INTERPOSER HOST PATH: " << result.translation.host_path;
    report << "\nALR INTERPOSER STAT PATH: " << (result.stated ? "PASS" : "FAIL");
    report << "\nALR INTERPOSER OPEN PATH: " << (result.opened ? "PASS" : "FAIL");
    report << "\nALR INTERPOSER FILE SIZE: " << result.size_bytes;
    report << "\nALR INTERPOSER FIRST BYTES: " << result.first_bytes;
    if (!result.stated) {
        report << "\nALR INTERPOSER STAT ERRNO: " << result.stat_errno;
    }
    if (!result.opened) {
        report << "\nALR INTERPOSER OPEN ERRNO: " << result.open_errno;
    }
    result.report = report.str();
    return result;
}

}  // namespace alr::runtime
