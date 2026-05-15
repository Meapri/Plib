#define _GNU_SOURCE

#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static const char* alr_rewrite_path(const char* path, char* buffer, size_t buffer_size) {
    const char* rootfs = getenv("ALR_ROOTFS");
    if (path == NULL || path[0] != '/' || rootfs == NULL || rootfs[0] == '\0') {
        return path;
    }
    if (strncmp(path, rootfs, strlen(rootfs)) == 0) {
        return path;
    }
    if (strncmp(path, "/proc/", 6) == 0 || strncmp(path, "/dev/", 5) == 0) {
        return path;
    }
    const int written = snprintf(buffer, buffer_size, "%s%s", rootfs, path);
    if (written <= 0 || (size_t)written >= buffer_size) {
        return path;
    }
    return buffer;
}

static int has_mode_arg(int flags) {
    return (flags & O_CREAT) != 0;
}

int open(const char* path, int flags, ...) {
    char rewritten[4096];
    const char* target = alr_rewrite_path(path, rewritten, sizeof(rewritten));
    if (has_mode_arg(flags)) {
        va_list args;
        va_start(args, flags);
        const mode_t mode = (mode_t)va_arg(args, int);
        va_end(args);
        return (int)syscall(SYS_openat, AT_FDCWD, target, flags, mode);
    }
    return (int)syscall(SYS_openat, AT_FDCWD, target, flags);
}

int open64(const char* path, int flags, ...) {
    char rewritten[4096];
    const char* target = alr_rewrite_path(path, rewritten, sizeof(rewritten));
    if (has_mode_arg(flags)) {
        va_list args;
        va_start(args, flags);
        const mode_t mode = (mode_t)va_arg(args, int);
        va_end(args);
        return (int)syscall(SYS_openat, AT_FDCWD, target, flags, mode);
    }
    return (int)syscall(SYS_openat, AT_FDCWD, target, flags);
}

int openat(int dirfd, const char* path, int flags, ...) {
    char rewritten[4096];
    const char* target = alr_rewrite_path(path, rewritten, sizeof(rewritten));
    if (has_mode_arg(flags)) {
        va_list args;
        va_start(args, flags);
        const mode_t mode = (mode_t)va_arg(args, int);
        va_end(args);
        return (int)syscall(SYS_openat, dirfd, target, flags, mode);
    }
    return (int)syscall(SYS_openat, dirfd, target, flags);
}

int openat64(int dirfd, const char* path, int flags, ...) {
    char rewritten[4096];
    const char* target = alr_rewrite_path(path, rewritten, sizeof(rewritten));
    if (has_mode_arg(flags)) {
        va_list args;
        va_start(args, flags);
        const mode_t mode = (mode_t)va_arg(args, int);
        va_end(args);
        return (int)syscall(SYS_openat, dirfd, target, flags, mode);
    }
    return (int)syscall(SYS_openat, dirfd, target, flags);
}

static int alr_stat_path(const char* path, struct stat* st) {
    char rewritten[4096];
    return (int)syscall(SYS_newfstatat, AT_FDCWD, alr_rewrite_path(path, rewritten, sizeof(rewritten)), st, 0);
}

static int alr_lstat_path(const char* path, struct stat* st) {
    char rewritten[4096];
    return (int)syscall(SYS_newfstatat, AT_FDCWD, alr_rewrite_path(path, rewritten, sizeof(rewritten)), st, AT_SYMLINK_NOFOLLOW);
}

static int alr_fstatat_path(int dirfd, const char* path, struct stat* st, int flags) {
    char rewritten[4096];
    return (int)syscall(SYS_newfstatat, dirfd, alr_rewrite_path(path, rewritten, sizeof(rewritten)), st, flags);
}

int stat(const char* path, struct stat* st) {
    return alr_stat_path(path, st);
}

int stat64(const char* path, struct stat64* st) {
    return alr_stat_path(path, (struct stat*)st);
}

int lstat(const char* path, struct stat* st) {
    return alr_lstat_path(path, st);
}

int lstat64(const char* path, struct stat64* st) {
    return alr_lstat_path(path, (struct stat*)st);
}

int fstatat(int dirfd, const char* path, struct stat* st, int flags) {
    return alr_fstatat_path(dirfd, path, st, flags);
}

int fstatat64(int dirfd, const char* path, struct stat64* st, int flags) {
    return alr_fstatat_path(dirfd, path, (struct stat*)st, flags);
}

int __xstat(int version, const char* path, struct stat* st) {
    (void)version;
    return alr_stat_path(path, st);
}

int __xstat64(int version, const char* path, struct stat64* st) {
    (void)version;
    return alr_stat_path(path, (struct stat*)st);
}

int __lxstat(int version, const char* path, struct stat* st) {
    (void)version;
    return alr_lstat_path(path, st);
}

int __lxstat64(int version, const char* path, struct stat64* st) {
    (void)version;
    return alr_lstat_path(path, (struct stat*)st);
}

int __fxstatat(int version, int dirfd, const char* path, struct stat* st, int flags) {
    (void)version;
    return alr_fstatat_path(dirfd, path, st, flags);
}

int __fxstatat64(int version, int dirfd, const char* path, struct stat64* st, int flags) {
    (void)version;
    return alr_fstatat_path(dirfd, path, (struct stat*)st, flags);
}
