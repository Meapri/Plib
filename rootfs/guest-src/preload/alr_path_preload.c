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

static char* (*alr_real_realpath(void))(const char*, char*);

static int alr_has_path_prefix(const char* path, const char* prefix) {
    if (path == NULL || prefix == NULL || prefix[0] == '\0') {
        return 0;
    }
    const size_t prefix_len = strlen(prefix);
    return strncmp(path, prefix, prefix_len) == 0 && (path[prefix_len] == '\0' || path[prefix_len] == '/');
}

static const char* alr_canonical_rootfs(void) {
    static char canonical_rootfs[4096];
    static int canonical_checked = 0;
    if (canonical_checked) {
        return canonical_rootfs[0] == '\0' ? NULL : canonical_rootfs;
    }
    canonical_checked = 1;

    const char* rootfs = getenv("ALR_ROOTFS");
    char* (*real_realpath)(const char*, char*) = alr_real_realpath();
    if (rootfs == NULL || rootfs[0] == '\0' || real_realpath == NULL) {
        return NULL;
    }
    char resolved[4096];
    if (real_realpath(rootfs, resolved) == NULL) {
        return NULL;
    }
    const int written = snprintf(canonical_rootfs, sizeof(canonical_rootfs), "%s", resolved);
    if (written <= 0 || (size_t)written >= sizeof(canonical_rootfs)) {
        canonical_rootfs[0] = '\0';
        return NULL;
    }
    return canonical_rootfs;
}

static const char* alr_rewrite_path(const char* path, char* buffer, size_t buffer_size) {
    const char* rootfs = getenv("ALR_ROOTFS");
    if (path == NULL || path[0] != '/' || rootfs == NULL || rootfs[0] == '\0') {
        return path;
    }
    const char* canonical_rootfs = alr_canonical_rootfs();
    if (alr_has_path_prefix(path, rootfs) || alr_has_path_prefix(path, canonical_rootfs)) {
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
    const char* matched_rootfs = NULL;
    if (alr_has_path_prefix(cwd, rootfs)) {
        matched_rootfs = rootfs;
    } else {
        const char* canonical_rootfs = alr_canonical_rootfs();
        if (alr_has_path_prefix(cwd, canonical_rootfs)) {
            matched_rootfs = canonical_rootfs;
        }
    }
    if (matched_rootfs == NULL) {
        return cwd;
    }
    const char* guest_cwd = cwd + strlen(matched_rootfs);
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

static char* (*alr_real_realpath(void))(const char*, char*) {
    static char* (*real_realpath)(const char*, char*) = NULL;
    if (real_realpath == NULL) {
        real_realpath = (char* (*)(const char*, char*))dlsym(ALR_RTLD_NEXT, "realpath");
    }
    return real_realpath;
}

static int (*alr_real_mkstemp(void))(char*) {
    static int (*real_mkstemp)(char*) = NULL;
    if (real_mkstemp == NULL) {
        real_mkstemp = (int (*)(char*))dlsym(ALR_RTLD_NEXT, "mkstemp");
    }
    return real_mkstemp;
}

static int (*alr_real_mkstemp64(void))(char*) {
    static int (*real_mkstemp64)(char*) = NULL;
    if (real_mkstemp64 == NULL) {
        real_mkstemp64 = (int (*)(char*))dlsym(ALR_RTLD_NEXT, "mkstemp64");
    }
    return real_mkstemp64;
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

char* realpath(const char* path, char* resolved_path) {
    char rewritten[4096];
    char* (*real_realpath)(const char*, char*) = alr_real_realpath();
    if (real_realpath == NULL) {
        errno = ENOSYS;
        return NULL;
    }

    char host_resolved[4096];
    char* host_result = real_realpath(
        alr_rewrite_path(path, rewritten, sizeof(rewritten)),
        resolved_path == NULL ? NULL : host_resolved
    );
    if (host_result == NULL) {
        return NULL;
    }

    char guest_resolved[4096];
    const char* translated = alr_unrewrite_cwd(host_result, guest_resolved, sizeof(guest_resolved));
    if (translated == NULL) {
        if (resolved_path == NULL) {
            free(host_result);
        }
        return NULL;
    }
    if (translated == host_result) {
        if (resolved_path == NULL) {
            return host_result;
        }
        const size_t needed = strlen(translated) + 1;
        memcpy(resolved_path, translated, needed);
        return resolved_path;
    }

    const size_t needed = strlen(translated) + 1;
    if (resolved_path == NULL) {
        char* guest_result = (char*)malloc(needed);
        if (guest_result == NULL) {
            free(host_result);
            errno = ENOMEM;
            return NULL;
        }
        memcpy(guest_result, translated, needed);
        free(host_result);
        return guest_result;
    }

    memcpy(resolved_path, translated, needed);
    return resolved_path;
}

char* canonicalize_file_name(const char* path) {
    return realpath(path, NULL);
}

static int alr_mkstemp_path(char* template_path, int (*real_mkstemp)(char*)) {
    if (real_mkstemp == NULL) {
        errno = ENOSYS;
        return -1;
    }
    char rewritten[4096];
    const char* target = alr_rewrite_path(template_path, rewritten, sizeof(rewritten));
    char host_template[4096];
    const int written = snprintf(host_template, sizeof(host_template), "%s", target);
    if (written <= 0 || (size_t)written >= sizeof(host_template)) {
        errno = ENAMETOOLONG;
        return -1;
    }

    const int fd = real_mkstemp(host_template);
    if (fd < 0) {
        return fd;
    }

    char guest_template[4096];
    const char* translated = alr_unrewrite_cwd(host_template, guest_template, sizeof(guest_template));
    if (translated == NULL) {
        close(fd);
        return -1;
    }
    strcpy(template_path, translated);
    return fd;
}

int mkstemp(char* template_path) {
    return alr_mkstemp_path(template_path, alr_real_mkstemp());
}

int mkstemp64(char* template_path) {
    return alr_mkstemp_path(template_path, alr_real_mkstemp64());
}

int rename(const char* old_path, const char* new_path) {
    char rewritten_old[4096];
    char rewritten_new[4096];
    return (int)syscall(
        SYS_renameat,
        AT_FDCWD,
        alr_rewrite_path(old_path, rewritten_old, sizeof(rewritten_old)),
        AT_FDCWD,
        alr_rewrite_path(new_path, rewritten_new, sizeof(rewritten_new))
    );
}

int renameat(int old_dirfd, const char* old_path, int new_dirfd, const char* new_path) {
    char rewritten_old[4096];
    char rewritten_new[4096];
    return (int)syscall(
        SYS_renameat,
        old_dirfd,
        alr_rewrite_path(old_path, rewritten_old, sizeof(rewritten_old)),
        new_dirfd,
        alr_rewrite_path(new_path, rewritten_new, sizeof(rewritten_new))
    );
}

#ifdef SYS_renameat2
int renameat2(int old_dirfd, const char* old_path, int new_dirfd, const char* new_path, unsigned int flags) {
    char rewritten_old[4096];
    char rewritten_new[4096];
    return (int)syscall(
        SYS_renameat2,
        old_dirfd,
        alr_rewrite_path(old_path, rewritten_old, sizeof(rewritten_old)),
        new_dirfd,
        alr_rewrite_path(new_path, rewritten_new, sizeof(rewritten_new)),
        flags
    );
}
#endif
