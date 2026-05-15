#include "alr_runtime/alr_handoff.hpp"

#include <fcntl.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#if defined(__ANDROID__) && defined(__aarch64__)
#include <asm/ptrace.h>
#include <asm/unistd.h>
#include <linux/elf.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#endif
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cerrno>
#include <cstring>
#include <map>
#include <set>
#include <string>
#include <sstream>
#include <vector>

#ifndef MFD_EXEC
#define MFD_EXEC 0x0004U
#endif

#ifndef AT_EMPTY_PATH
#define AT_EMPTY_PATH 0x1000
#endif

#ifndef AT_FDCWD
#define AT_FDCWD -100
#endif

namespace {

std::string errno_message(const char* action) {
    std::ostringstream out;
    out << action << " failed errno=" << errno << " message=" << std::strerror(errno);
    return out.str();
}

std::string hex_value(std::uint64_t value) {
    std::ostringstream out;
    out << "0x" << std::hex << value;
    return out.str();
}

int monotonic_elapsed_ms(timespec start) {
    timespec now {};
    if (::clock_gettime(CLOCK_MONOTONIC, &now) != 0) {
        return 0;
    }
    const auto sec_ms = static_cast<long long>(now.tv_sec - start.tv_sec) * 1000LL;
    const auto nsec_ms = static_cast<long long>(now.tv_nsec - start.tv_nsec) / 1000000LL;
    const auto elapsed = sec_ms + nsec_ms;
    return elapsed < 0 ? 0 : static_cast<int>(elapsed);
}

std::string one_line_text(const std::string& value) {
    std::string out;
    out.reserve(value.size());
    for (const char ch : value) {
        if (ch == '\n') {
            out += "\\n";
        } else if (ch == '\r') {
            out += "\\r";
        } else {
            out.push_back(ch);
        }
    }
    return out;
}

std::string build_report(const alr::runtime::StaticEntryHandoffResult& result) {
    std::ostringstream out;
    const bool success = result.child_exited && result.exit_code == 0;
    out << "ALR STATIC ENTRY HANDOFF: " << (!result.attempted ? "SKIP" : (success ? "PASS" : "FAIL"));
    out << "\nALR STATIC ENTRY HANDOFF AVAILABLE: " << (result.available ? "PASS" : "SKIP");
    out << "\nalr handoff requested=" << (result.requested ? "true" : "false");
    out << "\nalr handoff attempted=" << (result.attempted ? "true" : "false");
    out << "\nalr handoff preconditions ready=" << (result.preconditions_ready ? "true" : "false");
    out << "\nalr handoff timeout ms=" << result.timeout_ms;
    out << "\nalr handoff elapsed ms=" << result.elapsed_ms;
    out << "\nalr handoff timed out=" << (result.timed_out ? "true" : "false");
    out << "\nalr handoff child exited=" << (result.child_exited ? "true" : "false");
    out << "\nalr handoff child signaled=" << (result.child_signaled ? "true" : "false");
    out << "\nalr handoff exit code=" << result.exit_code;
    out << "\nalr handoff signal=" << result.signal_number;
    out << "\nalr handoff fault captured=" << (result.fault_captured ? "true" : "false");
    out << "\nalr handoff fault signal=" << result.fault_signal;
    out << "\nalr handoff fault addr=" << hex_value(result.fault_address);
    out << "\nalr handoff fault pc=" << hex_value(result.fault_pc);
    out << "\nalr handoff fault syscall=" << result.fault_syscall;
    out << "\nalr handoff syscall emulated count=" << result.syscall_emulated_count;
    out << "\nalr handoff identity syscall virtualized count=" << result.identity_syscall_virtualized_count;
    out << "\nalr handoff execve attempt count=" << result.execve_attempt_count;
    out << "\nalr handoff execve loader rewrite count=" << result.execve_loader_rewrite_count;
    out << "\nalr handoff traced process count=" << result.traced_process_count;
    out << "\nalr handoff path rewrite enabled=" << (result.path_rewrite_enabled ? "true" : "false");
    out << "\nalr handoff path rewrite limit=" << result.path_rewrite_limit;
    out << "\nalr handoff path rewrite idle syscall limit=" << result.path_rewrite_idle_syscall_limit;
    out << "\nalr handoff path rewrite syscall count=" << result.path_rewrite_syscall_count;
    out << "\nalr handoff path rewrite count=" << result.path_rewrite_count;
    out << "\nalr handoff path rewrite cache hit count=" << result.path_rewrite_cache_hit_count;
    out << "\nalr handoff last exec requested path=" << one_line_text(result.last_exec_requested_path);
    out << "\nalr handoff last status path syscall=" << result.last_status_path_syscall;
    out << "\nalr handoff last status path request=" << one_line_text(result.last_status_path_request);
    out << "\nalr handoff last status path guest=" << one_line_text(result.last_status_path_guest);
    out << "\nalr handoff last status path host=" << one_line_text(result.last_status_path_host);
    out << "\nalr handoff last guest path=" << one_line_text(result.last_guest_path);
    out << "\nalr handoff last host path=" << one_line_text(result.last_host_path);
    out << "\nalr handoff stdout=" << one_line_text(result.stdout_text);
    out << "\nalr handoff stderr=" << one_line_text(result.stderr_text);
    if (!result.error.empty()) {
        out << "\nalr handoff error=" << result.error;
    }
    return out.str();
}

#if defined(__aarch64__)
extern "C" __attribute__((naked, noreturn)) void alr_runtime_enter_static_entry(
    std::uintptr_t /*entry_address*/,
    std::uintptr_t /*initial_sp_address*/) {
    __asm__ volatile(
        "mov x16, x0\n"
        "mov x0, xzr\n"
        "mov sp, x1\n"
        "br x16\n");
}
#else
[[noreturn]] void alr_runtime_enter_static_entry(
    std::uintptr_t /*entry_address*/,
    std::uintptr_t /*initial_sp_address*/) {
    _exit(127);
}
#endif

void append_available_pipe_text(int fd, std::string& out) {
    char buffer[4096];
    for (;;) {
        const ssize_t count = ::read(fd, buffer, sizeof(buffer));
        if (count > 0) {
            out.append(buffer, static_cast<std::size_t>(count));
        } else if (count == 0) {
            return;
        } else if (errno != EINTR) {
            return;
        }
    }
}

void set_nonblocking(int fd) {
    const int flags = ::fcntl(fd, F_GETFL, 0);
    if (flags >= 0) {
        ::fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    }
}

void write_literal(int fd, const char* text) {
    (void)::write(fd, text, std::strlen(text));
}

void write_decimal(int fd, int value) {
    char buffer[32];
    char* cursor = buffer + sizeof(buffer);
    *--cursor = '\0';
    unsigned int remaining = value < 0 ? static_cast<unsigned int>(-value) : static_cast<unsigned int>(value);
    do {
        *--cursor = static_cast<char>('0' + (remaining % 10));
        remaining /= 10;
    } while (remaining != 0);
    if (value < 0) {
        *--cursor = '-';
    }
    (void)::write(fd, cursor, std::strlen(cursor));
}

void write_hex(int fd, std::uintptr_t value) {
    char buffer[2 + sizeof(std::uintptr_t) * 2];
    buffer[0] = '0';
    buffer[1] = 'x';
    for (std::size_t i = 0; i < sizeof(std::uintptr_t) * 2; ++i) {
        const unsigned int shift = static_cast<unsigned int>((sizeof(std::uintptr_t) * 2 - 1 - i) * 4);
        const unsigned int nibble = static_cast<unsigned int>((value >> shift) & 0xf);
        buffer[2 + i] = static_cast<char>(nibble < 10 ? '0' + nibble : 'a' + (nibble - 10));
    }
    (void)::write(fd, buffer, sizeof(buffer));
}

void alr_child_fault_handler(int signal_number, siginfo_t* info, void* context) {
    write_literal(STDERR_FILENO, "ALR_HANDOFF_FAULT sig=");
    write_decimal(STDERR_FILENO, signal_number);
    write_literal(STDERR_FILENO, " addr=");
    write_hex(STDERR_FILENO, reinterpret_cast<std::uintptr_t>(info != nullptr ? info->si_addr : nullptr));
#if defined(__ANDROID__) && defined(__aarch64__)
    auto* ucontext = static_cast<ucontext_t*>(context);
    write_literal(STDERR_FILENO, " pc=");
    write_hex(STDERR_FILENO, static_cast<std::uintptr_t>(ucontext != nullptr ? ucontext->uc_mcontext.pc : 0));
#else
    (void)context;
#endif
    write_literal(STDERR_FILENO, "\n");
    _exit(128 + signal_number);
}

void install_child_fault_handlers() {
    void* signal_stack = ::mmap(nullptr, 64 * 1024, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (signal_stack != MAP_FAILED) {
        stack_t stack {};
        stack.ss_sp = signal_stack;
        stack.ss_size = 64 * 1024;
        stack.ss_flags = 0;
        (void)::sigaltstack(&stack, nullptr);
    }

    struct sigaction action {};
    action.sa_sigaction = alr_child_fault_handler;
    action.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&action.sa_mask);
    (void)::sigaction(SIGSEGV, &action, nullptr);
    (void)::sigaction(SIGBUS, &action, nullptr);
    (void)::sigaction(SIGILL, &action, nullptr);
    (void)::sigaction(SIGABRT, &action, nullptr);
}

#if defined(__ANDROID__) && defined(__aarch64__)
bool enable_child_ptrace_stop() {
    if (::ptrace(PTRACE_TRACEME, 0, nullptr, nullptr) != 0) {
        return false;
    }
    return ::raise(SIGSTOP) == 0;
}

bool continue_child_ptrace(pid_t child, int signal_number = 0) {
    return ::ptrace(PTRACE_CONT, child, nullptr, reinterpret_cast<void*>(static_cast<std::uintptr_t>(signal_number))) == 0;
}

bool continue_child_syscall(pid_t child, int signal_number = 0) {
    return ::ptrace(PTRACE_SYSCALL, child, nullptr, reinterpret_cast<void*>(static_cast<std::uintptr_t>(signal_number))) == 0;
}

bool get_child_registers(pid_t child, user_pt_regs& regs) {
    iovec iov {};
    iov.iov_base = &regs;
    iov.iov_len = sizeof(regs);
    return ::ptrace(PTRACE_GETREGSET, child, reinterpret_cast<void*>(static_cast<std::uintptr_t>(NT_PRSTATUS)), &iov) == 0;
}

bool set_child_registers(pid_t child, const user_pt_regs& regs) {
    iovec iov {};
    iov.iov_base = const_cast<user_pt_regs*>(&regs);
    iov.iov_len = sizeof(regs);
    return ::ptrace(PTRACE_SETREGSET, child, reinterpret_cast<void*>(static_cast<std::uintptr_t>(NT_PRSTATUS)), &iov) == 0;
}

bool set_child_ptrace_options(pid_t child, long options) {
    return ::ptrace(PTRACE_SETOPTIONS, child, nullptr, reinterpret_cast<void*>(static_cast<std::uintptr_t>(options))) == 0;
}

bool read_child_bytes(pid_t child, std::uintptr_t address, void* out, std::size_t size) {
    iovec local {};
    local.iov_base = out;
    local.iov_len = size;
    iovec remote {};
    remote.iov_base = reinterpret_cast<void*>(address);
    remote.iov_len = size;
    return ::process_vm_readv(child, &local, 1, &remote, 1, 0) == static_cast<ssize_t>(size);
}

bool read_child_u64(pid_t child, std::uintptr_t address, std::uint64_t& out) {
    return read_child_bytes(child, address, &out, sizeof(out));
}

std::string read_child_cstring(pid_t child, std::uintptr_t address, std::size_t max_size = 4096) {
    std::string value;
    value.reserve(128);
    std::vector<char> buffer(256);
    while (value.size() < max_size) {
        const std::size_t remaining = max_size - value.size();
        const std::size_t chunk_size = remaining < buffer.size() ? remaining : buffer.size();
        iovec local {};
        local.iov_base = buffer.data();
        local.iov_len = chunk_size;
        iovec remote {};
        remote.iov_base = reinterpret_cast<void*>(address + value.size());
        remote.iov_len = chunk_size;
        const ssize_t count = ::process_vm_readv(child, &local, 1, &remote, 1, 0);
        if (count <= 0) {
            return "";
        }
        for (ssize_t index = 0; index < count; ++index) {
            if (buffer[static_cast<std::size_t>(index)] == '\0') {
                return value;
            }
            value.push_back(buffer[static_cast<std::size_t>(index)]);
        }
    }
    return "";
}

bool write_child_data(pid_t child, std::uintptr_t address, const void* data, std::size_t size) {
    iovec local {};
    local.iov_base = const_cast<void*>(data);
    local.iov_len = size;
    iovec remote {};
    remote.iov_base = reinterpret_cast<void*>(address);
    remote.iov_len = size;
    return ::process_vm_writev(child, &local, 1, &remote, 1, 0) == static_cast<ssize_t>(size);
}

bool write_child_bytes(pid_t child, std::uintptr_t address, const std::string& value) {
    return write_child_data(child, address, value.data(), value.size());
}

std::string join_rootfs_path(const std::string& rootfs_path, const std::string& guest_path) {
    if (rootfs_path.empty() || guest_path.empty() || guest_path.front() != '/') {
        return "";
    }
    return rootfs_path + guest_path;
}

std::string join_relative_path(const std::string& base, const std::string& relative) {
    if (base.empty() || relative.empty()) {
        return "";
    }
    return base.back() == '/' ? base + relative : base + "/" + relative;
}

bool path_exists(const std::string& path) {
    struct stat st {};
    return !path.empty() && ::stat(path.c_str(), &st) == 0;
}

bool write_all_fd(int fd, const char* data, std::size_t size);

bool copy_file_contents_and_mode(const std::string& from, const std::string& to) {
    const int source = ::open(from.c_str(), O_RDONLY | O_CLOEXEC);
    if (source < 0) {
        return false;
    }
    struct stat source_stat {};
    if (::fstat(source, &source_stat) != 0) {
        ::close(source);
        return false;
    }
    const int target = ::open(to.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, source_stat.st_mode & 0777);
    if (target < 0) {
        ::close(source);
        return false;
    }
    std::array<char, 16384> buffer {};
    bool ok = true;
    for (;;) {
        const ssize_t count = ::read(source, buffer.data(), buffer.size());
        if (count < 0 && errno == EINTR) {
            continue;
        }
        if (count < 0) {
            ok = false;
            break;
        }
        if (count == 0) {
            break;
        }
        if (!write_all_fd(target, buffer.data(), static_cast<std::size_t>(count))) {
            ok = false;
            break;
        }
    }
    if (::fchmod(target, source_stat.st_mode & 0777) != 0) {
        ok = false;
    }
    ::close(target);
    ::close(source);
    return ok;
}

bool write_all_fd(int fd, const char* data, std::size_t size) {
    std::size_t written = 0;
    while (written < size) {
        const ssize_t count = ::write(fd, data + written, size - written);
        if (count < 0 && errno == EINTR) {
            continue;
        }
        if (count <= 0) {
            return false;
        }
        written += static_cast<std::size_t>(count);
    }
    return true;
}

int create_loader_exec_memfd(const std::string& loader_host_path) {
#if defined(__NR_memfd_create)
    int fd = static_cast<int>(::syscall(__NR_memfd_create, "alr-ld-linux-aarch64", MFD_EXEC));
    if (fd < 0 && errno == EINVAL) {
        fd = static_cast<int>(::syscall(__NR_memfd_create, "alr-ld-linux-aarch64", 0));
    }
    if (fd < 0) {
        return -1;
    }
    const int source = ::open(loader_host_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (source < 0) {
        ::close(fd);
        return -1;
    }
    std::array<char, 16384> buffer {};
    bool ok = true;
    for (;;) {
        const ssize_t count = ::read(source, buffer.data(), buffer.size());
        if (count < 0 && errno == EINTR) {
            continue;
        }
        if (count < 0) {
            ok = false;
            break;
        }
        if (count == 0) {
            break;
        }
        if (!write_all_fd(fd, buffer.data(), static_cast<std::size_t>(count))) {
            ok = false;
            break;
        }
    }
    ::close(source);
    if (!ok || ::lseek(fd, 0, SEEK_SET) < 0) {
        ::close(fd);
        return -1;
    }
    (void)::fchmod(fd, 0700);
    return fd;
#else
    (void)loader_host_path;
    return -1;
#endif
}

bool parent_path_exists(const std::string& path) {
    if (path.empty()) {
        return false;
    }
    const auto slash = path.find_last_of('/');
    if (slash == std::string::npos) {
        return false;
    }
    const std::string parent = slash == 0 ? "/" : path.substr(0, slash);
    struct stat st {};
    return ::stat(parent.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

bool guest_path_rewritable(const std::string& guest_path) {
    return guest_path.size() >= 2 &&
        guest_path.front() == '/' &&
        guest_path.rfind("/data/", 0) != 0 &&
        guest_path.rfind("/proc/", 0) != 0 &&
        guest_path.rfind("/sys/", 0) != 0;
}

std::string read_process_link(pid_t pid, const std::string& link_path) {
    std::array<char, 4096> buffer {};
    const std::string proc_path = "/proc/" + std::to_string(pid) + "/" + link_path;
    const ssize_t count = ::readlink(proc_path.c_str(), buffer.data(), buffer.size() - 1);
    if (count <= 0) {
        return "";
    }
    buffer[static_cast<std::size_t>(count)] = '\0';
    return std::string(buffer.data(), static_cast<std::size_t>(count));
}

bool host_path_to_guest_path(
    const std::string& rootfs_path,
    const std::string& host_path,
    std::string& guest_path) {
    const std::string rootfs_prefix = rootfs_path + "/";
    if (host_path == rootfs_path) {
        guest_path = "/";
        return true;
    }
    if (!rootfs_path.empty() && host_path.rfind(rootfs_prefix, 0) == 0) {
        guest_path = "/" + host_path.substr(rootfs_prefix.size());
        return guest_path_rewritable(guest_path);
    }
    return false;
}

int dirfd_arg_index_for_syscall_path_arg(std::uint64_t syscall_number, int path_arg_index) {
    switch (syscall_number) {
#if defined(__NR_openat)
        case __NR_openat:
#endif
#if defined(__NR_faccessat)
        case __NR_faccessat:
#endif
#if defined(__NR_newfstatat)
        case __NR_newfstatat:
#endif
#if defined(__NR_readlinkat)
        case __NR_readlinkat:
#endif
#if defined(__NR_mkdirat)
        case __NR_mkdirat:
#endif
#if defined(__NR_unlinkat)
        case __NR_unlinkat:
#endif
#if defined(__NR_statx)
        case __NR_statx:
#endif
#if defined(__NR_openat2)
        case __NR_openat2:
#endif
#if defined(__NR_faccessat2)
        case __NR_faccessat2:
#endif
#if defined(__NR_utimensat)
        case __NR_utimensat:
#endif
#if defined(__NR_utimensat_time64)
        case __NR_utimensat_time64:
#endif
#if defined(__NR_fchmodat)
        case __NR_fchmodat:
#endif
#if defined(__NR_fchmodat2)
        case __NR_fchmodat2:
#endif
#if defined(__NR_mknodat)
        case __NR_mknodat:
#endif
            return path_arg_index == 1 ? 0 : -1;
#if defined(__NR_renameat)
        case __NR_renameat:
            return path_arg_index == 1 ? 0 : (path_arg_index == 3 ? 2 : -1);
#endif
#if defined(__NR_renameat2)
        case __NR_renameat2:
            return path_arg_index == 1 ? 0 : (path_arg_index == 3 ? 2 : -1);
#endif
#if defined(__NR_linkat)
        case __NR_linkat:
            return path_arg_index == 1 ? 0 : (path_arg_index == 3 ? 2 : -1);
#endif
#if defined(__NR_symlinkat)
        case __NR_symlinkat:
            return path_arg_index == 2 ? 1 : -1;
#endif
        default:
            return -1;
    }
}

bool resolve_guest_syscall_path(
    pid_t child,
    const std::string& rootfs_path,
    const user_pt_regs& regs,
    int path_arg_index,
    const std::string& requested_path,
    std::string& guest_path,
    std::string& host_path) {
    if (guest_path_rewritable(requested_path)) {
        guest_path = requested_path;
        host_path = join_rootfs_path(rootfs_path, guest_path);
        return !host_path.empty();
    }
    if (requested_path.empty() || requested_path.front() == '/') {
        return false;
    }
    const int dirfd_arg_index = dirfd_arg_index_for_syscall_path_arg(regs.regs[8], path_arg_index);
    if (dirfd_arg_index < 0) {
        return false;
    }
    const int dirfd = static_cast<int>(regs.regs[dirfd_arg_index]);
    const std::string base_host_path = dirfd == AT_FDCWD ?
        read_process_link(child, "cwd") :
        read_process_link(child, "fd/" + std::to_string(dirfd));
    std::string base_guest_path;
    if (!host_path_to_guest_path(rootfs_path, base_host_path, base_guest_path)) {
        return false;
    }
    host_path = join_relative_path(base_host_path, requested_path);
    if (!host_path_to_guest_path(rootfs_path, host_path, guest_path)) {
        return false;
    }
    return true;
}

std::uintptr_t align_up(std::uintptr_t value, std::uintptr_t alignment) {
    return (value + alignment - 1) & ~(alignment - 1);
}

std::uintptr_t align_down(std::uintptr_t value, std::uintptr_t alignment) {
    return value & ~(alignment - 1);
}

std::string rootfs_library_path(const std::string& rootfs_path) {
    return rootfs_path + "/lib/aarch64-linux-gnu:" +
        rootfs_path + "/usr/lib/aarch64-linux-gnu:" +
        rootfs_path + "/lib:" +
        rootfs_path + "/usr/lib:" +
        rootfs_path + "/usr/lib/androlinux";
}

std::string rootfs_host_path_env(const std::string& rootfs_path) {
    return rootfs_path + "/usr/local/sbin:" +
        rootfs_path + "/usr/local/bin:" +
        rootfs_path + "/usr/sbin:" +
        rootfs_path + "/usr/bin:" +
        rootfs_path + "/sbin:" +
        rootfs_path + "/bin";
}

bool resolve_guest_exec_path(
    const std::string& rootfs_path,
    const std::string& requested_path,
    std::string& guest_path,
    std::string& host_path) {
    const std::string rootfs_prefix = rootfs_path + "/";
    if (!rootfs_path.empty() && requested_path.rfind(rootfs_prefix, 0) == 0) {
        guest_path = "/" + requested_path.substr(rootfs_prefix.size());
        host_path = requested_path;
        return path_exists(host_path);
    }
    if (guest_path_rewritable(requested_path)) {
        guest_path = requested_path;
        host_path = join_rootfs_path(rootfs_path, guest_path);
        return path_exists(host_path);
    }
    if (requested_path.empty() || requested_path.find('/') != std::string::npos) {
        return false;
    }
    const std::array<const char*, 6> search_dirs {
        "/usr/local/sbin",
        "/usr/local/bin",
        "/usr/sbin",
        "/usr/bin",
        "/sbin",
        "/bin",
    };
    for (const char* dir : search_dirs) {
        const std::string candidate_guest = std::string(dir) + "/" + requested_path;
        const std::string candidate_host = join_rootfs_path(rootfs_path, candidate_guest);
        if (path_exists(candidate_host)) {
            guest_path = candidate_guest;
            host_path = candidate_host;
            return true;
        }
    }
    return false;
}

std::vector<std::string> read_child_string_vector(
    pid_t child,
    std::uintptr_t vector_address,
    std::size_t start_index,
    std::size_t max_items) {
    std::vector<std::string> values;
    if (vector_address == 0) {
        return values;
    }
    for (std::size_t index = start_index; index < max_items; ++index) {
        std::uint64_t item_ptr = 0;
        if (!read_child_u64(child, vector_address + index * sizeof(std::uint64_t), item_ptr) || item_ptr == 0) {
            break;
        }
        std::string value = read_child_cstring(child, static_cast<std::uintptr_t>(item_ptr), 8192);
        if (value.empty()) {
            break;
        }
        values.push_back(value);
    }
    return values;
}

std::vector<std::string> read_child_argv_tail(
    pid_t child,
    std::uintptr_t argv_address,
    std::size_t max_args = 48) {
    return read_child_string_vector(child, argv_address, 1, max_args);
}

std::vector<std::string> read_child_env(pid_t child, std::uintptr_t envp_address, std::size_t max_env = 96) {
    return read_child_string_vector(child, envp_address, 0, max_env);
}

std::vector<std::string> build_exec_loader_env(
    pid_t child,
    std::uintptr_t envp_address,
    const std::string& rootfs_path) {
    std::vector<std::string> env = read_child_env(child, envp_address);
    const std::string path_env = "PATH=" + rootfs_host_path_env(rootfs_path);
    bool replaced_path = false;
    for (auto& value : env) {
        if (value.rfind("PATH=", 0) == 0) {
            value = path_env;
            replaced_path = true;
        }
    }
    if (!replaced_path) {
        env.push_back(path_env);
    }
    return env;
}

int path_arg_index_for_syscall(std::uint64_t syscall_number) {
    switch (syscall_number) {
#if defined(__NR_openat)
        case __NR_openat:
            return 1;
#endif
#if defined(__NR_faccessat)
        case __NR_faccessat:
            return 1;
#endif
#if defined(__NR_newfstatat)
        case __NR_newfstatat:
            return 1;
#endif
#if defined(__NR_readlinkat)
        case __NR_readlinkat:
            return 1;
#endif
#if defined(__NR_mkdirat)
        case __NR_mkdirat:
            return 1;
#endif
#if defined(__NR_unlinkat)
        case __NR_unlinkat:
            return 1;
#endif
#if defined(__NR_renameat)
        case __NR_renameat:
            return 1;
#endif
#if defined(__NR_renameat2)
        case __NR_renameat2:
            return 1;
#endif
#if defined(__NR_linkat)
        case __NR_linkat:
            return 1;
#endif
#if defined(__NR_symlinkat)
        case __NR_symlinkat:
            return 2;
#endif
#if defined(__NR_statx)
        case __NR_statx:
            return 1;
#endif
#if defined(__NR_openat2)
        case __NR_openat2:
            return 1;
#endif
#if defined(__NR_faccessat2)
        case __NR_faccessat2:
            return 1;
#endif
#if defined(__NR_utimensat)
        case __NR_utimensat:
            return 1;
#endif
#if defined(__NR_utimensat_time64)
        case __NR_utimensat_time64:
            return 1;
#endif
#if defined(__NR_fchmodat)
        case __NR_fchmodat:
            return 1;
#endif
#if defined(__NR_fchmodat2)
        case __NR_fchmodat2:
            return 1;
#endif
#if defined(__NR_mknodat)
        case __NR_mknodat:
            return 1;
#endif
        default:
            return -1;
    }
}

int second_path_arg_index_for_syscall(std::uint64_t syscall_number) {
    switch (syscall_number) {
#if defined(__NR_renameat)
        case __NR_renameat:
            return 3;
#endif
#if defined(__NR_renameat2)
        case __NR_renameat2:
            return 3;
#endif
#if defined(__NR_linkat)
        case __NR_linkat:
            return 3;
#endif
        default:
            return -1;
    }
}

bool syscall_path_can_target_missing(const user_pt_regs& regs, int path_arg_index) {
    switch (regs.regs[8]) {
#if defined(__NR_openat)
        case __NR_openat:
            return path_arg_index == 1 && (regs.regs[2] & O_CREAT) != 0;
#endif
#if defined(__NR_openat2)
        case __NR_openat2:
            return path_arg_index == 1;
#endif
#if defined(__NR_mkdirat)
        case __NR_mkdirat:
            return path_arg_index == 1;
#endif
#if defined(__NR_mknodat)
        case __NR_mknodat:
            return path_arg_index == 1;
#endif
#if defined(__NR_renameat)
        case __NR_renameat:
            return path_arg_index == 3;
#endif
#if defined(__NR_renameat2)
        case __NR_renameat2:
            return path_arg_index == 3;
#endif
#if defined(__NR_linkat)
        case __NR_linkat:
            return path_arg_index == 3;
#endif
#if defined(__NR_symlinkat)
        case __NR_symlinkat:
            return path_arg_index == 2;
#endif
        default:
            return false;
    }
}

bool syscall_returns_guest_root_identity(std::uint64_t syscall_number) {
    switch (syscall_number) {
#if defined(__NR_getuid)
        case __NR_getuid:
#endif
#if defined(__NR_geteuid)
        case __NR_geteuid:
#endif
#if defined(__NR_getgid)
        case __NR_getgid:
#endif
#if defined(__NR_getegid)
        case __NR_getegid:
#endif
            return true;
        default:
            return false;
    }
}

bool syscall_sets_guest_root_identity(std::uint64_t syscall_number) {
    switch (syscall_number) {
#if defined(__NR_setregid)
        case __NR_setregid:
#endif
#if defined(__NR_setgid)
        case __NR_setgid:
#endif
#if defined(__NR_setreuid)
        case __NR_setreuid:
#endif
#if defined(__NR_setuid)
        case __NR_setuid:
#endif
#if defined(__NR_setresuid)
        case __NR_setresuid:
#endif
#if defined(__NR_setresgid)
        case __NR_setresgid:
#endif
#if defined(__NR_setfsuid)
        case __NR_setfsuid:
#endif
#if defined(__NR_setfsgid)
        case __NR_setfsgid:
#endif
            return true;
        default:
            return false;
    }
}

struct PathRewriteCacheEntry {
    pid_t pid = -1;
    std::uint64_t syscall_number = 0;
    int path_arg_index = -1;
    std::uintptr_t guest_path_address = 0;
    std::uintptr_t scratch_address = 0;
};

using PathRewriteCache = std::array<PathRewriteCacheEntry, 32>;

PathRewriteCacheEntry* find_path_rewrite_cache_entry(
    PathRewriteCache& cache,
    pid_t pid,
    std::uint64_t syscall_number,
    int path_arg_index,
    std::uintptr_t guest_path_address) {
    for (auto& entry : cache) {
        if (entry.pid == pid &&
            entry.syscall_number == syscall_number &&
            entry.path_arg_index == path_arg_index &&
            entry.guest_path_address == guest_path_address &&
            entry.scratch_address != 0) {
            return &entry;
        }
    }
    return nullptr;
}

void remember_path_rewrite_cache_entry(
    PathRewriteCache& cache,
    std::uint32_t rewrite_count,
    pid_t pid,
    std::uint64_t syscall_number,
    int path_arg_index,
    std::uintptr_t guest_path_address,
    std::uintptr_t scratch_address) {
    auto& entry = cache[rewrite_count % cache.size()];
    entry.pid = pid;
    entry.syscall_number = syscall_number;
    entry.path_arg_index = path_arg_index;
    entry.guest_path_address = guest_path_address;
    entry.scratch_address = scratch_address;
}

bool maybe_emulate_linkat_with_copy(
    pid_t child,
    const alr::runtime::StaticEntryHandoffOptions& options,
    user_pt_regs& regs,
    alr::runtime::StaticEntryHandoffResult& result) {
#if defined(__NR_linkat)
    if (!result.path_rewrite_enabled || regs.regs[8] != __NR_linkat) {
        return false;
    }
    const std::uintptr_t old_path_address = static_cast<std::uintptr_t>(regs.regs[1]);
    const std::uintptr_t new_path_address = static_cast<std::uintptr_t>(regs.regs[3]);
    if (old_path_address == 0 || new_path_address == 0) {
        return false;
    }
    const std::string old_request = read_child_cstring(child, old_path_address);
    const std::string new_request = read_child_cstring(child, new_path_address);
    std::string old_guest_path;
    std::string old_host_path;
    std::string new_guest_path;
    std::string new_host_path;
    if (!resolve_guest_syscall_path(child, options.rootfs_path, regs, 1, old_request, old_guest_path, old_host_path) ||
        !resolve_guest_syscall_path(child, options.rootfs_path, regs, 3, new_request, new_guest_path, new_host_path) ||
        !path_exists(old_host_path) ||
        path_exists(new_host_path) ||
        !parent_path_exists(new_host_path)) {
        return false;
    }
    if (!copy_file_contents_and_mode(old_host_path, new_host_path)) {
        return false;
    }
    result.last_guest_path = new_guest_path;
    result.last_host_path = new_host_path;
    if (old_request.find("status") != std::string::npos ||
        new_request.find("status") != std::string::npos ||
        old_guest_path.find("status") != std::string::npos ||
        new_guest_path.find("status") != std::string::npos) {
        result.last_status_path_syscall = regs.regs[8];
        result.last_status_path_request = new_request;
        result.last_status_path_guest = new_guest_path;
        result.last_status_path_host = new_host_path;
    }
    regs.regs[8] = static_cast<std::uint64_t>(-1);
    ++result.path_rewrite_count;
    return true;
#else
    (void)child;
    (void)options;
    (void)regs;
    (void)result;
    return false;
#endif
}

bool syscall_sets_guest_file_metadata(std::uint64_t syscall_number) {
    switch (syscall_number) {
#if defined(__NR_fchownat)
        case __NR_fchownat:
#endif
#if defined(__NR_fchown)
        case __NR_fchown:
#endif
#if defined(__NR_chown)
        case __NR_chown:
#endif
#if defined(__NR_lchown)
        case __NR_lchown:
#endif
#if defined(__NR_setxattr)
        case __NR_setxattr:
#endif
#if defined(__NR_lsetxattr)
        case __NR_lsetxattr:
#endif
#if defined(__NR_fsetxattr)
        case __NR_fsetxattr:
#endif
#if defined(__NR_removexattr)
        case __NR_removexattr:
#endif
#if defined(__NR_lremovexattr)
        case __NR_lremovexattr:
#endif
#if defined(__NR_fremovexattr)
        case __NR_fremovexattr:
#endif
            return true;
        default:
            return false;
    }
}

bool virtualize_guest_identity_syscall_exit(
    pid_t child,
    std::uint64_t syscall_number,
    alr::runtime::StaticEntryHandoffResult& result) {
    if (!syscall_returns_guest_root_identity(syscall_number) &&
        !syscall_sets_guest_root_identity(syscall_number)) {
        return false;
    }
    user_pt_regs regs {};
    if (!get_child_registers(child, regs)) {
        return false;
    }
    regs.regs[0] = 0;
    if (!set_child_registers(child, regs)) {
        return false;
    }
    ++result.identity_syscall_virtualized_count;
    return true;
}

bool rewrite_syscall_path_arg(
    pid_t child,
    const alr::runtime::StaticEntryTransferContext& context,
    const alr::runtime::StaticEntryHandoffOptions& options,
    user_pt_regs& regs,
    alr::runtime::StaticEntryHandoffResult& result,
    int path_arg_index,
    PathRewriteCache& cache) {
    if (!result.path_rewrite_enabled || context.stack.mapped_base == 0 || context.stack.mapped_size < 32768) {
        return false;
    }
    if (path_arg_index < 0) {
        return false;
    }
    const std::uintptr_t guest_path_address = static_cast<std::uintptr_t>(regs.regs[path_arg_index]);
    if (guest_path_address == 0) {
        return false;
    }
    if (auto* cached = find_path_rewrite_cache_entry(cache, child, regs.regs[8], path_arg_index, guest_path_address)) {
        regs.regs[path_arg_index] = cached->scratch_address;
        ++result.path_rewrite_count;
        ++result.path_rewrite_cache_hit_count;
        return true;
    }
    const std::string guest_path = read_child_cstring(child, guest_path_address);
    std::string resolved_guest_path;
    std::string host_path;
    if (guest_path.find("status") != std::string::npos) {
        result.last_status_path_syscall = regs.regs[8];
        result.last_status_path_request = guest_path;
        result.last_status_path_guest.clear();
        result.last_status_path_host.clear();
    }
    if (!resolve_guest_syscall_path(
            child,
            options.rootfs_path,
            regs,
            path_arg_index,
            guest_path,
            resolved_guest_path,
            host_path)) {
        return false;
    }
    if (guest_path.find("status") != std::string::npos ||
        resolved_guest_path.find("status") != std::string::npos ||
        host_path.find("status") != std::string::npos) {
        result.last_status_path_syscall = regs.regs[8];
        result.last_status_path_request = guest_path;
        result.last_status_path_guest = resolved_guest_path;
        result.last_status_path_host = host_path;
    }
    const bool target_exists = path_exists(host_path);
    const bool missing_target_allowed =
        !target_exists &&
        syscall_path_can_target_missing(regs, path_arg_index) &&
        parent_path_exists(host_path);
    if ((!target_exists && !missing_target_allowed) || host_path.size() + 1 > 4096) {
        return false;
    }
    const std::uintptr_t scratch_base = context.stack.mapped_base + 4096;
    const std::uintptr_t scratch = scratch_base + (result.path_rewrite_count % 16) * 4096;
    if (scratch + host_path.size() + 1 >= context.stack.mapped_base + context.stack.mapped_size) {
        return false;
    }
    const std::string host_path_with_nul = host_path + '\0';
    if (!write_child_bytes(child, scratch, host_path_with_nul)) {
        return false;
    }
    remember_path_rewrite_cache_entry(
        cache,
        result.path_rewrite_count,
        child,
        regs.regs[8],
        path_arg_index,
        guest_path_address,
        scratch);
    regs.regs[path_arg_index] = scratch;
    result.last_guest_path = resolved_guest_path;
    result.last_host_path = host_path;
    ++result.path_rewrite_count;
    return true;
}

bool maybe_rewrite_syscall_path(
    pid_t child,
    const alr::runtime::StaticEntryTransferContext& context,
    const alr::runtime::StaticEntryHandoffOptions& options,
    user_pt_regs& regs,
    alr::runtime::StaticEntryHandoffResult& result,
    PathRewriteCache& cache) {
    const int first_path_arg_index = path_arg_index_for_syscall(regs.regs[8]);
    const int second_path_arg_index = second_path_arg_index_for_syscall(regs.regs[8]);
    const bool first_rewritten = rewrite_syscall_path_arg(
        child,
        context,
        options,
        regs,
        result,
        first_path_arg_index,
        cache);
    const bool second_rewritten = rewrite_syscall_path_arg(
        child,
        context,
        options,
        regs,
        result,
        second_path_arg_index,
        cache);
    return first_rewritten || second_rewritten;
}

bool maybe_rewrite_execve_to_loader(
    pid_t child,
    const alr::runtime::StaticEntryTransferContext& context,
    const alr::runtime::StaticEntryHandoffOptions& options,
    const std::string& loader_exec_path,
    int loader_exec_fd,
    user_pt_regs& regs,
    alr::runtime::StaticEntryHandoffResult& result) {
#if defined(__NR_execve)
    if (!result.path_rewrite_enabled ||
        context.stack.mapped_base == 0 ||
        context.stack.mapped_size < 128 * 1024) {
        return false;
    }
    const bool is_execve = regs.regs[8] == __NR_execve;
#if defined(__NR_execveat)
    const bool is_execveat = regs.regs[8] == __NR_execveat;
#else
    const bool is_execveat = false;
#endif
    if (!is_execve && !is_execveat) {
        return false;
    }
    const std::uintptr_t guest_path_address = static_cast<std::uintptr_t>(regs.regs[is_execve ? 0 : 1]);
    const std::uintptr_t argv_address = static_cast<std::uintptr_t>(regs.regs[is_execve ? 1 : 2]);
    const std::uint64_t envp_address = regs.regs[is_execve ? 2 : 3];
    if (guest_path_address == 0 || argv_address == 0) {
        return false;
    }
    const std::string requested_path = read_child_cstring(child, guest_path_address);
    if (requested_path.empty()) {
        return false;
    }
    ++result.execve_attempt_count;
    result.last_exec_requested_path = requested_path;
    std::string guest_path;
    std::string host_path;
    if (!resolve_guest_exec_path(options.rootfs_path, requested_path, guest_path, host_path)) {
        return false;
    }
    const std::string loader_host_path = join_rootfs_path(options.rootfs_path, "/lib/ld-linux-aarch64.so.1");
    const std::string exec_loader_path = loader_exec_path.empty() ? loader_host_path : loader_exec_path;
    if (!path_exists(host_path) ||
        !path_exists(loader_host_path) ||
        exec_loader_path.empty() ||
        (loader_exec_fd < 0 && !path_exists(exec_loader_path))) {
        return false;
    }

    std::vector<std::string> argv;
    argv.reserve(8);
    argv.push_back(loader_exec_fd >= 0 ? loader_host_path : exec_loader_path);
    argv.emplace_back("--argv0");
    argv.push_back(guest_path);
    argv.emplace_back("--library-path");
    argv.push_back(rootfs_library_path(options.rootfs_path));
    argv.push_back(host_path);
    const auto original_tail = read_child_argv_tail(child, argv_address);
    for (const auto& arg : original_tail) {
        if ((guest_path == "/usr/bin/tar" || guest_path == "/bin/tar") &&
            arg.rfind("--warning=", 0) == 0) {
            continue;
        }
        if (guest_path_rewritable(arg)) {
            const std::string translated_arg = join_rootfs_path(options.rootfs_path, arg);
            if (path_exists(translated_arg) || parent_path_exists(translated_arg)) {
                argv.push_back(translated_arg);
                continue;
            }
        }
        argv.push_back(arg);
    }
    std::vector<std::string> env = build_exec_loader_env(
        child,
        static_cast<std::uintptr_t>(envp_address),
        options.rootfs_path);

    const std::uintptr_t current_sp = static_cast<std::uintptr_t>(regs.sp);
    if (current_sp < 96 * 1024) {
        return false;
    }
    const std::uintptr_t scratch_end = align_down(current_sp - 4096, 16);
    std::uintptr_t cursor = scratch_end - 64 * 1024;
    std::array<std::uint64_t, 64> child_argv {};
    if (argv.size() + 1 > child_argv.size()) {
        argv.resize(child_argv.size() - 1);
    }
    std::array<std::uint64_t, 128> child_env {};
    if (env.size() + 1 > child_env.size()) {
        env.resize(child_env.size() - 1);
    }
    for (std::size_t index = 0; index < argv.size(); ++index) {
        if (argv[index].size() + 1 > 8192 || cursor + argv[index].size() + 1 >= scratch_end) {
            return false;
        }
        child_argv[index] = cursor;
        if (!write_child_bytes(child, cursor, argv[index] + '\0')) {
            return false;
        }
        cursor = align_up(cursor + argv[index].size() + 1, 8);
    }
    for (std::size_t index = 0; index < env.size(); ++index) {
        if (env[index].size() + 1 > 8192 || cursor + env[index].size() + 1 >= scratch_end) {
            return false;
        }
        child_env[index] = cursor;
        if (!write_child_bytes(child, cursor, env[index] + '\0')) {
            return false;
        }
        cursor = align_up(cursor + env[index].size() + 1, 8);
    }
    const std::uintptr_t argv_vector_address = cursor;
    const std::size_t argv_vector_size = (argv.size() + 1) * sizeof(std::uint64_t);
    if (argv_vector_address + argv_vector_size >= scratch_end) {
        return false;
    }
    if (!write_child_data(child, argv_vector_address, child_argv.data(), argv_vector_size)) {
        return false;
    }
    const std::uintptr_t env_vector_address = align_up(argv_vector_address + argv_vector_size, 8);
    const std::size_t env_vector_size = (env.size() + 1) * sizeof(std::uint64_t);
    if (env_vector_address + env_vector_size >= scratch_end) {
        return false;
    }
    if (!write_child_data(child, env_vector_address, child_env.data(), env_vector_size)) {
        return false;
    }

#if defined(__NR_execveat)
    if (loader_exec_fd >= 0) {
        const std::uintptr_t empty_path_address = env_vector_address + env_vector_size;
        if (empty_path_address + 1 >= scratch_end || !write_child_bytes(child, empty_path_address, std::string("\0", 1))) {
            return false;
        }
        regs.regs[8] = __NR_execveat;
        regs.regs[0] = static_cast<std::uint64_t>(loader_exec_fd);
        regs.regs[1] = empty_path_address;
        regs.regs[2] = argv_vector_address;
        regs.regs[3] = env_vector_address;
        regs.regs[4] = AT_EMPTY_PATH;
    } else {
        regs.regs[8] = __NR_execve;
        regs.regs[0] = child_argv[0];
        regs.regs[1] = argv_vector_address;
        regs.regs[2] = env_vector_address;
    }
#else
    regs.regs[8] = __NR_execve;
    regs.regs[0] = child_argv[0];
    regs.regs[1] = argv_vector_address;
    regs.regs[2] = env_vector_address;
#endif
    result.last_guest_path = guest_path;
    result.last_host_path = host_path;
    ++result.execve_loader_rewrite_count;
    return true;
#else
    (void)child;
    (void)context;
    (void)options;
    (void)loader_exec_path;
    (void)loader_exec_fd;
    (void)regs;
    (void)result;
    return false;
#endif
}

void capture_ptrace_fault(pid_t child, int signal_number, alr::runtime::StaticEntryHandoffResult& result) {
    result.fault_captured = true;
    result.fault_signal = signal_number;

    siginfo_t info {};
    if (::ptrace(PTRACE_GETSIGINFO, child, nullptr, &info) == 0) {
        result.fault_address = reinterpret_cast<std::uintptr_t>(info.si_addr);
    }

    user_pt_regs regs {};
    if (get_child_registers(child, regs)) {
        result.fault_pc = regs.pc;
        result.fault_syscall = regs.regs[8];
    }
}

bool emulate_android_seccomp_syscall(pid_t child, alr::runtime::StaticEntryHandoffResult& result) {
    user_pt_regs regs {};
    if (!get_child_registers(child, regs)) {
        return false;
    }
    const std::uint64_t syscall_number = regs.regs[8];
    const bool known_optional_linux_syscall =
        syscall_number == 99 ||   // set_robust_list
        syscall_number == 293 ||  // rseq
        syscall_number == 435;    // clone3
    const bool identity_syscall =
        syscall_number == 143 ||  // setregid
        syscall_number == 144 ||  // setgid
        syscall_number == 145 ||  // setreuid
        syscall_number == 146 ||  // setuid
        syscall_number == 147 ||  // setresuid
        syscall_number == 149 ||  // setresgid
        syscall_number == 151 ||  // setfsuid
        syscall_number == 152;    // setfsgid
    if (!known_optional_linux_syscall && !identity_syscall) {
        return false;
    }
    regs.regs[0] = identity_syscall ? 0 : static_cast<std::uint64_t>(-ENOSYS);
    if (!set_child_registers(child, regs)) {
        return false;
    }
    result.fault_captured = true;
    result.fault_signal = SIGSYS;
    result.fault_pc = regs.pc;
    result.fault_syscall = syscall_number;
    ++result.syscall_emulated_count;
    return true;
}
#else
bool enable_child_ptrace_stop() {
    return false;
}

bool continue_child_ptrace(pid_t /*child*/, int /*signal_number*/ = 0) {
    errno = ENOSYS;
    return false;
}

bool continue_child_syscall(pid_t /*child*/, int /*signal_number*/ = 0) {
    errno = ENOSYS;
    return false;
}

void capture_ptrace_fault(pid_t /*child*/, int signal_number, alr::runtime::StaticEntryHandoffResult& result) {
    result.fault_captured = true;
    result.fault_signal = signal_number;
}

bool emulate_android_seccomp_syscall(pid_t /*child*/, alr::runtime::StaticEntryHandoffResult& /*result*/) {
    return false;
}

#endif

}  // namespace

namespace alr::runtime {

StaticEntryHandoffResult maybe_run_static_entry_handoff(
    const StaticEntryTransferContext& context,
    bool execute_requested,
    int timeout_ms,
    const StaticEntryHandoffOptions& options) {
    StaticEntryHandoffResult result;
    result.requested = execute_requested;
    result.timeout_ms = timeout_ms > 0 ? timeout_ms : 1000;
    result.path_rewrite_enabled = options.path_rewrite_enabled && !options.rootfs_path.empty();
    result.path_rewrite_limit = options.path_rewrite_limit;
    result.path_rewrite_idle_syscall_limit = options.path_rewrite_idle_syscall_limit;
#if defined(__aarch64__)
    result.available = true;
#endif
    result.preconditions_ready =
        context.prepared &&
        context.jump_ready &&
        context.entry_address != 0 &&
        context.initial_sp_address != 0;

    if (!execute_requested) {
        result.report = build_report(result);
        return result;
    }
    if (!result.available) {
        result.error = "static entry handoff is only implemented for AArch64";
        result.report = build_report(result);
        return result;
    }
    if (!result.preconditions_ready) {
        result.error = "static entry transfer context is not jump-ready";
        result.report = build_report(result);
        return result;
    }

    int stdout_pipe[2] = {-1, -1};
    int stderr_pipe[2] = {-1, -1};
    if (::pipe(stdout_pipe) != 0) {
        result.error = errno_message("pipe static entry handoff stdout");
        result.report = build_report(result);
        return result;
    }
    if (::pipe(stderr_pipe) != 0) {
        result.error = errno_message("pipe static entry handoff stderr");
        ::close(stdout_pipe[0]);
        ::close(stdout_pipe[1]);
        result.report = build_report(result);
        return result;
    }

    timespec handoff_started {};
    (void)::clock_gettime(CLOCK_MONOTONIC, &handoff_started);

    int loader_exec_fd = -1;
    std::string loader_exec_path = options.exec_loader_path;
#if defined(__ANDROID__) && defined(__aarch64__)
    if (result.path_rewrite_enabled && loader_exec_path.empty()) {
        const std::string loader_host_path = join_rootfs_path(options.rootfs_path, "/lib/ld-linux-aarch64.so.1");
        loader_exec_fd = create_loader_exec_memfd(loader_host_path);
        if (loader_exec_fd >= 0) {
            loader_exec_path = "/proc/self/fd/" + std::to_string(loader_exec_fd);
        }
    }
#endif

    const pid_t child = ::fork();
    if (child < 0) {
        result.error = errno_message("fork static entry handoff");
        if (loader_exec_fd >= 0) {
            ::close(loader_exec_fd);
        }
        ::close(stdout_pipe[0]);
        ::close(stdout_pipe[1]);
        ::close(stderr_pipe[0]);
        ::close(stderr_pipe[1]);
        result.report = build_report(result);
        return result;
    }
    result.attempted = true;
    if (child == 0) {
        ::close(stdout_pipe[0]);
        ::close(stderr_pipe[0]);
        ::dup2(stdout_pipe[1], STDOUT_FILENO);
        ::dup2(stderr_pipe[1], STDERR_FILENO);
        ::close(stdout_pipe[1]);
        ::close(stderr_pipe[1]);
        install_child_fault_handlers();
        (void)enable_child_ptrace_stop();
        alr_runtime_enter_static_entry(context.entry_address, context.initial_sp_address);
    }
    ::close(stdout_pipe[1]);
    ::close(stderr_pipe[1]);
    set_nonblocking(stdout_pipe[0]);
    set_nonblocking(stderr_pipe[0]);

    int status = 0;
    pid_t waited = -1;
    int waited_ms = 0;
    bool ptrace_entry_stop_seen = false;
    bool ptrace_fault_stop_seen = false;
#if defined(__ANDROID__) && defined(__aarch64__)
    struct TraceState {
        bool entering_syscall = true;
        bool stop_tracing_after_syscall_exit = false;
        bool force_success_on_syscall_exit = false;
        std::uint32_t syscalls_since_last_rewrite = 0;
        std::uint64_t pending_syscall_number = 0;
        PathRewriteCache path_rewrite_cache {};
    };
    std::map<pid_t, TraceState> trace_states;
    std::set<pid_t> live_traced_processes;
#endif
    bool trace_syscalls = result.path_rewrite_enabled || options.virtual_root_identity;
#if defined(__ANDROID__) && defined(__aarch64__)
    if (trace_syscalls) {
        trace_states.emplace(child, TraceState{});
        live_traced_processes.insert(child);
        result.traced_process_count = 1;
    }
    const long ptrace_options =
        PTRACE_O_TRACESYSGOOD |
        PTRACE_O_TRACEFORK |
        PTRACE_O_TRACEVFORK |
        PTRACE_O_TRACECLONE |
        PTRACE_O_TRACEEXEC;
    auto kill_live_traced_processes = [&]() {
        if (!trace_syscalls) {
            (void)::kill(child, SIGKILL);
            return;
        }
        for (const pid_t pid : live_traced_processes) {
            (void)::kill(pid, SIGKILL);
        }
    };
#else
    auto kill_live_traced_processes = [&]() {
        (void)::kill(child, SIGKILL);
    };
#endif
    do {
        int wait_options = WNOHANG;
#if defined(__ANDROID__) && defined(__aarch64__) && defined(__WALL)
        if (trace_syscalls) {
            wait_options |= __WALL;
        }
#endif
        waited = ::waitpid(trace_syscalls ? -1 : child, &status, wait_options);
        if (waited == 0) {
            append_available_pipe_text(stdout_pipe[0], result.stdout_text);
            append_available_pipe_text(stderr_pipe[0], result.stderr_text);
            if (waited_ms >= result.timeout_ms) {
                result.timed_out = true;
                kill_live_traced_processes();
                do {
                    waited = ::waitpid(child, &status, 0);
                } while (waited < 0 && errno == EINTR);
                break;
            }
            ::usleep(1000);
            waited_ms += 1;
        } else if (waited > 0 && WIFSTOPPED(status)) {
            append_available_pipe_text(stdout_pipe[0], result.stdout_text);
            append_available_pipe_text(stderr_pipe[0], result.stderr_text);
            const int stop_signal = WSTOPSIG(status);
            if (waited == child && stop_signal == SIGSTOP && !ptrace_entry_stop_seen) {
                ptrace_entry_stop_seen = true;
#if defined(__ANDROID__) && defined(__aarch64__)
                if (trace_syscalls &&
                    !set_child_ptrace_options(child, ptrace_options) &&
                    result.error.empty()) {
                    result.error = errno_message("ptrace setoptions static entry handoff");
                    kill_live_traced_processes();
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
#endif
                const bool continued = trace_syscalls ?
                    continue_child_syscall(child) :
                    continue_child_ptrace(child);
                if (!continued && result.error.empty()) {
                    result.error = errno_message("ptrace continue static entry handoff");
                    kill_live_traced_processes();
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
                waited = 0;
#if defined(__ANDROID__) && defined(__aarch64__)
            } else if (trace_syscalls && stop_signal == SIGSTOP) {
                trace_states.try_emplace(waited, TraceState{});
                if (live_traced_processes.insert(waited).second) {
                    ++result.traced_process_count;
                }
                (void)set_child_ptrace_options(waited, ptrace_options);
                if (!continue_child_syscall(waited) && result.error.empty()) {
                    result.error = errno_message("ptrace continue traced child stop");
                    kill_live_traced_processes();
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
                waited = 0;
            } else if (trace_syscalls && stop_signal == SIGTRAP && (status >> 16) != 0) {
                const int ptrace_event = status >> 16;
                if (ptrace_event == PTRACE_EVENT_FORK ||
                    ptrace_event == PTRACE_EVENT_VFORK ||
                    ptrace_event == PTRACE_EVENT_CLONE) {
                    unsigned long event_message = 0;
                    if (::ptrace(PTRACE_GETEVENTMSG, waited, nullptr, &event_message) == 0 &&
                        event_message != 0) {
                        const pid_t new_pid = static_cast<pid_t>(event_message);
                        trace_states.try_emplace(new_pid, TraceState{});
                        if (live_traced_processes.insert(new_pid).second) {
                            ++result.traced_process_count;
                        }
                        (void)set_child_ptrace_options(new_pid, ptrace_options);
                        (void)continue_child_syscall(new_pid);
                    }
                } else if (ptrace_event == PTRACE_EVENT_EXEC) {
                    auto& trace_state = trace_states[waited];
                    trace_state.entering_syscall = true;
                    trace_state.pending_syscall_number = 0;
                }
                if (!continue_child_syscall(waited) && result.error.empty()) {
                    result.error = errno_message("ptrace continue trace event");
                    kill_live_traced_processes();
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
                waited = 0;
            } else if (trace_syscalls && stop_signal == (SIGTRAP | 0x80)) {
                auto& trace_state = trace_states[waited];
                const bool was_entering_syscall = trace_state.entering_syscall;
                user_pt_regs opportunistic_regs {};
                if (get_child_registers(waited, opportunistic_regs)) {
                    const std::uint32_t execve_rewrite_count_before = result.execve_loader_rewrite_count;
                    ++result.path_rewrite_syscall_count;
                    if (maybe_rewrite_execve_to_loader(
                            waited,
                            context,
                            options,
                            loader_exec_path,
                            loader_exec_fd,
                            opportunistic_regs,
                            result)) {
                        (void)set_child_registers(waited, opportunistic_regs);
                        trace_state.pending_syscall_number = opportunistic_regs.regs[8];
                        trace_state.entering_syscall = false;
                        trace_state.syscalls_since_last_rewrite = 0;
                        if (result.execve_loader_rewrite_count > execve_rewrite_count_before &&
                            !continue_child_syscall(waited) &&
                            result.error.empty()) {
                            result.error = errno_message("ptrace opportunistic exec continue");
                            kill_live_traced_processes();
                            do {
                                waited = ::waitpid(child, &status, 0);
                            } while (waited < 0 && errno == EINTR);
                            break;
                        }
                        waited = 0;
                        continue;
                    }
                }
                if (was_entering_syscall) {
                    user_pt_regs regs {};
                    const std::uint32_t rewrite_count_before = result.path_rewrite_count;
                    const std::uint32_t execve_rewrite_count_before = result.execve_loader_rewrite_count;
                    trace_state.pending_syscall_number = 0;
                    ++result.path_rewrite_syscall_count;
                    if (get_child_registers(waited, regs)) {
                        trace_state.pending_syscall_number = regs.regs[8];
                        if (syscall_sets_guest_file_metadata(regs.regs[8])) {
                            regs.regs[8] = static_cast<std::uint64_t>(-1);
                            trace_state.force_success_on_syscall_exit = true;
                            (void)set_child_registers(waited, regs);
                        } else if (maybe_emulate_linkat_with_copy(waited, options, regs, result)) {
                            trace_state.force_success_on_syscall_exit = true;
                            (void)set_child_registers(waited, regs);
                        } else if (maybe_rewrite_execve_to_loader(waited, context, options, loader_exec_path, loader_exec_fd, regs, result) ||
                            maybe_rewrite_syscall_path(waited, context, options, regs, result, trace_state.path_rewrite_cache)) {
                            (void)set_child_registers(waited, regs);
                        }
                    }
                    if (result.path_rewrite_count > rewrite_count_before ||
                        result.execve_loader_rewrite_count > execve_rewrite_count_before) {
                        trace_state.syscalls_since_last_rewrite = 0;
                        if (result.path_rewrite_limit > 0 &&
                            result.path_rewrite_count >= result.path_rewrite_limit) {
                            trace_state.stop_tracing_after_syscall_exit = true;
                        }
                    } else if (result.path_rewrite_count > 0) {
                        ++trace_state.syscalls_since_last_rewrite;
                        if (result.path_rewrite_idle_syscall_limit > 0 &&
                            trace_state.syscalls_since_last_rewrite >= result.path_rewrite_idle_syscall_limit) {
                            trace_state.stop_tracing_after_syscall_exit = true;
                        }
                    }
                } else if (trace_state.force_success_on_syscall_exit) {
                    user_pt_regs regs {};
                    if (get_child_registers(waited, regs)) {
                        regs.regs[0] = 0;
                        (void)set_child_registers(waited, regs);
                    }
                    trace_state.force_success_on_syscall_exit = false;
                } else if (options.virtual_root_identity && trace_state.pending_syscall_number != 0) {
                    (void)virtualize_guest_identity_syscall_exit(waited, trace_state.pending_syscall_number, result);
                }
                trace_state.entering_syscall = !trace_state.entering_syscall;
                const bool stop_now =
                    !was_entering_syscall &&
                    trace_state.stop_tracing_after_syscall_exit &&
                    !options.virtual_root_identity;
                if (stop_now && waited == child) {
                    trace_syscalls = false;
                    trace_state.stop_tracing_after_syscall_exit = false;
                }
                const bool continued = stop_now ?
                    continue_child_ptrace(waited) :
                    continue_child_syscall(waited);
                if (!continued && result.error.empty()) {
                    result.error = errno_message("ptrace syscall continue static entry handoff");
                    kill_live_traced_processes();
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
                waited = 0;
            } else if (trace_syscalls && (stop_signal == SIGCHLD || stop_signal == SIGTRAP)) {
                if (!continue_child_syscall(waited, stop_signal == SIGCHLD ? SIGCHLD : 0) &&
                    result.error.empty()) {
                    result.error = errno_message("ptrace continue signal stop");
                    kill_live_traced_processes();
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
                waited = 0;
#endif
            } else if (!trace_syscalls && stop_signal == SIGCHLD) {
                if (!continue_child_ptrace(waited, SIGCHLD) && result.error.empty()) {
                    result.error = errno_message("ptrace continue SIGCHLD stop");
                    kill_live_traced_processes();
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
                waited = 0;
            } else {
                capture_ptrace_fault(waited, stop_signal, result);
                if (stop_signal == SIGSYS &&
                    result.syscall_emulated_count < 64 &&
                    emulate_android_seccomp_syscall(waited, result)) {
                    const bool continued = trace_syscalls ?
                        continue_child_syscall(waited) :
                        continue_child_ptrace(waited);
                    if (!continued && result.error.empty()) {
                        result.error = errno_message("ptrace continue emulated static entry syscall");
                        kill_live_traced_processes();
                        do {
                            waited = ::waitpid(child, &status, 0);
                        } while (waited < 0 && errno == EINTR);
                        break;
                    }
                    waited = 0;
                    continue;
                }
                ptrace_fault_stop_seen = true;
                result.child_signaled = true;
                result.signal_number = stop_signal;
                kill_live_traced_processes();
                do {
                    waited = ::waitpid(child, &status, 0);
                } while (waited < 0 && errno == EINTR);
                break;
            }
        } else if (waited > 0) {
#if defined(__ANDROID__) && defined(__aarch64__)
            live_traced_processes.erase(waited);
            trace_states.erase(waited);
#endif
            if (waited == child) {
                break;
            }
            waited = 0;
        }
    } while (waited == 0 || (waited < 0 && errno == EINTR));
    if (waited < 0) {
        result.error = errno_message("waitpid static entry handoff");
        if (loader_exec_fd >= 0) {
            ::close(loader_exec_fd);
        }
        ::close(stdout_pipe[0]);
        ::close(stderr_pipe[0]);
        result.report = build_report(result);
        return result;
    }
    append_available_pipe_text(stdout_pipe[0], result.stdout_text);
    append_available_pipe_text(stderr_pipe[0], result.stderr_text);
    result.elapsed_ms = monotonic_elapsed_ms(handoff_started);
    ::close(stdout_pipe[0]);
    ::close(stderr_pipe[0]);
    if (loader_exec_fd >= 0) {
        ::close(loader_exec_fd);
    }
    if (ptrace_fault_stop_seen) {
        result.child_exited = false;
    } else if (WIFEXITED(status)) {
        result.child_exited = true;
        result.exit_code = WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        result.child_signaled = true;
        result.signal_number = WTERMSIG(status);
    }
    result.report = build_report(result);
    return result;
}

std::string build_static_entry_handoff_skip_report() {
    return
        "ALR STATIC ENTRY HANDOFF: SKIP\n"
        "ALR STATIC ENTRY HANDOFF AVAILABLE: SKIP\n"
        "alr handoff requested=false\n"
        "alr handoff attempted=false\n"
        "alr handoff preconditions ready=false\n"
        "alr handoff timeout ms=0\n"
        "alr handoff elapsed ms=0\n"
        "alr handoff timed out=false\n"
        "alr handoff child exited=false\n"
        "alr handoff child signaled=false\n"
        "alr handoff exit code=-1\n"
        "alr handoff signal=0\n"
        "alr handoff fault captured=false\n"
        "alr handoff fault signal=0\n"
        "alr handoff fault addr=0x0\n"
        "alr handoff fault pc=0x0\n"
        "alr handoff fault syscall=0\n"
        "alr handoff syscall emulated count=0\n"
        "alr handoff identity syscall virtualized count=0\n"
        "alr handoff execve attempt count=0\n"
        "alr handoff execve loader rewrite count=0\n"
        "alr handoff traced process count=0\n"
        "alr handoff path rewrite enabled=false\n"
        "alr handoff path rewrite limit=0\n"
        "alr handoff path rewrite idle syscall limit=0\n"
        "alr handoff path rewrite syscall count=0\n"
        "alr handoff path rewrite count=0\n"
        "alr handoff last exec requested path=\n"
        "alr handoff last status path syscall=0\n"
        "alr handoff last status path request=\n"
        "alr handoff last status path guest=\n"
        "alr handoff last status path host=\n"
        "alr handoff last guest path=\n"
        "alr handoff last host path=\n"
        "alr handoff stdout=\n"
        "alr handoff stderr=";
}

}  // namespace alr::runtime
