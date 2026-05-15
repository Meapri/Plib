#include "alr_runtime/alr_entry.hpp"

#include <sys/mman.h>

#include <algorithm>
#include <array>
#include <cerrno>
#include <cstring>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace alr::runtime {
namespace {

constexpr std::uint64_t kAtNull = 0;
constexpr std::uint64_t kAtPhdr = 3;
constexpr std::uint64_t kAtPhent = 4;
constexpr std::uint64_t kAtPhnum = 5;
constexpr std::uint64_t kAtPagesz = 6;
constexpr std::uint64_t kAtBase = 7;
constexpr std::uint64_t kAtFlags = 8;
constexpr std::uint64_t kAtEntry = 9;
constexpr std::uint64_t kAtUid = 11;
constexpr std::uint64_t kAtEuid = 12;
constexpr std::uint64_t kAtGid = 13;
constexpr std::uint64_t kAtEgid = 14;
constexpr std::uint64_t kAtSecure = 23;
constexpr std::uint64_t kAtRandom = 25;

std::string hex_value(std::uint64_t value) {
    std::ostringstream out;
    out << "0x" << std::hex << value;
    return out.str();
}

std::string errno_message(const char* action) {
    std::ostringstream out;
    out << action << " failed errno=" << errno << " message=" << std::strerror(errno);
    return out.str();
}

std::uint64_t align_down(std::uint64_t value, std::uint64_t alignment) {
    return value / alignment * alignment;
}

void write_u64(std::vector<unsigned char>& image, std::uint64_t offset, std::uint64_t value) {
    for (std::size_t i = 0; i < sizeof(value); ++i) {
        image.at(static_cast<std::size_t>(offset) + i) = static_cast<unsigned char>((value >> (i * 8)) & 0xff);
    }
}

std::string build_report(const EntryStackPlan& plan) {
    std::ostringstream out;
    out << "ALR STATIC ENTRY STACK PLAN: " << (plan.valid ? "PASS" : "FAIL");
    out << "\nalr entry stack top=" << hex_value(plan.stack_top_vaddr);
    out << "\nalr entry stack size=" << plan.stack_size;
    out << "\nalr entry initial sp=" << hex_value(plan.initial_sp_vaddr);
    out << "\nalr entry vaddr=" << hex_value(plan.entry_vaddr);
    out << "\nalr entry argc=" << plan.argc;
    out << "\nalr entry envc=" << plan.env_count;
    out << "\nalr entry auxv pairs=" << plan.auxv_pair_count;
    if (!plan.error.empty()) {
        out << "\nalr entry stack error=" << plan.error;
    }
    return out.str();
}

std::string build_report(const EntryStackRuntimeMapping& mapping) {
    std::ostringstream out;
    out << "ALR STATIC ENTRY STACK TRANSFER MAP: " << (mapping.mapped ? "PASS" : "FAIL");
    out << "\nALR STATIC ENTRY STACK TRANSFER MPROTECT: " << (mapping.protected_stack ? "PASS" : "SKIP");
    out << "\nalr transfer stack base=" << hex_value(mapping.mapped_base);
    out << "\nalr transfer stack size=" << mapping.mapped_size;
    out << "\nalr transfer initial sp address=" << hex_value(mapping.initial_sp_address);
    out << "\nalr transfer stack unmapped=" << (mapping.unmapped ? "true" : "false");
    if (!mapping.error.empty()) {
        out << "\nalr transfer stack error=" << mapping.error;
    }
    return out.str();
}

bool valid_stack_bounds(const EntryStackInput& input) {
    return input.stack_size >= 4096 &&
        input.stack_size <= 8 * 1024 * 1024 &&
        input.stack_top_vaddr > input.stack_size &&
        (input.stack_top_vaddr % 16) == 0;
}

}  // namespace

std::string build_entry_stack_skip_report() {
    return
        "ALR STATIC ENTRY STACK PLAN: SKIP\n"
        "alr entry stack top=0x0\n"
        "alr entry stack size=0\n"
        "alr entry initial sp=0x0\n"
        "alr entry vaddr=0x0\n"
        "alr entry argc=0\n"
        "alr entry envc=0\n"
        "alr entry auxv pairs=0";
}

EntryStackPlan build_static_entry_stack_plan(
    const ElfLoadPlan& elf_plan,
    const StaticImagePlan& image_plan,
    const EntryStackInput& input) {
    EntryStackPlan plan;
    plan.stack_top_vaddr = input.stack_top_vaddr;
    plan.stack_size = input.stack_size;
    plan.entry_vaddr = elf_plan.entry;
    plan.argc = static_cast<std::uint32_t>(input.argv.size());
    plan.env_count = static_cast<std::uint32_t>(input.env.size());

    if (!elf_plan.valid || elf_plan.status != ElfLoadPlanStatus::StaticExecutable) {
        plan.error = "ELF is not a static executable";
        plan.report = build_report(plan);
        return plan;
    }
    if (!image_plan.valid || !image_plan.entry_ready) {
        plan.error = "static image is not entry-ready";
        plan.report = build_report(plan);
        return plan;
    }
    if (elf_plan.program_header_vaddr == 0 ||
        elf_plan.program_header_entry_size == 0 ||
        elf_plan.program_header_count == 0) {
        plan.error = "ELF program header address is unavailable";
        plan.report = build_report(plan);
        return plan;
    }
    if (!valid_stack_bounds(input)) {
        plan.error = "entry stack bounds are invalid";
        plan.report = build_report(plan);
        return plan;
    }

    plan.image.assign(static_cast<std::size_t>(input.stack_size), 0);
    std::uint64_t cursor = input.stack_size;
    const std::uint64_t stack_base_vaddr = input.stack_top_vaddr - input.stack_size;

    auto push_bytes = [&](const void* data, std::uint64_t size, std::uint64_t alignment) -> std::uint64_t {
        if (size > cursor) {
            throw std::runtime_error("entry stack string area overflow");
        }
        cursor = align_down(cursor - size, alignment);
        if (cursor + size > plan.image.size()) {
            throw std::runtime_error("entry stack write overflow");
        }
        std::memcpy(plan.image.data() + cursor, data, static_cast<std::size_t>(size));
        return stack_base_vaddr + cursor;
    };

    std::vector<std::uint64_t> argv_ptrs;
    argv_ptrs.reserve(input.argv.size());
    std::vector<std::uint64_t> env_ptrs;
    env_ptrs.reserve(input.env.size());

    try {
        for (auto iter = input.env.rbegin(); iter != input.env.rend(); ++iter) {
            env_ptrs.push_back(push_bytes(iter->c_str(), iter->size() + 1, 1));
        }
        std::reverse(env_ptrs.begin(), env_ptrs.end());
        for (auto iter = input.argv.rbegin(); iter != input.argv.rend(); ++iter) {
            argv_ptrs.push_back(push_bytes(iter->c_str(), iter->size() + 1, 1));
        }
        std::reverse(argv_ptrs.begin(), argv_ptrs.end());

        const std::array<unsigned char, 16> random_bytes{
            0x41, 0x4c, 0x52, 0x2d, 0x73, 0x74, 0x61, 0x63,
            0x6b, 0x2d, 0x72, 0x61, 0x6e, 0x64, 0x30, 0x31,
        };
        const std::uint64_t random_vaddr = push_bytes(random_bytes.data(), random_bytes.size(), 16);

        std::vector<std::pair<std::uint64_t, std::uint64_t>> auxv{
            {kAtPhdr, elf_plan.program_header_vaddr},
            {kAtPhent, elf_plan.program_header_entry_size},
            {kAtPhnum, elf_plan.program_header_count},
            {kAtPagesz, image_plan.page_size},
            {kAtBase, 0},
            {kAtFlags, 0},
            {kAtEntry, elf_plan.entry},
            {kAtUid, 0},
            {kAtEuid, 0},
            {kAtGid, 0},
            {kAtEgid, 0},
            {kAtSecure, 0},
            {kAtRandom, random_vaddr},
            {kAtNull, 0},
        };
        plan.auxv_pair_count = static_cast<std::uint32_t>(auxv.size());

        std::vector<std::uint64_t> words;
        words.reserve(1 + argv_ptrs.size() + 1 + env_ptrs.size() + 1 + auxv.size() * 2);
        words.push_back(argv_ptrs.size());
        words.insert(words.end(), argv_ptrs.begin(), argv_ptrs.end());
        words.push_back(0);
        words.insert(words.end(), env_ptrs.begin(), env_ptrs.end());
        words.push_back(0);
        for (const auto& [key, value] : auxv) {
            words.push_back(key);
            words.push_back(value);
        }

        const std::uint64_t words_size = words.size() * sizeof(std::uint64_t);
        if (words_size > cursor) {
            throw std::runtime_error("entry stack pointer area overflow");
        }
        cursor = align_down(cursor - words_size, 16);
        for (std::size_t i = 0; i < words.size(); ++i) {
            write_u64(plan.image, cursor + i * sizeof(std::uint64_t), words[i]);
        }
        plan.initial_sp_vaddr = stack_base_vaddr + cursor;
        plan.valid = true;
    } catch (const std::exception& exc) {
        plan.error = exc.what();
    }
    plan.report = build_report(plan);
    return plan;
}

EntryStackRuntimeMapping map_entry_stack_for_transfer(const EntryStackPlan& plan) {
    EntryStackRuntimeMapping mapping;
    mapping.mapped_size = plan.stack_size;
    if (!plan.valid) {
        mapping.error = plan.error.empty() ? "entry stack plan is not valid" : plan.error;
        mapping.report = build_report(mapping);
        return mapping;
    }
    if (plan.image.empty() || plan.stack_size == 0 ||
        plan.stack_size > static_cast<std::uint64_t>(std::numeric_limits<std::size_t>::max())) {
        mapping.error = "entry stack image is not mappable";
        mapping.report = build_report(mapping);
        return mapping;
    }
    const std::uint64_t stack_base_vaddr = plan.stack_top_vaddr - plan.stack_size;
    if (plan.initial_sp_vaddr < stack_base_vaddr || plan.initial_sp_vaddr > plan.stack_top_vaddr) {
        mapping.error = "entry stack initial sp falls outside stack image";
        mapping.report = build_report(mapping);
        return mapping;
    }

    void* mapped = ::mmap(
        nullptr,
        static_cast<std::size_t>(plan.stack_size),
        PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS,
        -1,
        0);
    if (mapped == MAP_FAILED) {
        mapping.error = errno_message("mmap transfer stack");
        mapping.report = build_report(mapping);
        return mapping;
    }

    mapping.mapped = true;
    mapping.mapped_base = reinterpret_cast<std::uintptr_t>(mapped);
    mapping.initial_sp_address = mapping.mapped_base + (plan.initial_sp_vaddr - stack_base_vaddr);
    std::memcpy(mapped, plan.image.data(), plan.image.size());
    if (::mprotect(mapped, static_cast<std::size_t>(plan.stack_size), PROT_READ | PROT_WRITE) == 0) {
        mapping.protected_stack = true;
    } else {
        mapping.error = errno_message("mprotect transfer stack");
        unmap_entry_stack_runtime_mapping(mapping);
    }
    mapping.report = build_report(mapping);
    return mapping;
}

void unmap_entry_stack_runtime_mapping(EntryStackRuntimeMapping& mapping) {
    if (!mapping.mapped || mapping.mapped_base == 0 || mapping.mapped_size == 0 || mapping.unmapped) {
        return;
    }
    if (::munmap(reinterpret_cast<void*>(mapping.mapped_base), static_cast<std::size_t>(mapping.mapped_size)) == 0) {
        mapping.unmapped = true;
    } else if (mapping.error.empty()) {
        mapping.error = errno_message("munmap transfer stack");
    }
    if (mapping.unmapped) {
        mapping.mapped = false;
    }
    mapping.report = build_report(mapping);
}

}  // namespace alr::runtime
