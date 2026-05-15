#include <cstdlib>
#include <iostream>
#include <string>
#include <string_view>
#include <vector>

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

int env_int_clamped(const char* key, int fallback, int minimum, int maximum) {
    int value = env_int_or_default(key, fallback);
    if (value < minimum) {
        value = minimum;
    }
    if (value > maximum) {
        value = maximum;
    }
    return value;
}

std::vector<std::string> env_extra_args() {
    std::vector<std::string> args;
    const int count = env_int_or_default("ALR_TRAMPOLINE_EXTRA_ARG_COUNT", 0);
    for (int index = 0; index < count && index < 16; ++index) {
        const std::string key = "ALR_TRAMPOLINE_EXTRA_ARG_" + std::to_string(index);
        const char* value = std::getenv(key.c_str());
        if (value != nullptr) {
            args.emplace_back(value);
        }
    }
    return args;
}

std::vector<std::string> env_extra_guest_env() {
    std::vector<std::string> env;
    const int count = env_int_or_default("ALR_TRAMPOLINE_EXTRA_ENV_COUNT", 0);
    for (int index = 0; index < count && index < 16; ++index) {
        const std::string key = "ALR_TRAMPOLINE_EXTRA_ENV_" + std::to_string(index);
        const char* value = std::getenv(key.c_str());
        if (value != nullptr && std::string_view(value).find('=') != std::string_view::npos) {
            env.emplace_back(value);
        }
    }
    return env;
}

struct HandoffBenchmarkSummary {
    int requested_count = 1;
    int attempted_count = 0;
    int pass_count = 0;
    int total_elapsed_ms = 0;
    int min_elapsed_ms = 0;
    int max_elapsed_ms = 0;
    alr::runtime::StaticEntryHandoffResult last_result;
};

bool handoff_passed(const alr::runtime::StaticEntryHandoffResult& result) {
    return result.attempted && result.child_exited && result.exit_code == 0;
}

HandoffBenchmarkSummary run_handoff_benchmark(
    const alr::runtime::StaticEntryTransferContext& transfer_context,
    bool execute_requested,
    int timeout_ms,
    int repeat_count,
    const alr::runtime::StaticEntryHandoffOptions& options) {
    HandoffBenchmarkSummary summary;
    summary.requested_count = repeat_count;
    for (int index = 0; index < repeat_count; ++index) {
        auto result = alr::runtime::maybe_run_static_entry_handoff(
            transfer_context,
            execute_requested,
            timeout_ms,
            options);
        summary.last_result = result;
        ++summary.attempted_count;
        summary.total_elapsed_ms += result.elapsed_ms;
        if (index == 0 || result.elapsed_ms < summary.min_elapsed_ms) {
            summary.min_elapsed_ms = result.elapsed_ms;
        }
        if (result.elapsed_ms > summary.max_elapsed_ms) {
            summary.max_elapsed_ms = result.elapsed_ms;
        }
        if (handoff_passed(result)) {
            ++summary.pass_count;
        } else {
            break;
        }
    }
    return summary;
}

std::string build_handoff_benchmark_report(const HandoffBenchmarkSummary& summary) {
    const int average_elapsed_ms =
        summary.attempted_count > 0 ? summary.total_elapsed_ms / summary.attempted_count : 0;
    const bool success =
        summary.requested_count > 0 &&
        summary.attempted_count == summary.requested_count &&
        summary.pass_count == summary.requested_count;
    std::string report =
        std::string("ALR STATIC ENTRY HANDOFF BENCHMARK: ") + (success ? "PASS" : "FAIL");
    report += "\nalr handoff repeat requested count=" + std::to_string(summary.requested_count);
    report += "\nalr handoff repeat attempted count=" + std::to_string(summary.attempted_count);
    report += "\nalr handoff repeat pass count=" + std::to_string(summary.pass_count);
    report += "\nalr handoff repeat total elapsed ms=" + std::to_string(summary.total_elapsed_ms);
    report += "\nalr handoff repeat average elapsed ms=" + std::to_string(average_elapsed_ms);
    report += "\nalr handoff repeat min elapsed ms=" + std::to_string(summary.min_elapsed_ms);
    report += "\nalr handoff repeat max elapsed ms=" + std::to_string(summary.max_elapsed_ms);
    return report;
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
    std::cout << "alr trampoline path rewrite=" << (env_enabled("ALR_TRAMPOLINE_PATH_REWRITE") ? "true" : "false") << "\n";
    std::cout << "alr trampoline path rewrite limit=" << env_int_or_default("ALR_TRAMPOLINE_PATH_REWRITE_LIMIT", 0) << "\n";
    std::cout << "alr trampoline path rewrite idle syscall limit=" << env_int_or_default("ALR_TRAMPOLINE_PATH_REWRITE_IDLE_SYSCALL_LIMIT", 0) << "\n";
    std::cout << "alr trampoline virtual root identity=" << (env_enabled("ALR_TRAMPOLINE_VIRTUAL_ROOT_IDENTITY") ? "true" : "false") << "\n";
    std::cout << "alr trampoline exec loader path=" << env_or_none("ALR_TRAMPOLINE_EXEC_LOADER_PATH") << "\n";
    const int repeat_count = env_int_clamped("ALR_TRAMPOLINE_REPEAT_COUNT", 1, 1, 50);
    std::cout << "alr trampoline repeat count=" << repeat_count << "\n";
    const char* target_host = std::getenv("ALR_TRAMPOLINE_TARGET_HOST_PATH");
    if (target_host != nullptr && target_host[0] != '\0') {
        const auto elf_plan = alr::runtime::build_elf_load_plan(target_host);
        const auto image_plan = alr::runtime::build_static_image_plan(elf_plan);
        std::vector<std::string> guest_argv{env_or_none("ALR_TRAMPOLINE_TARGET_GUEST_PATH")};
        const auto extra_args = env_extra_args();
        guest_argv.insert(guest_argv.end(), extra_args.begin(), extra_args.end());
        std::vector<std::string> guest_env{"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"};
        const auto extra_guest_env = env_extra_guest_env();
        guest_env.insert(guest_env.end(), extra_guest_env.begin(), extra_guest_env.end());
        const auto entry_plan = alr::runtime::build_static_entry_stack_plan(
            elf_plan,
            image_plan,
            alr::runtime::EntryStackInput{
                .argv = guest_argv,
                .env = guest_env,
            });
        std::cout << entry_plan.report << "\n";
        const auto load_result = alr::runtime::load_static_image_for_preflight(target_host, image_plan);
        std::cout << load_result.report << "\n";
        auto transfer_context = alr::runtime::prepare_static_entry_transfer_context(
            target_host,
            image_plan,
            entry_plan);
        const bool execute_requested = env_enabled("ALR_TRAMPOLINE_EXECUTE_ENTRY");
        const std::string_view rootfs_env = env_or_none("ALR_ROOTFS");
        const alr::runtime::StaticEntryHandoffOptions handoff_options{
            .path_rewrite_enabled = env_enabled("ALR_TRAMPOLINE_PATH_REWRITE"),
            .path_rewrite_limit = static_cast<std::uint32_t>(env_int_or_default("ALR_TRAMPOLINE_PATH_REWRITE_LIMIT", 0)),
            .path_rewrite_idle_syscall_limit = static_cast<std::uint32_t>(env_int_or_default("ALR_TRAMPOLINE_PATH_REWRITE_IDLE_SYSCALL_LIMIT", 0)),
            .virtual_root_identity = env_enabled("ALR_TRAMPOLINE_VIRTUAL_ROOT_IDENTITY"),
            .rootfs_path = rootfs_env == "none" ? "" : std::string(rootfs_env),
            .exec_loader_path = std::string(env_or_none("ALR_TRAMPOLINE_EXEC_LOADER_PATH")) == "none" ? "" : std::string(env_or_none("ALR_TRAMPOLINE_EXEC_LOADER_PATH")),
        };
        const auto handoff_summary = run_handoff_benchmark(
            transfer_context,
            execute_requested,
            env_int_or_default("ALR_TRAMPOLINE_HANDOFF_TIMEOUT_MS", 1000),
            repeat_count,
            handoff_options);
        std::cout << handoff_summary.last_result.report << "\n";
        if (execute_requested) {
            std::cout << build_handoff_benchmark_report(handoff_summary) << "\n";
        }
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
