#include "alr_runtime/alr_handoff.hpp"

#include <fcntl.h>
#include <signal.h>
#include <sys/mman.h>
#if defined(__ANDROID__) && defined(__aarch64__)
#include <asm/ptrace.h>
#include <linux/elf.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#endif
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <string>
#include <sstream>

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
    int timeout_ms) {
    StaticEntryHandoffResult result;
    result.requested = execute_requested;
    result.timeout_ms = timeout_ms > 0 ? timeout_ms : 1000;
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

    const pid_t child = ::fork();
    if (child < 0) {
        result.error = errno_message("fork static entry handoff");
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
    do {
        waited = ::waitpid(child, &status, WNOHANG);
        if (waited == 0) {
            append_available_pipe_text(stdout_pipe[0], result.stdout_text);
            append_available_pipe_text(stderr_pipe[0], result.stderr_text);
            if (waited_ms >= result.timeout_ms) {
                result.timed_out = true;
                if (::kill(child, SIGKILL) != 0 && result.error.empty()) {
                    result.error = errno_message("kill timed-out static entry handoff");
                }
                do {
                    waited = ::waitpid(child, &status, 0);
                } while (waited < 0 && errno == EINTR);
                break;
            }
            ::usleep(1000);
            waited_ms += 1;
        } else if (waited == child && WIFSTOPPED(status)) {
            append_available_pipe_text(stdout_pipe[0], result.stdout_text);
            append_available_pipe_text(stderr_pipe[0], result.stderr_text);
            const int stop_signal = WSTOPSIG(status);
            if (stop_signal == SIGSTOP && !ptrace_entry_stop_seen) {
                ptrace_entry_stop_seen = true;
                if (!continue_child_ptrace(child) && result.error.empty()) {
                    result.error = errno_message("ptrace continue static entry handoff");
                    (void)::kill(child, SIGKILL);
                    do {
                        waited = ::waitpid(child, &status, 0);
                    } while (waited < 0 && errno == EINTR);
                    break;
                }
                waited = 0;
            } else {
                capture_ptrace_fault(child, stop_signal, result);
                if (stop_signal == SIGSYS &&
                    result.syscall_emulated_count < 64 &&
                    emulate_android_seccomp_syscall(child, result)) {
                    if (!continue_child_ptrace(child) && result.error.empty()) {
                        result.error = errno_message("ptrace continue emulated static entry syscall");
                        (void)::kill(child, SIGKILL);
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
                (void)::kill(child, SIGKILL);
                do {
                    waited = ::waitpid(child, &status, 0);
                } while (waited < 0 && errno == EINTR);
                break;
            }
        }
    } while (waited == 0 || (waited < 0 && errno == EINTR));
    if (waited < 0) {
        result.error = errno_message("waitpid static entry handoff");
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
        "alr handoff stdout=\n"
        "alr handoff stderr=";
}

}  // namespace alr::runtime
