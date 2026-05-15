#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include <unistd.h>

#include "../app/src/main/cpp/alr_runtime/alr_elf_format.hpp"
#include "../app/src/main/cpp/alr_runtime/alr_elf.hpp"
#include "../app/src/main/cpp/alr_runtime/alr_image.hpp"

namespace {

void require(bool condition, const char* message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

void write_at(std::vector<unsigned char>& bytes, std::size_t offset, const void* data, std::size_t size) {
    if (offset + size > bytes.size()) {
        bytes.resize(offset + size);
    }
    std::memcpy(bytes.data() + offset, data, size);
}

void write_file(const std::filesystem::path& path, const std::vector<unsigned char>& bytes) {
    std::ofstream os(path, std::ios::binary);
    os.write(reinterpret_cast<const char*>(bytes.data()), static_cast<std::streamsize>(bytes.size()));
}

std::vector<unsigned char> make_static_elf(
    std::uint64_t entry = 0x400080,
    std::uint64_t offset = 0,
    std::uint64_t vaddr = 0x400000,
    std::uint64_t file_size = 0x1000,
    std::uint64_t mem_size = 0x2000) {
    std::vector<unsigned char> bytes(0x4000);

    Elf64_Ehdr ehdr {};
    std::memcpy(ehdr.e_ident, ELFMAG, SELFMAG);
    ehdr.e_ident[EI_CLASS] = ELFCLASS64;
    ehdr.e_ident[EI_DATA] = ELFDATA2LSB;
    ehdr.e_ident[EI_VERSION] = EV_CURRENT;
    ehdr.e_type = ET_EXEC;
    ehdr.e_machine = EM_AARCH64;
    ehdr.e_version = EV_CURRENT;
    ehdr.e_entry = entry;
    ehdr.e_phoff = sizeof(Elf64_Ehdr);
    ehdr.e_ehsize = sizeof(Elf64_Ehdr);
    ehdr.e_phentsize = sizeof(Elf64_Phdr);
    ehdr.e_phnum = 1;
    write_at(bytes, 0, &ehdr, sizeof(ehdr));

    Elf64_Phdr load {};
    load.p_type = PT_LOAD;
    load.p_flags = PF_R | PF_X;
    load.p_offset = offset;
    load.p_vaddr = vaddr;
    load.p_filesz = file_size;
    load.p_memsz = mem_size;
    load.p_align = 0x1000;
    write_at(bytes, ehdr.e_phoff, &load, sizeof(load));
    return bytes;
}

}  // namespace

int main() {
    const auto dir = std::filesystem::temp_directory_path() /
        ("alr-static-image-plan-" + std::to_string(static_cast<long long>(::getpid())));
    std::filesystem::remove_all(dir);
    std::filesystem::create_directories(dir);

    const auto valid_path = dir / "hello-static";
    const auto bad_mem_path = dir / "bad-mem";
    const auto bad_align_path = dir / "bad-align";
    const auto entry_outside_path = dir / "entry-outside";

    write_file(valid_path, make_static_elf());
    write_file(bad_mem_path, make_static_elf(0x400080, 0, 0x400000, 0x2000, 0x1000));
    write_file(bad_align_path, make_static_elf(0x400080, 0x100, 0x400000));
    write_file(entry_outside_path, make_static_elf(0x500000));

    const auto valid_elf = alr::runtime::build_elf_load_plan(valid_path.string());
    require(valid_elf.load_segments.size() == 1, "preserved load segment");
    const auto valid_plan = alr::runtime::build_static_image_plan(valid_elf);
    require(valid_plan.valid, "valid image plan");
    require(valid_plan.entry_ready, "entry ready");
    require(valid_plan.segments.size() == 1, "mapped segment count");
    require(valid_plan.segments[0].map_vaddr == 0x400000, "map vaddr");
    require(valid_plan.segments[0].file_map_size == 0x1000, "file map size");
    require(valid_plan.segments[0].mem_map_size == 0x2000, "mem map size");
    require(valid_plan.segments[0].protection == "r-x", "protection");
    require(valid_plan.report.find("ALR STATIC IMAGE MAP PLAN: PASS") != std::string::npos, "image report");
    require(valid_plan.report.find("ALR STATIC IMAGE ENTRY READY: PASS") != std::string::npos, "entry report");

    const auto bad_mem_plan = alr::runtime::build_static_image_plan(alr::runtime::build_elf_load_plan(bad_mem_path.string()));
    require(!bad_mem_plan.valid, "bad mem invalid");
    require(bad_mem_plan.report.find("mem size is smaller") != std::string::npos, "bad mem report");

    const auto bad_align_plan = alr::runtime::build_static_image_plan(alr::runtime::build_elf_load_plan(bad_align_path.string()));
    require(!bad_align_plan.valid, "bad align invalid");
    require(bad_align_plan.report.find("page alignment differ") != std::string::npos, "bad align report");

    const auto entry_outside_plan = alr::runtime::build_static_image_plan(alr::runtime::build_elf_load_plan(entry_outside_path.string()));
    require(entry_outside_plan.valid, "entry outside still maps");
    require(!entry_outside_plan.entry_ready, "entry outside not ready");
    require(entry_outside_plan.report.find("ALR STATIC IMAGE ENTRY READY: SKIP") != std::string::npos, "entry outside report");

    std::filesystem::remove_all(dir);
    std::cout << "alr runtime image native test ok\n";
    return EXIT_SUCCESS;
}
