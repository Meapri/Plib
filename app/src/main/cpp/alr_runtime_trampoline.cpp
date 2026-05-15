#include <cstdlib>
#include <iostream>
#include <string_view>

namespace {

const char* env_or_none(const char* key) {
    const char* value = std::getenv(key);
    return value == nullptr || value[0] == '\0' ? "none" : value;
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
    return 0;
}
