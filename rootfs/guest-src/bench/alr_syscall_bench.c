#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static long long now_us(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return ((long long)ts.tv_sec * 1000000LL) + (ts.tv_nsec / 1000LL);
}

static int parse_count(const char* value, int fallback, int minimum, int maximum) {
    if (value == NULL || value[0] == '\0') {
        return fallback;
    }
    char* end = NULL;
    const long parsed = strtol(value, &end, 10);
    if (end == value || parsed < minimum) {
        return fallback;
    }
    if (parsed > maximum) {
        return maximum;
    }
    return (int)parsed;
}

static int bench_stat(const char* path, int count, uint64_t* checksum) {
    int pass_count = 0;
    for (int index = 0; index < count; ++index) {
        struct stat st;
        if (stat(path, &st) != 0) {
            return pass_count;
        }
        *checksum += (uint64_t)st.st_size + (uint64_t)(st.st_mode & 07777);
        ++pass_count;
    }
    return pass_count;
}

static int bench_open_read_close(const char* path, int count, uint64_t* checksum) {
    int pass_count = 0;
    char buffer[256];
    for (int index = 0; index < count; ++index) {
        const int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) {
            return pass_count;
        }
        const ssize_t read_count = read(fd, buffer, sizeof(buffer));
        const int saved_errno = errno;
        close(fd);
        if (read_count < 0) {
            errno = saved_errno;
            return pass_count;
        }
        for (ssize_t offset = 0; offset < read_count; ++offset) {
            *checksum += (unsigned char)buffer[offset];
        }
        ++pass_count;
    }
    return pass_count;
}

static int bench_spawn(int count, uint64_t* checksum) {
    int pass_count = 0;
    for (int index = 0; index < count; ++index) {
        const pid_t child = fork();
        if (child == 0) {
            _exit(0);
        }
        if (child < 0) {
            return pass_count;
        }
        int status = 0;
        if (waitpid(child, &status, 0) < 0) {
            return pass_count;
        }
        if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
            return pass_count;
        }
        *checksum += (uint64_t)child;
        ++pass_count;
    }
    return pass_count;
}

int main(int argc, char** argv) {
    const char* mode = argc > 1 ? argv[1] : "stat";
    const int default_count = strcmp(mode, "spawn") == 0 ? 20 : 1000;
    const int count = parse_count(argc > 2 ? argv[2] : NULL, default_count, 1, 10000);
    const char* path = argc > 3 ? argv[3] : "/etc/os-release";
    uint64_t checksum = 0;

    const long long started_us = now_us();
    int pass_count = 0;
    if (strcmp(mode, "stat") == 0) {
        pass_count = bench_stat(path, count, &checksum);
    } else if (strcmp(mode, "openread") == 0) {
        pass_count = bench_open_read_close(path, count, &checksum);
    } else if (strcmp(mode, "spawn") == 0) {
        pass_count = bench_spawn(count, &checksum);
    } else {
        fprintf(stderr, "unknown mode: %s\n", mode);
        return 2;
    }
    const long long elapsed_us = now_us() - started_us;
    const int success = pass_count == count;

    printf("ALR SYSCALL BENCH: %s\n", success ? "PASS" : "FAIL");
    printf("alr syscall bench mode=%s\n", mode);
    printf("alr syscall bench path=%s\n", strcmp(mode, "spawn") == 0 ? "none" : path);
    printf("alr syscall bench requested count=%d\n", count);
    printf("alr syscall bench pass count=%d\n", pass_count);
    printf("alr syscall bench elapsed us=%lld\n", elapsed_us);
    printf("alr syscall bench average us=%lld\n", count > 0 ? elapsed_us / count : 0);
    printf("alr syscall bench checksum=%llu\n", (unsigned long long)checksum);
    if (!success) {
        printf("alr syscall bench errno=%d\n", errno);
        printf("alr syscall bench strerror=%s\n", strerror(errno));
    }
    return success ? 0 : 1;
}
