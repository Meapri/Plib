#pragma once

#include <cstdint>
#include <string>

#include "alr_runtime/alr_transfer.hpp"

namespace alr::runtime {

struct StaticEntryHandoffResult {
    bool available = false;
    bool requested = false;
    bool attempted = false;
    bool preconditions_ready = false;
    bool child_exited = false;
    bool child_signaled = false;
    bool timed_out = false;
    int timeout_ms = 0;
    int elapsed_ms = 0;
    int exit_code = -1;
    int signal_number = 0;
    bool fault_captured = false;
    int fault_signal = 0;
    std::uint64_t fault_address = 0;
    std::uint64_t fault_pc = 0;
    std::uint64_t fault_syscall = 0;
    std::uint32_t syscall_emulated_count = 0;
    std::uint32_t identity_syscall_virtualized_count = 0;
    std::uint32_t execve_loader_rewrite_count = 0;
    std::uint32_t traced_process_count = 0;
    bool path_rewrite_enabled = false;
    std::uint32_t path_rewrite_limit = 0;
    std::uint32_t path_rewrite_idle_syscall_limit = 0;
    std::uint32_t path_rewrite_syscall_count = 0;
    std::uint32_t path_rewrite_count = 0;
    std::string last_guest_path;
    std::string last_host_path;
    std::string error;
    std::string stdout_text;
    std::string stderr_text;
    std::string report;
};

struct StaticEntryHandoffOptions {
    bool path_rewrite_enabled = false;
    std::uint32_t path_rewrite_limit = 0;
    std::uint32_t path_rewrite_idle_syscall_limit = 0;
    bool virtual_root_identity = false;
    std::string rootfs_path;
    std::string exec_loader_path;
};

StaticEntryHandoffResult maybe_run_static_entry_handoff(
    const StaticEntryTransferContext& context,
    bool execute_requested,
    int timeout_ms,
    const StaticEntryHandoffOptions& options = {});

std::string build_static_entry_handoff_skip_report();

}  // namespace alr::runtime
