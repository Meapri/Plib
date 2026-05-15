#include "alr_runtime/alr_transfer.hpp"

#include <sstream>

namespace alr::runtime {
namespace {

std::string hex_value(std::uint64_t value) {
    std::ostringstream out;
    out << "0x" << std::hex << value;
    return out.str();
}

std::string build_report(const StaticEntryTransferContext& context) {
    std::ostringstream out;
    out << "ALR STATIC ENTRY TRANSFER CONTEXT: " << (context.prepared ? "PASS" : "FAIL");
    out << "\nALR STATIC ENTRY JUMP READY: " << (context.jump_ready ? "PASS" : "SKIP");
    out << "\nalr transfer image mapped=" << (context.image_mapped ? "true" : "false");
    out << "\nalr transfer stack mapped=" << (context.stack_mapped ? "true" : "false");
    out << "\nalr transfer entry address=" << hex_value(context.entry_address);
    out << "\nalr transfer initial sp address=" << hex_value(context.initial_sp_address);
    out << "\nalr transfer stack pointers rebased=" << (context.stack.pointers_rebased ? "true" : "false");
    out << "\nalr transfer fixed vaddr required=" << (context.fixed_vaddr_required ? "true" : "false");
    out << "\nalr transfer fixed image mapped=" << (context.fixed_image_mapped ? "true" : "false");
    out << "\nalr transfer image load bias=" << hex_value(context.image.load_bias);
    out << "\nalr transfer cleanup done=" << (context.cleanup_done ? "true" : "false");
    out << "\nalr transfer image unmapped=" << (context.image.unmapped ? "true" : "false");
    out << "\nalr transfer stack unmapped=" << (context.stack.unmapped ? "true" : "false");
    if (!context.fixed_image_error.empty()) {
        out << "\nalr transfer fixed image error=" << context.fixed_image_error;
    }
    if (!context.error.empty()) {
        out << "\nalr transfer error=" << context.error;
    }
    return out.str();
}

}  // namespace

StaticEntryTransferContext prepare_static_entry_transfer_context(
    const std::string& host_path,
    const StaticImagePlan& image_plan,
    const EntryStackPlan& entry_plan) {
    StaticEntryTransferContext context;
    if (!image_plan.valid || !image_plan.entry_ready) {
        context.error = image_plan.error.empty() ? "static image plan is not entry-ready" : image_plan.error;
        context.report = build_report(context);
        return context;
    }
    if (!entry_plan.valid) {
        context.error = entry_plan.error.empty() ? "entry stack plan is not valid" : entry_plan.error;
        context.report = build_report(context);
        return context;
    }

    context.fixed_vaddr_required = image_plan.fixed_vaddr_required;
    if (context.fixed_vaddr_required) {
        context.image = map_static_image_fixed_for_transfer(host_path, image_plan);
        context.fixed_image_mapped = context.image.mapped && context.image.fixed_address;
    }
    if (context.fixed_vaddr_required && !context.fixed_image_mapped) {
        context.fixed_image_error = context.image.error;
        context.image = map_static_image_for_transfer(host_path, image_plan);
    } else if (!context.fixed_vaddr_required) {
        context.image = map_static_image_for_transfer(host_path, image_plan);
    }
    context.image_mapped = context.image.mapped;
    if (!context.image_mapped) {
        context.error = context.image.error.empty() ? "static image transfer mapping failed" : context.image.error;
        context.report = build_report(context);
        return context;
    }

    const std::uint64_t image_load_bias = context.fixed_image_mapped ? 0 : context.image.load_bias;
    context.stack = map_entry_stack_for_transfer(entry_plan, image_load_bias);
    context.stack_mapped = context.stack.mapped;
    if (!context.stack_mapped) {
        context.error = context.stack.error.empty() ? "entry stack transfer mapping failed" : context.stack.error;
        cleanup_static_entry_transfer_context(context);
        context.report = build_report(context);
        return context;
    }

    context.entry_address = context.image.entry_address;
    context.initial_sp_address = context.stack.initial_sp_address;
    context.prepared = true;
    context.jump_ready = (!context.fixed_vaddr_required || context.fixed_image_mapped) && context.stack.pointers_rebased;
    context.report = build_report(context);
    return context;
}

void cleanup_static_entry_transfer_context(StaticEntryTransferContext& context) {
    unmap_entry_stack_runtime_mapping(context.stack);
    unmap_static_image_runtime_mapping(context.image);
    context.cleanup_done =
        (!context.stack_mapped || context.stack.unmapped) &&
        (!context.image_mapped || context.image.unmapped);
    context.report = build_report(context);
}

std::string build_static_entry_transfer_skip_report() {
    return
        "ALR STATIC ENTRY TRANSFER CONTEXT: SKIP\n"
        "ALR STATIC ENTRY JUMP READY: SKIP\n"
        "alr transfer image mapped=false\n"
        "alr transfer stack mapped=false\n"
        "alr transfer entry address=0x0\n"
        "alr transfer initial sp address=0x0\n"
        "alr transfer stack pointers rebased=false\n"
        "alr transfer fixed vaddr required=true\n"
        "alr transfer fixed image mapped=false\n"
        "alr transfer image load bias=0x0\n"
        "alr transfer cleanup done=false\n"
        "alr transfer image unmapped=false\n"
        "alr transfer stack unmapped=false";
}

}  // namespace alr::runtime
