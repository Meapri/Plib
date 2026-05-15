#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "alr_runtime/alr_elf.hpp"

namespace alr::runtime {

struct StaticImageSegmentMap {
    std::uint64_t source_offset = 0;
    std::uint64_t map_offset = 0;
    std::uint64_t vaddr = 0;
    std::uint64_t map_vaddr = 0;
    std::uint64_t file_size = 0;
    std::uint64_t mem_size = 0;
    std::uint64_t file_map_size = 0;
    std::uint64_t mem_map_size = 0;
    std::uint64_t page_delta = 0;
    std::uint32_t flags = 0;
    std::string protection;
};

struct StaticImagePlan {
    bool valid = false;
    bool entry_ready = false;
    bool fixed_vaddr_required = true;
    std::string error;
    std::uint64_t page_size = 0;
    std::uint64_t entry = 0;
    std::uint64_t image_min_vaddr = 0;
    std::uint64_t image_max_vaddr = 0;
    std::uint64_t image_size = 0;
    std::vector<StaticImageSegmentMap> segments;
    std::string report;
};

struct StaticImageLoadResult {
    bool loaded = false;
    bool protected_segments = false;
    bool unmapped = false;
    std::string error;
    std::uintptr_t mapped_base = 0;
    std::uint64_t mapped_size = 0;
    std::uint64_t entry_offset = 0;
    std::uint32_t loaded_segment_count = 0;
    std::string report;
};

struct StaticImageRuntimeMapping {
    bool mapped = false;
    bool protected_segments = false;
    bool fixed_address = false;
    bool unmapped = false;
    std::string error;
    std::uintptr_t mapped_base = 0;
    std::uint64_t mapped_size = 0;
    std::uint64_t load_bias = 0;
    std::uintptr_t entry_address = 0;
    std::uint32_t loaded_segment_count = 0;
    std::string report;
};

StaticImagePlan build_static_image_plan(const ElfLoadPlan& elf_plan, std::uint64_t page_size = 4096);
std::string build_static_image_skip_report();
StaticImageLoadResult load_static_image_for_preflight(const std::string& host_path, const StaticImagePlan& plan);
std::string build_static_image_load_skip_report();
StaticImageRuntimeMapping map_static_image_for_transfer(const std::string& host_path, const StaticImagePlan& plan);
StaticImageRuntimeMapping map_static_image_fixed_for_transfer(const std::string& host_path, const StaticImagePlan& plan);
void unmap_static_image_runtime_mapping(StaticImageRuntimeMapping& mapping);

}  // namespace alr::runtime
