#pragma once

#include <cstdint>
#include <string>

#include "alr_runtime/alr_entry.hpp"
#include "alr_runtime/alr_image.hpp"

namespace alr::runtime {

struct StaticEntryTransferContext {
    bool prepared = false;
    bool image_mapped = false;
    bool stack_mapped = false;
    bool cleanup_done = false;
    bool jump_ready = false;
    bool fixed_vaddr_required = true;
    std::string error;
    std::uintptr_t entry_address = 0;
    std::uintptr_t initial_sp_address = 0;
    StaticImageRuntimeMapping image;
    EntryStackRuntimeMapping stack;
    std::string report;
};

StaticEntryTransferContext prepare_static_entry_transfer_context(
    const std::string& host_path,
    const StaticImagePlan& image_plan,
    const EntryStackPlan& entry_plan);

void cleanup_static_entry_transfer_context(StaticEntryTransferContext& context);
std::string build_static_entry_transfer_skip_report();

}  // namespace alr::runtime
