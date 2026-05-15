#include <cstdlib>
#include <iostream>
#include <string_view>

#include "alr_runtime/alr_elf.hpp"
#include "alr_runtime/alr_entry.hpp"
#include "alr_runtime/alr_handoff.hpp"
#include "alr_runtime/alr_image.hpp"
#include "alr_runtime/alr_transfer.hpp"

namespace {

const char* env_or_none(const char* key) {
    const char* value = std::getenv(key);
    return value == nullptr || value[0] == '\0' ? "none" : value;
}

bool env_enabled(const char* key) {
    const char* value = std::getenv(key);
    return value != nullptr &&
        (std::string_view(value) == "1" ||
            std::string_view(value) == "true" ||
            std::string_view(value) == "yes");
}

int env_int_or_default(const char* key, int fallback) {
    const char* value = std::getenv(key);
    if (value == nullptr || value[0] == '\0') {
        return fallback;
    }
    const int parsed = std::atoi(value);
    return parsed > 0 ? parsed : fallback;
}

}  // namespace

int main(int argc, char** argv) {
    const bool preflight = argc > 1 && std::string_view(argv[1]) == "--preflight";
    if (!preflight) {
        std::cerr << "ALR TRAMPOLINE PREFLIGHT: FAIL\n";
        std::cerr << "alr trampoline reason=missing --preflight\n";
        return 2;
    }
    std::cout << "ALR TRAMPOLINE PREFLIGHT: PASS\n";
    std::cout << "alr trampoline mode=" << env_or_none("ALR_TRAMPOLINE_MODE") << "\n";
    std::cout << "alr trampoline config checksum=" << env_or_none("ALR_CONFIG_CHECKSUM") << "\n";
    std::cout << "alr trampoline target guest=" << env_or_none("ALR_TRAMPOLINE_TARGET_GUEST_PATH") << "\n";
    std::cout << "alr trampoline target host=" << env_or_none("ALR_TRAMPOLINE_TARGET_HOST_PATH") << "\n";
    std::cout << "alr trampoline elf status=" << env_or_none("ALR_TRAMPOLINE_ELF_STATUS") << "\n";
    std::cout << "alr trampoline execute entry=" << (env_enabled("ALR_TRAMPOLINE_EXECUTE_ENTRY") ? "true" : "false") << "\n";
    std::cout << "alr trampoline handoff timeout ms=" << env_int_or_default("ALR_TRAMPOLINE_HANDOFF_TIMEOUT_MS", 1000) << "\n";
    const char* target_host = std::getenv("ALR_TRAMPOLINE_TARGET_HOST_PATH");
    if (target_host != nullptr && target_host[0] != '\0') {
        const auto elf_plan = alr::runtime::build_elf_load_plan(target_host);
        const auto image_plan = alr::runtime::build_static_image_plan(elf_plan);
        const auto entry_plan = alr::runtime::build_static_entry_stack_plan(
            elf_plan,
            image_plan,
            alr::runtime::EntryStackInput{
                .argv = {env_or_none("ALR_TRAMPOLINE_TARGET_GUEST_PATH")},
                .env = {"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"},
            });
        std::cout << entry_plan.report << "\n";
        const auto load_result = alr::runtime::load_static_image_for_preflight(target_host, image_plan);
        std::cout << load_result.report << "\n";
        auto transfer_context = alr::runtime::prepare_static_entry_transfer_context(
            target_host,
            image_plan,
            entry_plan);
        const auto handoff_result = alr::runtime::maybe_run_static_entry_handoff(
            transfer_context,
            env_enabled("ALR_TRAMPOLINE_EXECUTE_ENTRY"),
            env_int_or_default("ALR_TRAMPOLINE_HANDOFF_TIMEOUT_MS", 1000));
        std::cout << handoff_result.report << "\n";
        alr::runtime::cleanup_static_entry_transfer_context(transfer_context);
        std::cout << transfer_context.report << "\n";
    } else {
        std::cout << alr::runtime::build_entry_stack_skip_report() << "\n";
        std::cout << alr::runtime::build_static_image_load_skip_report() << "\n";
        std::cout << alr::runtime::build_static_entry_handoff_skip_report() << "\n";
        std::cout << alr::runtime::build_static_entry_transfer_skip_report() << "\n";
    }
    return 0;
}
