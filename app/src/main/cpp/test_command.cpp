#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <string_view>
#include <sys/wait.h>
#include <unistd.h>

namespace {

int fork_benchmark(int argc, char** argv) {
    int repeat_count = 10;
    if (argc > 2) {
        repeat_count = std::atoi(argv[2]);
    }
    repeat_count = std::clamp(repeat_count, 1, 200);

    int attempted_count = 0;
    int pass_count = 0;
    long long total_elapsed_us = 0;
    long long min_elapsed_us = 0;
    long long max_elapsed_us = 0;

    for (int index = 0; index < repeat_count; ++index) {
        const auto started = std::chrono::steady_clock::now();
        const pid_t child = ::fork();
        if (child == 0) {
            _exit(0);
        }
        ++attempted_count;
        if (child < 0) {
            break;
        }

        int status = 0;
        if (::waitpid(child, &status, 0) < 0) {
            break;
        }
        const auto elapsed_us = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - started).count();
        total_elapsed_us += elapsed_us;
        if (index == 0 || elapsed_us < min_elapsed_us) {
            min_elapsed_us = elapsed_us;
        }
        if (elapsed_us > max_elapsed_us) {
            max_elapsed_us = elapsed_us;
        }
        if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
            ++pass_count;
        } else {
            break;
        }
    }

    const bool success =
        attempted_count == repeat_count &&
        pass_count == repeat_count;
    const long long average_elapsed_us = attempted_count > 0 ? total_elapsed_us / attempted_count : 0;
    std::cout << "NATIVE BIONIC FORK BENCHMARK: " << (success ? "PASS" : "FAIL") << "\n";
    std::cout << "native fork repeat requested count=" << repeat_count << "\n";
    std::cout << "native fork repeat attempted count=" << attempted_count << "\n";
    std::cout << "native fork repeat pass count=" << pass_count << "\n";
    std::cout << "native fork repeat total elapsed us=" << total_elapsed_us << "\n";
    std::cout << "native fork repeat average elapsed us=" << average_elapsed_us << "\n";
    std::cout << "native fork repeat min elapsed us=" << min_elapsed_us << "\n";
    std::cout << "native fork repeat max elapsed us=" << max_elapsed_us << "\n";
    return success ? 0 : 1;
}

}  // namespace

int main(int argc, char** argv) {
    if (argc > 1 && std::string_view(argv[1]) == "fork-benchmark") {
        return fork_benchmark(argc, argv);
    }

    std::cout << "alr-test-command ok";
    for (int i = 1; i < argc; ++i) {
        std::cout << " arg" << i << "=" << argv[i];
    }
    std::cout << "\n";
    return 0;
}
