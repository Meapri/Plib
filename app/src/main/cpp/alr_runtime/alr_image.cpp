#include "alr_runtime/alr_image.hpp"

#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <limits>
#include <sstream>
#include <stdexcept>
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

std::string errno_message(const char* action) {
    std::ostringstream out;
    out << action << " failed errno=" << errno << " message=" << std::strerror(errno);
    return out.str();
}

int mmap_protection(std::uint32_t flags) {
    int prot = PROT_NONE;
    if ((flags & PF_R) != 0) prot |= PROT_READ;
    if ((flags & PF_W) != 0) prot |= PROT_WRITE;
    if ((flags & PF_X) != 0) prot |= PROT_EXEC;
    return prot;
}

void read_exact(int fd, void* buffer, std::size_t size, std::uint64_t offset) {
    auto* out = static_cast<unsigned char*>(buffer);
    std::size_t done = 0;
    while (done < size) {
        const ssize_t count = ::pread(fd, out + done, size - done, static_cast<off_t>(offset + done));
        if (count > 0) {
            done += static_cast<std::size_t>(count);
        } else if (count == 0) {
            throw std::runtime_error("unexpected EOF while loading static image");
        } else if (errno != EINTR) {
            throw std::runtime_error(errno_message("pread static image"));
        }
    }
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

std::string build_report(const StaticImageLoadResult& result) {
    std::ostringstream out;
    out << "ALR STATIC IMAGE LOAD PREFLIGHT: " << (result.loaded ? "PASS" : "FAIL");
    out << "\nALR STATIC IMAGE MPROTECT: " << (result.protected_segments ? "PASS" : "SKIP");
    out << "\nalr image mapped base=" << hex_value(result.mapped_base);
    out << "\nalr image mapped size=" << result.mapped_size;
    out << "\nalr image entry offset=" << hex_value(result.entry_offset);
    out << "\nalr image loaded segments=" << result.loaded_segment_count;
    out << "\nalr image unmapped=" << (result.unmapped ? "true" : "false");
    if (!result.error.empty()) {
        out << "\nalr image load error=" << result.error;
    }
    return out.str();
}

std::string build_report(const StaticImageRuntimeMapping& result) {
    std::ostringstream out;
    out << "ALR STATIC IMAGE TRANSFER MAP: " << (result.mapped ? "PASS" : "FAIL");
    out << "\nALR STATIC IMAGE TRANSFER MPROTECT: " << (result.protected_segments ? "PASS" : "SKIP");
    out << "\nalr transfer image base=" << hex_value(result.mapped_base);
    out << "\nalr transfer image size=" << result.mapped_size;
    out << "\nalr transfer entry address=" << hex_value(result.entry_address);
    out << "\nalr transfer image fixed address=" << (result.fixed_address ? "true" : "false");
    out << "\nalr transfer loaded segments=" << result.loaded_segment_count;
    out << "\nalr transfer image unmapped=" << (result.unmapped ? "true" : "false");
    if (!result.error.empty()) {
        out << "\nalr transfer image error=" << result.error;
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

std::string build_static_image_load_skip_report() {
    return
        "ALR STATIC IMAGE LOAD PREFLIGHT: SKIP\n"
        "ALR STATIC IMAGE MPROTECT: SKIP\n"
        "alr image mapped base=0x0\n"
        "alr image mapped size=0\n"
        "alr image entry offset=0x0\n"
        "alr image loaded segments=0\n"
        "alr image unmapped=false";
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

StaticImageLoadResult load_static_image_for_preflight(const std::string& host_path, const StaticImagePlan& plan) {
    StaticImageLoadResult result;
    result.mapped_size = plan.image_size;
    if (!plan.valid || !plan.entry_ready) {
        result.error = plan.error.empty() ? "static image plan is not entry-ready" : plan.error;
        result.report = build_report(result);
        return result;
    }
    if (plan.image_size == 0 || plan.image_size > static_cast<std::uint64_t>(std::numeric_limits<std::size_t>::max())) {
        result.error = "static image size is not mappable";
        result.report = build_report(result);
        return result;
    }

    const int fd = ::open(host_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        result.error = errno_message("open static image");
        result.report = build_report(result);
        return result;
    }

    void* mapped = ::mmap(
        nullptr,
        static_cast<std::size_t>(plan.image_size),
        PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS,
        -1,
        0);
    if (mapped == MAP_FAILED) {
        const int saved_errno = errno;
        ::close(fd);
        errno = saved_errno;
        result.error = errno_message("mmap static image");
        result.report = build_report(result);
        return result;
    }
    result.mapped_base = reinterpret_cast<std::uintptr_t>(mapped);
    result.entry_offset = plan.entry - plan.image_min_vaddr;

    auto* base = static_cast<unsigned char*>(mapped);
    try {
        for (const auto& segment : plan.segments) {
            const std::uint64_t relative = segment.map_vaddr - plan.image_min_vaddr;
            if (relative > plan.image_size || segment.mem_map_size > plan.image_size - relative) {
                throw std::runtime_error("static image segment falls outside mapped image");
            }
            read_exact(
                fd,
                base + relative + segment.page_delta,
                static_cast<std::size_t>(segment.file_size),
                segment.source_offset);
            ++result.loaded_segment_count;
        }
        result.loaded = true;
        for (const auto& segment : plan.segments) {
            const std::uint64_t relative = segment.map_vaddr - plan.image_min_vaddr;
            if (::mprotect(base + relative, static_cast<std::size_t>(segment.mem_map_size), mmap_protection(segment.flags)) != 0) {
                throw std::runtime_error(errno_message("mprotect static image segment"));
            }
        }
        result.protected_segments = true;
    } catch (const std::exception& exc) {
        result.error = exc.what();
    }

    const int saved_errno = errno;
    if (::munmap(mapped, static_cast<std::size_t>(plan.image_size)) == 0) {
        result.unmapped = true;
    } else if (result.error.empty()) {
        result.error = errno_message("munmap static image");
    }
    ::close(fd);
    errno = saved_errno;
    if (!result.loaded || !result.protected_segments || !result.unmapped) {
        result.loaded = false;
    }
    result.report = build_report(result);
    return result;
}

StaticImageRuntimeMapping map_static_image_for_transfer(const std::string& host_path, const StaticImagePlan& plan) {
    StaticImageRuntimeMapping result;
    result.mapped_size = plan.image_size;
    if (!plan.valid || !plan.entry_ready) {
        result.error = plan.error.empty() ? "static image plan is not entry-ready" : plan.error;
        result.report = build_report(result);
        return result;
    }
    if (plan.image_size == 0 || plan.image_size > static_cast<std::uint64_t>(std::numeric_limits<std::size_t>::max())) {
        result.error = "static image size is not mappable";
        result.report = build_report(result);
        return result;
    }

    const int fd = ::open(host_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        result.error = errno_message("open transfer image");
        result.report = build_report(result);
        return result;
    }
    void* mapped = ::mmap(
        nullptr,
        static_cast<std::size_t>(plan.image_size),
        PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS,
        -1,
        0);
    if (mapped == MAP_FAILED) {
        const int saved_errno = errno;
        ::close(fd);
        errno = saved_errno;
        result.error = errno_message("mmap transfer image");
        result.report = build_report(result);
        return result;
    }
    result.mapped = true;
    result.mapped_base = reinterpret_cast<std::uintptr_t>(mapped);
    result.entry_address = result.mapped_base + (plan.entry - plan.image_min_vaddr);

    auto* base = static_cast<unsigned char*>(mapped);
    try {
        for (const auto& segment : plan.segments) {
            const std::uint64_t relative = segment.map_vaddr - plan.image_min_vaddr;
            if (relative > plan.image_size || segment.mem_map_size > plan.image_size - relative) {
                throw std::runtime_error("transfer image segment falls outside mapped image");
            }
            read_exact(
                fd,
                base + relative + segment.page_delta,
                static_cast<std::size_t>(segment.file_size),
                segment.source_offset);
            ++result.loaded_segment_count;
        }
        for (const auto& segment : plan.segments) {
            const std::uint64_t relative = segment.map_vaddr - plan.image_min_vaddr;
            if (::mprotect(base + relative, static_cast<std::size_t>(segment.mem_map_size), mmap_protection(segment.flags)) != 0) {
                throw std::runtime_error(errno_message("mprotect transfer image segment"));
            }
        }
        result.protected_segments = true;
    } catch (const std::exception& exc) {
        result.error = exc.what();
    }
    ::close(fd);
    if (!result.protected_segments) {
        unmap_static_image_runtime_mapping(result);
    }
    result.report = build_report(result);
    return result;
}

StaticImageRuntimeMapping map_static_image_fixed_for_transfer(const std::string& host_path, const StaticImagePlan& plan) {
    StaticImageRuntimeMapping result;
    (void)host_path;
    result.mapped_size = plan.image_size;
    if (!plan.valid || !plan.entry_ready) {
        result.error = plan.error.empty() ? "static image plan is not entry-ready" : plan.error;
        result.report = build_report(result);
        return result;
    }
    if (plan.image_size == 0 || plan.image_size > static_cast<std::uint64_t>(std::numeric_limits<std::size_t>::max())) {
        result.error = "static image size is not mappable";
        result.report = build_report(result);
        return result;
    }
    if (plan.image_min_vaddr > static_cast<std::uint64_t>(std::numeric_limits<std::uintptr_t>::max())) {
        result.error = "static image fixed address is not representable";
        result.report = build_report(result);
        return result;
    }

#if defined(MAP_FIXED_NOREPLACE)
    const int fd = ::open(host_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        result.error = errno_message("open fixed transfer image");
        result.report = build_report(result);
        return result;
    }

    void* requested = reinterpret_cast<void*>(static_cast<std::uintptr_t>(plan.image_min_vaddr));
    void* mapped = ::mmap(
        requested,
        static_cast<std::size_t>(plan.image_size),
        PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE,
        -1,
        0);
    if (mapped == MAP_FAILED) {
        const int saved_errno = errno;
        ::close(fd);
        errno = saved_errno;
        result.error = errno_message("mmap fixed transfer image");
        result.report = build_report(result);
        return result;
    }
    if (mapped != requested) {
        result.error = "mmap fixed transfer image returned an unexpected address";
        result.mapped = true;
        result.mapped_base = reinterpret_cast<std::uintptr_t>(mapped);
        unmap_static_image_runtime_mapping(result);
        ::close(fd);
        result.report = build_report(result);
        return result;
    }

    result.mapped = true;
    result.fixed_address = true;
    result.mapped_base = reinterpret_cast<std::uintptr_t>(mapped);
    result.entry_address = plan.entry;

    auto* base = static_cast<unsigned char*>(mapped);
    try {
        for (const auto& segment : plan.segments) {
            const std::uint64_t relative = segment.map_vaddr - plan.image_min_vaddr;
            if (relative > plan.image_size || segment.mem_map_size > plan.image_size - relative) {
                throw std::runtime_error("fixed transfer image segment falls outside mapped image");
            }
            read_exact(
                fd,
                base + relative + segment.page_delta,
                static_cast<std::size_t>(segment.file_size),
                segment.source_offset);
            ++result.loaded_segment_count;
        }
        for (const auto& segment : plan.segments) {
            const std::uint64_t relative = segment.map_vaddr - plan.image_min_vaddr;
            if (::mprotect(base + relative, static_cast<std::size_t>(segment.mem_map_size), mmap_protection(segment.flags)) != 0) {
                throw std::runtime_error(errno_message("mprotect fixed transfer image segment"));
            }
        }
        result.protected_segments = true;
    } catch (const std::exception& exc) {
        result.error = exc.what();
    }
    ::close(fd);
    if (!result.protected_segments) {
        unmap_static_image_runtime_mapping(result);
    }
    result.report = build_report(result);
    return result;
#else
    result.error = "MAP_FIXED_NOREPLACE is unavailable on this host";
    result.report = build_report(result);
    return result;
#endif
}

void unmap_static_image_runtime_mapping(StaticImageRuntimeMapping& mapping) {
    if (!mapping.mapped || mapping.mapped_base == 0 || mapping.mapped_size == 0 || mapping.unmapped) {
        return;
    }
    if (::munmap(reinterpret_cast<void*>(mapping.mapped_base), static_cast<std::size_t>(mapping.mapped_size)) == 0) {
        mapping.unmapped = true;
    } else if (mapping.error.empty()) {
        mapping.error = errno_message("munmap transfer image");
    }
    if (mapping.unmapped) {
        mapping.mapped = false;
    }
    mapping.report = build_report(mapping);
}

}  // namespace alr::runtime
