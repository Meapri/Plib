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
    std::string error;
    std::uint64_t page_size = 0;
    std::uint64_t entry = 0;
    std::uint64_t image_min_vaddr = 0;
    std::uint64_t image_max_vaddr = 0;
    std::uint64_t image_size = 0;
    std::vector<StaticImageSegmentMap> segments;
    std::string report;
};

StaticImagePlan build_static_image_plan(const ElfLoadPlan& elf_plan, std::uint64_t page_size = 4096);
std::string build_static_image_skip_report();

}  // namespace alr::runtime
