#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace alr::runtime {

enum class ElfLoadPlanStatus {
    Unsupported,
    StaticExecutable,
    InterpreterNeeded,
};

struct ElfLoadSegment {
    std::uint64_t offset = 0;
    std::uint64_t vaddr = 0;
    std::uint64_t file_size = 0;
    std::uint64_t mem_size = 0;
    std::uint32_t flags = 0;
    std::uint64_t align = 0;
};

struct ElfLoadPlan {
    bool valid = false;
    ElfLoadPlanStatus status = ElfLoadPlanStatus::Unsupported;
    std::string error;
    std::string elf_class;
    std::string machine;
    std::string type;
    std::uint64_t entry = 0;
    std::uint64_t min_vaddr = 0;
    std::uint64_t max_vaddr = 0;
    std::uint64_t program_header_offset = 0;
    std::uint64_t program_header_vaddr = 0;
    std::uint16_t program_header_entry_size = 0;
    std::uint16_t program_header_count = 0;
    std::uint32_t load_segment_count = 0;
    std::vector<ElfLoadSegment> load_segments;
    std::string interpreter;
    std::string report;
};

const char* elf_load_plan_status_name(ElfLoadPlanStatus status);
ElfLoadPlan build_elf_load_plan(const std::string& host_path);

}  // namespace alr::runtime
