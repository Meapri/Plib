#include "alr_runtime/alr_image.hpp"

#include <algorithm>
#include <limits>
#include <sstream>
#include <string>

#include "alr_runtime/alr_elf_format.hpp"

namespace alr::runtime {
namespace {

bool is_power_of_two(std::uint64_t value) {
    return value != 0 && (value & (value - 1)) == 0;
}

std::uint64_t page_down(std::uint64_t value, std::uint64_t page_size) {
    return value / page_size * page_size;
}

bool page_up(std::uint64_t value, std::uint64_t page_size, std::uint64_t& out) {
    if (value > std::numeric_limits<std::uint64_t>::max() - (page_size - 1)) {
        return false;
    }
    out = page_down(value + page_size - 1, page_size);
    return true;
}

std::string hex_value(std::uint64_t value) {
    std::ostringstream out;
    out << "0x" << std::hex << value;
    return out.str();
}

std::string protection_text(std::uint32_t flags) {
    std::string out;
    out.push_back((flags & PF_R) != 0 ? 'r' : '-');
    out.push_back((flags & PF_W) != 0 ? 'w' : '-');
    out.push_back((flags & PF_X) != 0 ? 'x' : '-');
    return out;
}

bool entry_inside_segment(std::uint64_t entry, const StaticImageSegmentMap& segment) {
    if (segment.mem_size == 0) {
        return false;
    }
    const std::uint64_t end = segment.vaddr + segment.mem_size;
    return end >= segment.vaddr && entry >= segment.vaddr && entry < end;
}

std::string build_report(const StaticImagePlan& plan) {
    std::ostringstream out;
    out << "ALR STATIC IMAGE MAP PLAN: " << (plan.valid ? "PASS" : "FAIL");
    out << "\nALR STATIC IMAGE ENTRY READY: " << (plan.entry_ready ? "PASS" : "SKIP");
    out << "\nalr image page size=" << plan.page_size;
    out << "\nalr image entry=" << hex_value(plan.entry);
    out << "\nalr image min vaddr=" << hex_value(plan.image_min_vaddr);
    out << "\nalr image max vaddr=" << hex_value(plan.image_max_vaddr);
    out << "\nalr image size=" << plan.image_size;
    out << "\nalr image load segments=" << plan.segments.size();
    for (std::size_t i = 0; i < plan.segments.size(); ++i) {
        const auto& segment = plan.segments[i];
        out << "\nalr image segment " << i
            << " offset=" << hex_value(segment.source_offset)
            << " map_offset=" << hex_value(segment.map_offset)
            << " vaddr=" << hex_value(segment.vaddr)
            << " map_vaddr=" << hex_value(segment.map_vaddr)
            << " file_size=" << segment.file_size
            << " mem_size=" << segment.mem_size
            << " file_map_size=" << segment.file_map_size
            << " mem_map_size=" << segment.mem_map_size
            << " prot=" << segment.protection;
    }
    if (!plan.error.empty()) {
        out << "\nalr image error=" << plan.error;
    }
    return out.str();
}

}  // namespace

std::string build_static_image_skip_report() {
    return
        "ALR STATIC IMAGE MAP PLAN: SKIP\n"
        "ALR STATIC IMAGE ENTRY READY: SKIP\n"
        "alr image page size=0\n"
        "alr image entry=0x0\n"
        "alr image min vaddr=0x0\n"
        "alr image max vaddr=0x0\n"
        "alr image size=0\n"
        "alr image load segments=0";
}

StaticImagePlan build_static_image_plan(const ElfLoadPlan& elf_plan, std::uint64_t page_size) {
    StaticImagePlan plan;
    plan.page_size = page_size;
    plan.entry = elf_plan.entry;

    if (!is_power_of_two(page_size)) {
        plan.error = "page size must be a non-zero power of two";
        plan.report = build_report(plan);
        return plan;
    }
    if (!elf_plan.valid || elf_plan.status != ElfLoadPlanStatus::StaticExecutable) {
        plan.error = "ELF is not a static executable image";
        plan.report = build_report(plan);
        return plan;
    }
    if (elf_plan.load_segments.empty()) {
        plan.error = "ELF has no preserved PT_LOAD segments";
        plan.report = build_report(plan);
        return plan;
    }

    std::uint64_t min_vaddr = std::numeric_limits<std::uint64_t>::max();
    std::uint64_t max_vaddr = 0;
    bool entry_ready = false;
    plan.segments.reserve(elf_plan.load_segments.size());

    for (const auto& load : elf_plan.load_segments) {
        if (load.mem_size < load.file_size) {
            plan.error = "PT_LOAD mem size is smaller than file size";
            plan.report = build_report(plan);
            return plan;
        }
        if ((load.offset % page_size) != (load.vaddr % page_size)) {
            plan.error = "PT_LOAD file offset and vaddr page alignment differ";
            plan.report = build_report(plan);
            return plan;
        }
        const std::uint64_t segment_end = load.vaddr + load.mem_size;
        if (segment_end < load.vaddr) {
            plan.error = "PT_LOAD virtual address overflow";
            plan.report = build_report(plan);
            return plan;
        }

        StaticImageSegmentMap mapped;
        mapped.source_offset = load.offset;
        mapped.map_offset = page_down(load.offset, page_size);
        mapped.vaddr = load.vaddr;
        mapped.map_vaddr = page_down(load.vaddr, page_size);
        mapped.file_size = load.file_size;
        mapped.mem_size = load.mem_size;
        mapped.page_delta = load.vaddr - mapped.map_vaddr;
        mapped.flags = load.flags;
        mapped.protection = protection_text(load.flags);
        if (!page_up(mapped.page_delta + mapped.file_size, page_size, mapped.file_map_size) ||
            !page_up(mapped.page_delta + mapped.mem_size, page_size, mapped.mem_map_size)) {
            plan.error = "PT_LOAD mapping size overflow";
            plan.report = build_report(plan);
            return plan;
        }
        if (mapped.mem_map_size > std::numeric_limits<std::uint64_t>::max() - mapped.map_vaddr) {
            plan.error = "PT_LOAD mapped address overflow";
            plan.report = build_report(plan);
            return plan;
        }
        entry_ready = entry_ready || entry_inside_segment(elf_plan.entry, mapped);
        min_vaddr = std::min(min_vaddr, mapped.map_vaddr);
        max_vaddr = std::max(max_vaddr, mapped.map_vaddr + mapped.mem_map_size);
        plan.segments.push_back(mapped);
    }

    if (max_vaddr < min_vaddr) {
        plan.error = "static image span overflow";
        plan.report = build_report(plan);
        return plan;
    }
    plan.valid = true;
    plan.entry_ready = entry_ready;
    plan.image_min_vaddr = min_vaddr;
    plan.image_max_vaddr = max_vaddr;
    plan.image_size = max_vaddr - min_vaddr;
    if (!entry_ready) {
        plan.error = "ELF entry is not inside a PT_LOAD segment";
    }
    plan.report = build_report(plan);
    return plan;
}

}  // namespace alr::runtime
