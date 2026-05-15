#include "alr_runtime/alr_handoff.hpp"

#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <sstream>

namespace {

std::string errno_message(const char* action) {
    std::ostringstream out;
    out << action << " failed errno=" << errno << " message=" << std::strerror(errno);
    return out.str();
}

std::string build_report(const alr::runtime::StaticEntryHandoffResult& result) {
    std::ostringstream out;
    out << "ALR STATIC ENTRY HANDOFF: " << (result.attempted ? "PASS" : "SKIP");
    out << "\nALR STATIC ENTRY HANDOFF AVAILABLE: " << (result.available ? "PASS" : "SKIP");
    out << "\nalr handoff requested=" << (result.requested ? "true" : "false");
    out << "\nalr handoff preconditions ready=" << (result.preconditions_ready ? "true" : "false");
    out << "\nalr handoff child exited=" << (result.child_exited ? "true" : "false");
    out << "\nalr handoff child signaled=" << (result.child_signaled ? "true" : "false");
    out << "\nalr handoff exit code=" << result.exit_code;
    out << "\nalr handoff signal=" << result.signal_number;
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
        "mov sp, x1\n"
        "br x0\n");
}
#else
[[noreturn]] void alr_runtime_enter_static_entry(
    std::uintptr_t /*entry_address*/,
    std::uintptr_t /*initial_sp_address*/) {
    _exit(127);
}
#endif

}  // namespace

namespace alr::runtime {

StaticEntryHandoffResult maybe_run_static_entry_handoff(
    const StaticEntryTransferContext& context,
    bool execute_requested) {
    StaticEntryHandoffResult result;
    result.requested = execute_requested;
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

    const pid_t child = ::fork();
    if (child < 0) {
        result.error = errno_message("fork static entry handoff");
        result.report = build_report(result);
        return result;
    }
    result.attempted = true;
    if (child == 0) {
        alr_runtime_enter_static_entry(context.entry_address, context.initial_sp_address);
    }

    int status = 0;
    pid_t waited = -1;
    do {
        waited = ::waitpid(child, &status, 0);
    } while (waited < 0 && errno == EINTR);
    if (waited < 0) {
        result.error = errno_message("waitpid static entry handoff");
        result.report = build_report(result);
        return result;
    }
    if (WIFEXITED(status)) {
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
        "alr handoff preconditions ready=false\n"
        "alr handoff child exited=false\n"
        "alr handoff child signaled=false\n"
        "alr handoff exit code=-1\n"
        "alr handoff signal=0";
}

}  // namespace alr::runtime
