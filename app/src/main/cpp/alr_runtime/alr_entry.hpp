#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "alr_runtime/alr_elf.hpp"
#include "alr_runtime/alr_image.hpp"

namespace alr::runtime {

struct EntryStackInput {
    std::vector<std::string> argv;
    std::vector<std::string> env;
    std::uint64_t stack_top_vaddr = 0x7fff00000000ULL;
    std::uint64_t stack_size = 256 * 1024;
};

struct EntryStackPlan {
    bool valid = false;
    std::string error;
    std::uint64_t stack_top_vaddr = 0;
    std::uint64_t stack_size = 0;
    std::uint64_t initial_sp_vaddr = 0;
    std::uint64_t entry_vaddr = 0;
    std::uint32_t argc = 0;
    std::uint32_t env_count = 0;
    std::uint32_t auxv_pair_count = 0;
    std::vector<unsigned char> image;
    std::string report;
};

struct EntryStackRuntimeMapping {
    bool mapped = false;
    bool protected_stack = false;
    bool unmapped = false;
    std::string error;
    std::uintptr_t mapped_base = 0;
    std::uint64_t mapped_size = 0;
    std::uintptr_t initial_sp_address = 0;
    std::string report;
};

EntryStackPlan build_static_entry_stack_plan(
    const ElfLoadPlan& elf_plan,
    const StaticImagePlan& image_plan,
    const EntryStackInput& input);

std::string build_entry_stack_skip_report();
EntryStackRuntimeMapping map_entry_stack_for_transfer(const EntryStackPlan& plan);
void unmap_entry_stack_runtime_mapping(EntryStackRuntimeMapping& mapping);

}  // namespace alr::runtime
