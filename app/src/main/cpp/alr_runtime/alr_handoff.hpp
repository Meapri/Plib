#pragma once

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
    int exit_code = -1;
    int signal_number = 0;
    std::string error;
    std::string report;
};

StaticEntryHandoffResult maybe_run_static_entry_handoff(
    const StaticEntryTransferContext& context,
    bool execute_requested);

std::string build_static_entry_handoff_skip_report();

}  // namespace alr::runtime
