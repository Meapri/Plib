#define _GNU_SOURCE

#include <dirent.h>
#include <fcntl.h>
#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#define ALR_RTLD_NEXT ((void*)-1L)

extern void* dlsym(void* handle, const char* symbol);

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

static const char* alr_unrewrite_cwd(const char* cwd, char* buffer, size_t buffer_size) {
    const char* rootfs = getenv("ALR_ROOTFS");
    if (cwd == NULL || rootfs == NULL || rootfs[0] == '\0') {
        return cwd;
    }
    const size_t rootfs_len = strlen(rootfs);
    if (strncmp(cwd, rootfs, rootfs_len) != 0 || (cwd[rootfs_len] != '\0' && cwd[rootfs_len] != '/')) {
        return cwd;
    }
    const char* guest_cwd = cwd + rootfs_len;
    if (guest_cwd[0] == '\0') {
        guest_cwd = "/";
    }
    const int written = snprintf(buffer, buffer_size, "%s", guest_cwd);
    if (written <= 0 || (size_t)written >= buffer_size) {
        errno = ERANGE;
        return NULL;
    }
    return buffer;
}

static DIR* (*alr_real_opendir(void))(const char*) {
    static DIR* (*real_opendir)(const char*) = NULL;
    if (real_opendir == NULL) {
        real_opendir = (DIR* (*)(const char*))dlsym(ALR_RTLD_NEXT, "opendir");
    }
    return real_opendir;
}

static FILE* (*alr_real_fopen(void))(const char*, const char*) {
    static FILE* (*real_fopen)(const char*, const char*) = NULL;
    if (real_fopen == NULL) {
        real_fopen = (FILE* (*)(const char*, const char*))dlsym(ALR_RTLD_NEXT, "fopen");
    }
    return real_fopen;
}

static FILE* (*alr_real_fopen64(void))(const char*, const char*) {
    static FILE* (*real_fopen64)(const char*, const char*) = NULL;
    if (real_fopen64 == NULL) {
        real_fopen64 = (FILE* (*)(const char*, const char*))dlsym(ALR_RTLD_NEXT, "fopen64");
    }
    return real_fopen64;
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

int access(const char* path, int mode) {
    char rewritten[4096];
    return (int)syscall(SYS_faccessat, AT_FDCWD, alr_rewrite_path(path, rewritten, sizeof(rewritten)), mode);
}

int faccessat(int dirfd, const char* path, int mode, int flags) {
    char rewritten[4096];
    const char* target = alr_rewrite_path(path, rewritten, sizeof(rewritten));
#ifdef SYS_faccessat2
    return (int)syscall(SYS_faccessat2, dirfd, target, mode, flags);
#else
    if (flags != 0) {
        errno = ENOSYS;
        return -1;
    }
    return (int)syscall(SYS_faccessat, dirfd, target, mode);
#endif
}

ssize_t readlink(const char* path, char* buffer, size_t buffer_size) {
    char rewritten[4096];
    return (ssize_t)syscall(SYS_readlinkat, AT_FDCWD, alr_rewrite_path(path, rewritten, sizeof(rewritten)), buffer, buffer_size);
}

ssize_t readlinkat(int dirfd, const char* path, char* buffer, size_t buffer_size) {
    char rewritten[4096];
    return (ssize_t)syscall(SYS_readlinkat, dirfd, alr_rewrite_path(path, rewritten, sizeof(rewritten)), buffer, buffer_size);
}

int chdir(const char* path) {
    char rewritten[4096];
    return (int)syscall(SYS_chdir, alr_rewrite_path(path, rewritten, sizeof(rewritten)));
}

char* getcwd(char* buffer, size_t size) {
    char host_cwd[4096];
    const long rc = syscall(SYS_getcwd, host_cwd, sizeof(host_cwd));
    if (rc < 0) {
        return NULL;
    }
    char guest_cwd[4096];
    const char* translated = alr_unrewrite_cwd(host_cwd, guest_cwd, sizeof(guest_cwd));
    if (translated == NULL) {
        return NULL;
    }
    const size_t needed = strlen(translated) + 1;
    if (buffer == NULL) {
        const size_t allocation_size = size == 0 || size < needed ? needed : size;
        buffer = (char*)malloc(allocation_size);
        if (buffer == NULL) {
            errno = ENOMEM;
            return NULL;
        }
        size = allocation_size;
    }
    if (needed > size) {
        errno = ERANGE;
        return NULL;
    }
    memcpy(buffer, translated, needed);
    return buffer;
}

DIR* opendir(const char* path) {
    char rewritten[4096];
    DIR* (*real_opendir)(const char*) = alr_real_opendir();
    if (real_opendir == NULL) {
        errno = ENOSYS;
        return NULL;
    }
    return real_opendir(alr_rewrite_path(path, rewritten, sizeof(rewritten)));
}

FILE* fopen(const char* path, const char* mode) {
    char rewritten[4096];
    FILE* (*real_fopen)(const char*, const char*) = alr_real_fopen();
    if (real_fopen == NULL) {
        errno = ENOSYS;
        return NULL;
    }
    return real_fopen(alr_rewrite_path(path, rewritten, sizeof(rewritten)), mode);
}

FILE* fopen64(const char* path, const char* mode) {
    char rewritten[4096];
    FILE* (*real_fopen64)(const char*, const char*) = alr_real_fopen64();
    if (real_fopen64 == NULL) {
        errno = ENOSYS;
        return NULL;
    }
    return real_fopen64(alr_rewrite_path(path, rewritten, sizeof(rewritten)), mode);
}
