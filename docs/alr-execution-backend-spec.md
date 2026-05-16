# ALR Execution Backend Specification

## Status

Draft v0.1. This document defines the target open execution backend that will grow beyond the existing path/env skeleton. It intentionally starts smaller than a full proroot-class runtime and expands only when tests prove each layer.

## Objectives

- Execute Linux/glibc arm64 userland from an app-private rootfs on stock Android.
- Avoid direct execution of writable app-data rootfs binaries as the first process entrypoint.
- Reduce common syscall and process-management overhead below ptrace-heavy PRoot.
- Preserve predictable fallback to packaged PRoot.
- Keep implementation clean-room and test-driven.

## Component Model

```text
Android Activity / Service
  gathers package paths, permissions, Surface, user action
        |
        v
libalr_runtime_launcher.so
  Android/Bionic packaged executable or shared-object entrypoint
  prepares config, env, fds, rootfs paths
        |
        v
guest dynamic loader path
  initially use the guest rootfs loader where possible
  later add libalr_runtime_linker.so only if required
        |
        v
libalr_runtime_hook.so
  glibc LD_PRELOAD mediation layer
        |
        v
guest program
  shell, CLI tools, GUI clients, package tools
```

Optional later:

```text
libalr_runtime_bridge.so
  preserves runtime config across execve/posix_spawn

libalr_runtime_patch.so or patch module
  handles direct raw syscalls only after wrapper coverage is measured insufficient
```

## Versioned Scope

### ALR Exec v0: Existing Skeleton

Already present:

- Lexical guest path normalization.
- Rootfs host path translation.
- Minimal deterministic guest environment helper.
- Host-native C++ tests.

### ALR Exec v1: Preload Filesystem MVP

Required wrappers:

- `open`
- `open64`
- `openat`
- `openat64`
- `access`
- `faccessat`
- `stat`
- `stat64`
- `lstat`
- `lstat64`
- `fstatat`
- `newfstatat` where exposed
- `statx`
- `readlink`
- `readlinkat`
- `mkdir`
- `mkdirat`
- `unlink`
- `unlinkat`
- `rename`
- `renameat`
- `renameat2`
- `symlink`
- `symlinkat`
- `chmod`
- `fchmodat`
- `chown`
- `lchown`
- `fchownat`
- `utimensat`
- `opendir`
- `realpath`
- `getcwd`
- `chdir`
- `fchdir`

Acceptance:

```text
ALR PATH NORMALIZATION: PASS
ALR OPEN READ ROOTFS FILE: PASS
ALR STAT ROOTFS FILE: PASS
ALR READLINK PROC SELF EXE: PASS
ALR CWD TRANSLATION: PASS
ALR BASIC SHELL FILE OPS: PASS
```

### ALR Exec v2: Process Continuity

Required behavior:

- `execve` translates guest path to host executable or loader plan.
- `execvp` and `execvpe` perform guest `PATH` lookup.
- `posix_spawn` and `posix_spawnp` preserve runtime environment.
- Child process inherits rootfs config through environment and/or config fd.
- Android host environment is not leaked by default.
- Guest `argv[0]` remains guest-visible where practical.

Acceptance:

```text
ALR EXECVE HELLO: PASS
ALR EXECVP PATH LOOKUP: PASS
ALR POSIX_SPAWN CHILD: PASS
ALR SHELL -C CHILD: PASS
CLEAN GUEST ENVIRONMENT: PASS
```

Performance evidence:

- Report ALR and PRoot elapsed time for static hello and dynamic glibc hello on the same device run.
- Report ALR/PRoot elapsed ratio as an integer percent so regressions are visible without reading verbose logs.
- Compare ALR repeated handoff average against single PRoot sessions that loop the same static hello and dynamic glibc hello commands. This reduces launcher noise and better exposes steady execution overhead.
- Report a native Bionic fork/wait microbaseline from the packaged executable so ALR/PRoot measurements can be read against a device-local native process-control floor.
- Treat `ALR LOOP HOT PATH PERF EVIDENCE: PASS` as a device-local signal only when both static and dynamic repeated handoff probes are faster than their PRoot loop baselines in that run. This is not a universal claim until repeated across device classes.

Current V54 device snapshot:

```text
native bionic fork benchmark average us=native fork repeat average elapsed us=953
alr static handoff benchmark average ms=alr handoff repeat average elapsed ms=4
proot static hello loop benchmark average ms=10
alr static handoff vs native fork ratio pct=419
alr static handoff vs proot loop ratio pct=40
alr dynamic glibc handoff benchmark average ms=alr handoff repeat average elapsed ms=4
proot dynamic glibc loop benchmark average ms=20
alr dynamic glibc handoff vs native fork ratio pct=419
alr dynamic glibc handoff vs proot loop ratio pct=20
alr loop hot path perf evidence=PASS
```

Current V55 syscall-heavy snapshot:

```text
ALR SYSCALL STAT BENCH EXECUTION: PASS
ALR SYSCALL OPENREAD BENCH EXECUTION: PASS
ALR SYSCALL SPAWN BENCH EXECUTION: PASS
PROOT SYSCALL STAT BENCH EXECUTION: PASS
PROOT SYSCALL OPENREAD BENCH EXECUTION: PASS
PROOT SYSCALL SPAWN BENCH EXECUTION: PASS
alr syscall stat benchmark average us=2760
proot syscall stat benchmark average us=283
alr syscall stat vs proot ratio pct=975
alr syscall openread benchmark average us=8073
proot syscall openread benchmark average us=214
alr syscall openread vs proot ratio pct=3772
alr syscall spawn benchmark average us=12928
proot syscall spawn benchmark average us=1593
alr syscall spawn vs proot ratio pct=811
alr syscall hot path perf evidence=NEEDS_WORK
```

The v55 result proves execution coverage but contradicts any broad claim that
the current ALR syscall path is already below PRoot. The next backend iteration
must reduce path-rewrite trapping cost or move common filesystem operations out
of the global ptrace stop/resume path.

Current V56 preload path-fastpath snapshot:

```text
build: 0.4.56-preload-path-fastpath
ALR SYSCALL STAT PRELOAD BENCH EXECUTION: FAIL
ALR SYSCALL OPENREAD PRELOAD BENCH EXECUTION: PASS
alr syscall stat benchmark average us=2632
proot syscall stat benchmark average us=258
alr syscall stat preload benchmark average us=unavailable
alr syscall openread benchmark average us=8010
proot syscall openread benchmark average us=201
alr syscall openread preload benchmark average us=6
alr syscall openread preload vs proot ratio pct=2
alr syscall openread preload faster than proot=true
alr syscall preload hot path measured faster count=1/2
alr syscall preload hot path perf evidence=INCOMPLETE
```

The v56 open/read preload result establishes the preferred hot-path direction:
when libc-facing filesystem calls can be handled inside the guest process and
forwarded through direct syscalls, the runtime avoids global ptrace stop/resume
overhead and can outperform the measured PRoot baseline by a wide margin. This
does not yet complete the syscall backend. The stat preload path currently
fails and must not be counted as a supported or faster fast path until fixed.

Current V57 preload stat-fastpath snapshot:

```text
build: 0.4.57-preload-stat-fastpath
ALR SYSCALL STAT PRELOAD BENCH EXECUTION: PASS
ALR SYSCALL OPENREAD PRELOAD BENCH EXECUTION: PASS
alr syscall stat benchmark average us=2471
proot syscall stat benchmark average us=251
alr syscall stat preload benchmark average us=2
alr syscall stat preload vs proot ratio pct=0
alr syscall stat preload faster than proot=true
alr syscall openread benchmark average us=7898
proot syscall openread benchmark average us=207
alr syscall openread preload benchmark average us=7
alr syscall openread preload vs proot ratio pct=3
alr syscall openread preload faster than proot=true
alr syscall preload hot path measured faster count=2/2
alr syscall preload hot path perf evidence=PASS
```

The v57 result extends the preload path to glibc compatibility entry points
such as `__xstat`, `__xstat64`, `__fxstatat`, and `__fxstatat64`. This makes
the measured stat and open/read preload paths both faster than PRoot on the
test device. The ptrace path remains a fallback/discovery path, not the desired
interactive hot path.

Current V58 preload fsmeta-fastpath snapshot:

```text
build: 0.4.58-preload-fsmeta-fastpath
ALR SYSCALL FSMETA BENCH EXECUTION: PASS
ALR SYSCALL STAT PRELOAD BENCH EXECUTION: PASS
ALR SYSCALL OPENREAD PRELOAD BENCH EXECUTION: PASS
ALR SYSCALL FSMETA PRELOAD BENCH EXECUTION: PASS
PROOT SYSCALL FSMETA BENCH EXECUTION: PASS
proot syscall stat benchmark average us=79
alr syscall stat preload benchmark average us=2
proot syscall openread benchmark average us=202
alr syscall openread preload benchmark average us=7
alr syscall fsmeta benchmark average us=5354
proot syscall fsmeta benchmark average us=173
alr syscall fsmeta preload benchmark average us=9
alr syscall fsmeta preload vs proot ratio pct=5
alr syscall fsmeta preload faster than proot=true
alr syscall preload hot path measured faster count=3/3
alr syscall preload hot path perf evidence=PASS
```

The v58 result adds `access` and `readlink` coverage to the measured preload
hot path and proves it against an extracted rootfs symlink fixture. Safe
relative symlink extraction is now part of the rootfs installer contract; hard
links and escaping symlink targets remain rejected.

Current V59 preload userland-dpkg snapshot:

```text
build: 0.4.59-preload-userland-dpkg
ALR DPKG ARCH PRELOAD EXECUTION: PASS
alr dpkg --print-architecture preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr dpkg --print-architecture preload path rewrite=alr handoff path rewrite count=0
alr dpkg --print-architecture preload stdout=arm64
alr syscall preload hot path measured faster count=3/3
alr syscall preload hot path perf evidence=PASS
```

The v59 result proves the preload filesystem layer is no longer bench-only:
`dpkg --print-architecture` can run as a real glibc program with `LD_PRELOAD`
and without ALR's global ptrace path rewrite loop. Directory iteration remains
the next required preload surface for broader `apt`/package-manager commands.

Current V60 preload apt-config snapshot:

```text
build: 0.4.60-preload-aptconfig
ALR APT-CONFIG PRELOAD EXECUTION: PASS
alr apt-config --version preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr apt-config --version preload path rewrite=alr handoff path rewrite count=0
alr apt-config --version preload stdout=apt 2.8.3 (arm64)
alr syscall preload hot path measured faster count=3/3
alr syscall preload hot path perf evidence=PASS
```

The v60 result adds `opendir`, `fopen`, and `fopen64` coverage to the preload
filesystem layer and proves that a real apt-family glibc command can execute
with ptrace path rewriting disabled. The rootfs now includes a
`libdl.so.2 -> libc.so.6` compatibility symlink required by the preload shim's
source-built `dlsym` path on the packaged Debian userland.

Current V61 preload apt-family snapshot:

```text
build: 0.4.61-preload-apt-family
ALR APT PRELOAD EXECUTION: PASS
ALR APT-GET PRELOAD EXECUTION: PASS
ALR APT-CACHE PRELOAD EXECUTION: PASS
ALR APT-CONFIG PRELOAD EXECUTION: PASS
alr apt --version preload path rewrite=alr handoff path rewrite count=0
alr apt --version preload stdout=apt 2.8.3 (arm64)
alr apt-get --version preload path rewrite=alr handoff path rewrite count=0
alr apt-get --version preload stdout=apt 2.8.3 (arm64)
alr apt-cache --version preload path rewrite=alr handoff path rewrite count=0
alr apt-cache --version preload stdout=apt 2.8.3 (arm64)
alr apt-config --version preload path rewrite=alr handoff path rewrite count=0
alr apt-config --version preload stdout=apt 2.8.3 (arm64)
alr syscall preload hot path measured faster count=3/3
alr syscall preload hot path perf evidence=PASS
```

The v61 snapshot promotes the apt-family preload path from direct probing into
the app's standard execution report. It verifies that four package-manager
frontends can run without ALR's global ptrace path rewrite loop, narrowing the
remaining work toward deeper package-manager operations such as list reads,
cache policy, downloads, unpack/configure flows, and maintainer-script process
trees.

Current V64 preload apt-cache state snapshot:

```text
build: 0.4.64-preload-apt-cache-state
ALR APT-CACHE POLICY PRELOAD EXECUTION: PASS
ALR APT-CACHE STATS PRELOAD EXECUTION: PASS
ALR APT-CACHE PKGNAMES PRELOAD EXECUTION: PASS
alr apt-cache policy preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr apt-cache policy preload path rewrite=alr handoff path rewrite count=0
alr apt-cache policy preload stdout=Package files:
 100 /var/lib/dpkg/status
     release a=now
Pinned packages:
alr apt-cache stats preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr apt-cache stats preload path rewrite=alr handoff path rewrite count=0
alr apt-cache stats preload stdout=Total package names: 0 (0 )
alr apt-cache pkgnames preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr apt-cache pkgnames preload path rewrite=alr handoff path rewrite count=0
alr syscall preload hot path measured faster count=3/3
alr syscall preload hot path perf evidence=PASS
```

The v64 snapshot adds broader package-manager cache/state behavior to the
preload path. The hooks cover apt's `realpath` canonicalization of dpkg status
files, `mkstemp` cache temp-file creation, and `rename` cache commit path while
keeping the ALR global path-rewrite loop disabled for policy, stats, and
package-name cache queries.

Current V65 preload dpkg install snapshot:

```text
build: 0.4.65-preload-dpkg-install
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
alr dpkg -i local deb preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr dpkg -i local deb preload execve loader rewrites=alr handoff execve loader rewrite count=5
alr dpkg -i local deb preload traced processes=alr handoff traced process count=10
alr dpkg -i local deb preload stdout=Selecting previously unselected package alr-smoke.\n(Reading database ... 0 files and directories currently installed.)\nPreparing to unpack .../alr-smoke_1.0_arm64.deb ...\nUnpacking alr-smoke (1.0) ...\nSetting up alr-smoke (1.0) ...
```

The v65 snapshot proves a local `.deb` install through the hybrid low-overhead
path: preload handles filesystem/fakeroot hot paths while ALR tracing remains
available for the colder helper `execve` chain and loader rewrite. This is the
first package-manager mutation evidence that completes successfully inside the
normal non-root Android app sandbox.

Current V67 installed package preload snapshot:

```text
build: 0.4.67-preload-installed-package
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
alr installed package preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package preload stdout=alr local deb package smoke ok\nALR_SMOKE_PACKAGE_SCRIPT=1
```

The v67 snapshot proves installed package entrypoint execution after the local
dpkg mutation. The smoke package now separates package-entrypoint execution from
nested package-manager child execution, and the ALR handoff path has shebang
script detection so future script entrypoints can be routed through the guest
interpreter instead of being presented to the glibc ELF loader as if they were
ELF binaries.

Current V68 shell child exec snapshot:

```text
build: 0.4.68-preload-shell-child-exec
ALR SHELL DPKG ARCH PRELOAD EXECUTION: PASS
alr shell dpkg --print-architecture preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr shell dpkg --print-architecture preload execve attempts=alr handoff execve attempt count=1
alr shell dpkg --print-architecture preload execve loader rewrites=alr handoff execve loader rewrite count=1
alr shell dpkg --print-architecture preload traced processes=alr handoff traced process count=2
alr shell dpkg --print-architecture preload stdout=arm64
```

The v68 snapshot proves a shell wrapper can spawn a glibc child binary inside
the Android app process. The key Android-specific fix is seccomp emulation for
guest `faccessat2` PATH probing: the handoff layer now maps host-rootfs PATH
entries back to guest paths and answers access checks from rootfs file mode
metadata instead of Android host access policy.

Current V69 installed package child exec snapshot:

```text
build: 0.4.69-preload-package-child-exec
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
ALR SHELL DPKG ARCH PRELOAD EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
alr installed package preload execve attempts=alr handoff execve attempt count=1
alr installed package preload execve loader rewrites=alr handoff execve loader rewrite count=1
alr installed package preload traced processes=alr handoff traced process count=3
alr installed package preload last exec requested=alr handoff last exec requested path=/usr/bin/dpkg
alr installed package preload stdout=alr local deb package smoke ok\nALR_SMOKE_PACKAGE_SCRIPT=1\nALR_SMOKE_ARCH=arm64
```

The v69 snapshot connects the v68 shell child exec support back into an
installed package entrypoint. A package-installed shell script can now spawn a
glibc child program through PATH lookup, have ALR rewrite that child `execve`
through the glibc loader, and return the child result through the script while
still running inside the non-root Android app sandbox.

Current V70 env child chain snapshot:

```text
build: 0.4.70-preload-env-child-chain
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
ALR SHELL DPKG ARCH PRELOAD EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
alr installed package preload execve attempts=alr handoff execve attempt count=6
alr installed package preload execve loader rewrites=alr handoff execve loader rewrite count=3
alr installed package preload traced processes=alr handoff traced process count=5
alr installed package preload stdout=alr local deb package smoke ok\nALR_SMOKE_PACKAGE_SCRIPT=1\nALR_SMOKE_ARCH=arm64\nALR_SMOKE_ENV_ARCH=arm64
```

The v70 snapshot extends installed-package execution from a single nested child
to an env-mediated child chain. The package script now invokes both direct
`dpkg --print-architecture` and `/usr/bin/env dpkg --print-architecture`, proving
that ALR/preload can preserve the runtime across script launch, PATH lookup,
env dispatch, and the final glibc child exec.

### ALR Exec v3: Identity and Procfs

Required behavior:

- Optional fakeroot mode returns uid/gid 0 for common identity calls.
- `stat` and `statx` metadata can be patched for root-looking ownership where enabled.
- Minimal `/proc/self`, `/proc/thread-self`, `/proc/mounts`, `/proc/self/exe`, `/proc/self/status`, `/proc/self/cmdline`, and `/proc/self/environ` are virtualized or safely passed through.
- Android app-private host paths are not unnecessarily exposed to guest tools.

Acceptance:

```text
ALR FAKE ROOT IDENTITY: PASS
ALR PROC SELF EXE: PASS
ALR PROC MOUNTS ROOTFS: PASS
ALR PROC STATUS SANITIZED: PASS
```

### ALR Exec v4: Package-Manager Preflight

Required behavior:

- `dpkg --version` passes.
- `dpkg --print-architecture` passes.
- `dpkg-query --version` passes.
- `apt --version`, `apt-get --version`, `apt-cache --version`, and `apt-config --version` pass.
- Minimal device binds such as `/dev/null`, `/dev/zero`, and `/dev/urandom` work.
- Local `.deb` install passes for the smoke package, including helper `execve`, `dpkg-deb`, `tar`, status-file backup, fakeroot ownership, timestamp, and metadata operations.

Acceptance:

```text
ALR DPKG VERSION: PASS
ALR DPKG ARCH: PASS
ALR APT VERSION: PASS
ALR DPKG LOCAL INSTALL: PASS
```

Implementation rule:

- Treat package-manager mutation as a cold compatibility path. It may use ptrace-backed mediation while ALR is still discovering required Linux filesystem semantics.
- Do not let this cold path define the long-term GUI/runtime hot path. Interactive Linux app execution must move toward preload/fd-broker/native bridge coverage where ordinary syscalls run without global ptrace stop/resume.
- Keep PRoot and Termux-derived behavior as compatibility baselines, not performance targets.
- Use proroot-class evidence to decide which Linux ABI seams matter, then implement only the necessary open ALR behavior.
- Use Termux as Android packaging and app-UID prior art: writable app-private prefixes, Android-friendly helper placement, no assumption that `chown`, hardlinks, mounts, or FHS paths behave like a privileged Linux system.

### ALR Exec v5: Direct Syscall Coverage

Only after v1-v4 evidence shows wrapper bypass:

- Detect direct raw syscall sites in open-source guest libraries through public source or runtime behavior.
- Add selective instrumentation, seccomp user-notification experiments, or clean-room patching.
- Keep patching isolated and heavily tested.

Non-goal for v5:

- Full arbitrary binary transparency.
- Closed-runtime patch algorithm parity.

## Rootfs Model

Inputs:

- `rootfs_dir`: absolute host path under app-private storage.
- `guest_cwd`: absolute guest path.
- `guest_path`: path from guest call.
- `binds`: optional mapping list.

Rules:

- All guest absolute paths start at guest `/`.
- Relative paths resolve against guest cwd.
- `.` is removed.
- `..` clamps at guest root.
- No lexical traversal may escape `rootfs_dir`.
- Host symlinks are not resolved during lexical translation.
- Existence checks occur only after translation.
- Embedded NUL is invalid.

Bind mapping:

```text
host_path:guest_path
```

Rules:

- Longest guest-prefix match wins.
- Guest bind paths must be absolute and normalized.
- Host bind paths must be absolute.
- Bind paths are explicit escape hatches and must be visible in diagnostics.

## Environment Model

Minimal guest environment:

```text
ALR_PACKAGE=<android package>
ALR_ROOTFS=<host rootfs path>
ALR_PROGRAM=<guest program path>
ALR_BACKEND=alr-runtime
HOME=/root
TMPDIR=/tmp
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
```

Optional backend environment:

```text
ALR_CONFIG_FD=<fd>
ALR_BRIDGE_PATH=<host path>
ALR_HOOK_PATH=<host path>
ALR_VERBOSE=0|1|2
ALR_TRACE_PATH=0|1
ALR_TRACE_EXEC=0|1
ALR_FAKE_ROOT=0|1
```

Sanitization:

- Clear inherited Android environment before guest launch.
- Add only allowlisted variables.
- Do not pass `ANDROID_ROOT`, `ANDROID_DATA`, `BOOTCLASSPATH`, `DEX2OATBOOTCLASSPATH`, `SYSTEMSERVERCLASSPATH`, `ANDROID_SOCKET_*`, `EXTERNAL_STORAGE`, or vendor storage variables unless explicitly required by an Android-host helper.

## Filesystem Behavior

### Open-like Calls

For path-bearing open calls:

1. Validate path string.
2. Resolve guest path using cwd or dirfd model.
3. Apply bind mapping.
4. Translate to host path.
5. Call real syscall or libc function.
6. Preserve errno.
7. Log if tracing is enabled.

Dirfd rules:

- `AT_FDCWD` uses guest cwd.
- Rootfs-relative directory fds require fd-to-guest-path tracking.
- Host-only fds are not assumed to map back to guest paths unless opened through ALR.

### Metadata Calls

For `stat`, `fstatat`, and `statx`:

- Translate path.
- Call host metadata function.
- Patch guest-visible path-dependent metadata where required.
- Apply fakeroot uid/gid patch only when enabled.
- Apply procfs virtual metadata for known virtual proc paths.

### Symlink Calls

- `readlink` returns guest-visible target when target is under rootfs.
- Absolute host rootfs prefixes must be converted back to guest paths.
- Unsupported host symlink escapes are reported as known limitations.

### Hardlink Calls

Initial ALR does not emulate hardlinks beyond host filesystem support.

Later `link2symlink`-style emulation may be added only with a project-owned metadata format:

- Document metadata directory.
- Make original-anchor preservation explicit.
- Add migration and cleanup tests.
- Do not copy closed-runtime metadata formats.

## Process Behavior

### execve

Inputs:

- guest path
- argv
- envp

Rules:

- Program path must resolve inside rootfs or allowed bind.
- Scripts with shebang require guest shebang resolution.
- Dynamic glibc binaries require guest loader strategy.
- Child environment must include ALR runtime state.
- `argv[0]` should remain guest-compatible.

Fallbacks:

- If ALR cannot launch safely, return errno and report reason.
- Do not silently fall back to host shell for guest commands.

### PATH Lookup

For `execvp` and `posix_spawnp`:

- Use guest `PATH`.
- Resolve each candidate lexically inside guest namespace.
- Preserve Linux search semantics as closely as possible.
- Report candidate misses under trace mode.

## Procfs Virtualization

Initial virtual paths:

- `/proc/self/exe`
- `/proc/self/cwd`
- `/proc/self/root`
- `/proc/self/status`
- `/proc/self/cmdline`
- `/proc/self/environ`
- `/proc/mounts`
- `/proc/thread-self`

Rules:

- Prefer small generated virtual files for stable metadata.
- Pass through only paths that are safe and do not expose confusing Android internals.
- Make unsupported procfs leaves return Linux-compatible errors where possible.

## Networking and IPC

Initial ALR does not virtualize network namespaces.

Behavior:

- TCP loopback is host Android loopback.
- Guest bridge clients use explicit `ALR_*_BRIDGE_HOST` and port variables.
- Unix socket path length issues may require a runtime socket directory under app cache.
- Netlink behavior is unsupported unless explicitly emulated later.

## Error and Report Model

Every device report should include:

```text
backend=<proot|optional-proroot|alr-runtime>
command=<guest command>
exit=<code>
stdout=<trimmed or summarized>
stderr=<trimmed or summarized>
elapsed_ms=<number>
result=<PASS|FAIL|KNOWN_FAIL>
reason=<stable reason>
```

Stable reason examples:

- `unsupported-static-binary`
- `unsupported-direct-syscall`
- `missing-guest-loader`
- `path-escape-rejected`
- `procfs-leaf-unsupported`
- `fakeroot-metadata-mismatch`
- `android-seccomp-denied`
- `timeout`

## Performance Benchmarks

Minimum benchmark set:

- `hello` startup.
- `/bin/sh -c true`.
- 1,000 `stat` calls.
- 1,000 `open/read/close` calls.
- 100 child process spawns.
- `dpkg --print-architecture`.
- Guest GPU client frame stream while command producer is active.

Compare:

- PRoot backend.
- Optional external low-overhead backend if present.
- ALR runtime backend.

Current V71 installed-package GPU IPC snapshot:

```text
build: 0.4.71-installed-package-gpu-ipc
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
INSTALLED PACKAGE EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
ALR GUEST GPU IPC BRIDGE EXECUTION: PASS
ALR INSTALLED PACKAGE GPU IPC EXECUTION: PASS
rootfs installed alr gpu smoke exists=true executable=true bytes=537664
alr installed package gpu ipc received frames=3
alr installed package gpu ipc lossless=true
alr installed package gpu ipc error=none
alr installed package gpu ipc handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package gpu ipc execve attempts=alr handoff execve attempt count=0
alr installed package gpu ipc execve loader rewrites=alr handoff execve loader rewrite count=0
alr installed package gpu ipc stdout=alr guest gpu client ok
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
```

V71 intentionally keeps the regular installed package script smoke as the
preload child-exec proof, and makes the installed GPU entrypoint an ELF copied
from the guest GPU client. This avoids the static-child exec loop observed when
a shell wrapper tried to `exec /usr/bin/alr-gpu-client` through the preload
chain, while still proving that a `.deb` can install a Linux GPU app entrypoint
that ALR launches through the Android-native GPU IPC bridge.

Current V72 installed-package GLES shim snapshot:

```text
build: 0.4.72-installed-package-gles-shim
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
INSTALLED PACKAGE EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
ALR GUEST GPU IPC BRIDGE EXECUTION: PASS
ALR INSTALLED PACKAGE GPU IPC EXECUTION: PASS
ALR GUEST GLES DEMO GEARS EXECUTION: PASS
ALR INSTALLED PACKAGE GLES DEMO EXECUTION: PASS
rootfs installed alr gles demo exists=true executable=true bytes=8264
alr installed package gles demo handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package gles demo stdout=alr guest gles demo gears ok
alr installed package gles demo command parsed count=60
alr installed package gles demo draw command count=60
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=99
```

V72 moves the installed-package proof from a tiny GPU clear client toward a
realer GLES ABI path: the `.deb` installs an ELF copied from the source-built
guest GLES demo, ALR launches it through the guest glibc loader and the
`libEGL.so`/`libGLESv2.so` shim library path, and the Android Surface renderer
consumes its 60 draw commands alongside the native GLES baseline.

Current V73 installed-package GLES IPC snapshot:

```text
build: 0.4.73-installed-gles-ipc
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
INSTALLED PACKAGE EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
ALR GUEST GPU IPC BRIDGE EXECUTION: PASS
ALR INSTALLED PACKAGE GPU IPC EXECUTION: PASS
ALR GUEST GLES DEMO GEARS EXECUTION: PASS
ALR INSTALLED PACKAGE GLES DEMO EXECUTION: PASS
ALR INSTALLED PACKAGE GLES IPC EXECUTION: PASS
alr installed package gles demo handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package gles demo command parsed count=60
alr installed package gles demo draw command count=60
alr installed package gles ipc received frames=60
alr installed package gles ipc draw frames=60
alr installed package gles ipc lossless=true
alr installed package gles ipc error=none
alr installed package gles ipc handoff=ALR STATIC ENTRY HANDOFF: PASS
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=100
```

V73 changes the GLES shim itself: when `ALR_GPU_BRIDGE_HOST` and
`ALR_GPU_BRIDGE_PORT` are present, `eglSwapBuffers` submits `ALR_GPU_CLEAR` or
`ALR_GPU_DRAW_TRIANGLE` records over TCP loopback in addition to stdout. This
turns the installed `.deb` GLES demo into an actual bridge producer instead of
only a text-command fixture.

Current V74 installed-package GLES ACK snapshot:

```text
build: 0.4.74-installed-gles-ack
ALR INSTALLED PACKAGE GLES IPC EXECUTION: PASS
alr installed package gles ipc received frames=60
alr installed package gles ipc draw frames=60
alr installed package gles ipc ack frames=60
alr installed package gles ipc lossless=true
alr installed package gles ipc error=none
alr installed package gles ipc handoff=ALR STATIC ENTRY HANDOFF: PASS
ALR_GLES_IPC_ACK_SUMMARY requested=60 received=60 avg_us=102882 min_us=32774 max_us=130170
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=99
```

V74 adds an optional host-to-guest ACK mode with `ALR_GPU_BRIDGE_ACK=1`.
The Android bridge acknowledges each submitted GLES frame, and the guest shim
prints RTT statistics before shutdown. The first device run exposed the previous
5s child timeout by cutting off at 51 ACKed frames; the gate now uses a 12s
timeout and requires all 60 frame ACKs plus the guest-side ACK summary.

Current V75 installed-package GLES procaddr snapshot:

```text
build: 0.4.75-installed-procaddr
ALR INSTALLED PACKAGE GLES PROCADDR EXECUTION: PASS
rootfs installed alr gles procaddr demo exists=true executable=true bytes=7736
alr installed package gles procaddr handoff=ALR STATIC ENTRY HANDOFF: PASS
ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset
ALR_GLES_PROC_DEMO_PROC glDrawArrays ok
ALR_GLES_PROC_DEMO_PROC glUniform4f ok
ALR_GLES_PROC_DEMO_WORKLOAD requested=45 submitted=45
alr installed package gles procaddr draw command count=45
installed package compatibility table=script:PASS,gpu-clear-ipc:PASS,gles-demo:PASS,gles-tcp-ack:PASS,gles-procaddr:PASS,wayland:not-packaged,x11:not-packaged
ALR_GLES_IPC_ACK_SUMMARY requested=60 received=60 avg_us=113000 min_us=67177 max_us=132319
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=99
```

V75 packages the source-built `eglGetProcAddress` GLES demo as a second
installed `.deb` application entrypoint. This makes the installed-package GPU
path cover both direct GLES symbol linkage and dynamic GLES symbol lookup before
the commands are submitted to the Android-native Surface/EGL renderer.

Current V76 installed-package Wayland/X11 GUI snapshot:

```text
build: 0.4.76-installed-gui
ALR INSTALLED PACKAGE WAYLAND GUI GPU BRIDGE EXECUTION: PASS
ALR INSTALLED PACKAGE X11 GUI GPU BRIDGE EXECUTION: PASS
rootfs installed alr wayland gui client exists=true executable=true bytes=706896
rootfs installed alr x11 gui client exists=true executable=true bytes=706896
alr installed package wayland gui ipc received frames=4
alr installed package x11 gui ipc received frames=4
alr installed package wayland gui ipc lossless=true
alr installed package x11 gui ipc lossless=true
alr installed package wayland gui ipc client handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package x11 gui ipc client handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package wayland gui ipc stdout=alr-wayland-gpu-client ok
alr installed package x11 gui ipc stdout=alr-x11-gpu-client ok
installed package compatibility table=script:PASS,gpu-clear-ipc:PASS,gles-demo:PASS,gles-tcp-ack:PASS,gles-procaddr:PASS,wayland:PASS,x11:PASS
ALR_GLES_IPC_ACK_SUMMARY requested=60 received=60 avg_us=125401 min_us=43218 max_us=132379
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=100
```

V76 moves the Wayland and X11 GUI bridge clients into the installed `.deb`
surface as `/usr/local/bin/alr-package-wayland-gpu-client` and
`/usr/local/bin/alr-package-x11-gpu-client`. The installed-package compatibility
table is now backed by real package entrypoints for script, GPU clear, GLES
stdout, GLES TCP+ACK, GLES procaddr, Wayland, and X11.

Current V77 installed-package Vulkan discovery snapshot:

```text
build: 0.4.77-installed-vulkan-discovery
HOST VULKAN DISCOVERY EXECUTION: PASS
ALR INSTALLED PACKAGE VULKAN DISCOVERY EXECUTION: PASS
rootfs installed alr vulkan discovery client exists=true executable=true bytes=6312
alr installed package vulkan discovery raw=ALR_VK_DISCOVERY_HELLO version=1 request=instance-device
alr installed package vulkan discovery ack=ALR_VK_DISCOVERY_ACK status=PASS physical_devices=1 hardware=true device=Mali-G615_MC2
alr installed package vulkan discovery handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package vulkan discovery stdout=alr guest vulkan discovery client ok
host vulkan device=host vulkan device=Mali-G615 MC2
host vulkan hardware candidate=host vulkan hardware candidate=true
installed package compatibility table=script:PASS,gpu-clear-ipc:PASS,gles-demo:PASS,gles-tcp-ack:PASS,gles-procaddr:PASS,wayland:PASS,x11:PASS,vulkan-discovery:PASS
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=99
```

V77 adds the first Vulkan-shaped installed package probe. The guest side is a
glibc Linux TCP client installed from the local `.deb`; the host side stays in
the Android APK process and uses the NDK Vulkan loader to create an instance,
enumerate a physical device, find a graphics queue family, and create a logical
device. This is intentionally discovery-only, not yet a Vulkan ICD or WSI
present path, but it proves the process split needed for a future Vulkan proxy.

Current V78 Vulkan device-record bridge snapshot:

```text
build: 0.4.78-vulkan-device-records
HOST VULKAN DISCOVERY EXECUTION: PASS
ALR INSTALLED PACKAGE VULKAN DISCOVERY EXECUTION: PASS
alr installed package vulkan discovery ack=ALR_VK_DISCOVERY_ACK status=PASS physical_devices=1 hardware=true device=Mali-G615_MC2
alr installed package vulkan discovery device record=ALR_VK_DEVICE_RECORD name=Mali-G615_MC2 api=1.3.247 type=integrated-gpu physical_devices=1 queue_families=1 graphics_queue=0
alr installed package vulkan discovery feature record=ALR_VK_FEATURE_RECORD robust_buffer_access=true geometry_shader=true sampler_anisotropy=true max_image_2d=16384 max_memory_allocations=16384
rootfs installed alr vulkan discovery client exists=true executable=true bytes=6952
installed package compatibility table=script:PASS,gpu-clear-ipc:PASS,gles-demo:PASS,gles-tcp-ack:PASS,gles-procaddr:PASS,wayland:PASS,x11:PASS,vulkan-discovery:PASS
surface gl renderer=Mali-G615 MC2
surface gles shim vs native average ratio pct=99
alr installed package vulkan discovery stdout=... ALR_VK_DISCOVERY_DEVICE_RECORD ok ... ALR_VK_DISCOVERY_FEATURE_RECORD ok
```

V78 promotes the Vulkan bridge from a single PASS ACK to structured Android
device and feature records. The guest now fails the installed-package Vulkan
probe unless the host returns both `ALR_VK_DEVICE_RECORD` and
`ALR_VK_FEATURE_RECORD`. This is still a discovery/data-plane contract rather
than a Vulkan command stream, but it gives the future ICD/proxy path concrete
device, queue, feature, and limit fields to negotiate against.

Current V79 Android Vulkan Surface clear snapshot:

```text
build: 0.4.79-vulkan-surface-clear
versionCode=79
versionName=0.4.79-vulkan-surface-clear
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
surface vulkan device=Mali-G615 MC2
surface vulkan api version=1.3.247
surface vulkan graphics present queue=0
surface vulkan present mode=mailbox
surface vulkan swapchain image count=7
surface vulkan clear command=ok color=0.12,0.64,0.92,1.0
surface vulkan queue submit=ok
surface vulkan present=ok
surface vulkan hardware render=true
surface vulkan render elapsed us=30482
surface gl renderer=Mali-G615 MC2
surface gles shim vs native average ratio pct=100
```

V79 adds the first Android-native Vulkan WSI proof. The APK process creates an
Android `VkSurfaceKHR` from the live `SurfaceView`, creates a swapchain, clears
one acquired image through a Vulkan command buffer, submits it to a graphics
present queue, and presents it. This is still host-side proof rather than guest
Vulkan command forwarding, but it confirms that the same Android surface used
by the GLES bridge can be driven by Vulkan without a software renderer.

Current V80 guest-requested Vulkan Surface clear snapshot:

```text
build: 0.4.80-guest-vulkan-clear-request
versionCode=80
versionName=0.4.80-guest-vulkan-clear-request
GUEST VULKAN SURFACE CLEAR REQUEST EXECUTION: PASS
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
surface vulkan clear request source=guest-request
surface vulkan clear request tag=guest-vulkan-clear-0001
surface vulkan device=Mali-G615 MC2
surface vulkan present mode=mailbox
surface vulkan clear command=ok color=0.12,0.64,0.92,1
surface vulkan present=ok
surface vulkan hardware render=true
surface vulkan render elapsed us=19542
surface gl renderer=Mali-G615 MC2
surface gles shim vs native average ratio pct=100
```

V80 connects the installed glibc Vulkan discovery client to the host Vulkan
Surface proof as the first WSI-shaped guest command. The guest sends
`ALR_VK_SURFACE_CLEAR_REQUEST`, the host bridge accepts it with
`ALR_VK_SURFACE_CLEAR_ACCEPTED`, and the Surface callback uses that request as
the Vulkan clear command source. This is still a narrow clear/present command,
not an ICD, but it moves the Vulkan path from host-only proof to a
guest-directed Android-native Surface operation.

Current V81 guest `libvulkan.so.1` proxy smoke snapshot:

```text
build: 0.4.81-guest-vulkan-proxy-smoke
versionCode=81
versionName=0.4.81-guest-vulkan-proxy-smoke
rootfs_version=bookworm-slim-2026-05-gui-gpu-v81
rootfs sha256=c67ec2fbf8e6d882d6f28cc4aab29d6e6658f1eb3ebe64b1d579b4f8a991a120
rootfs size bytes=35860480
rootfs alr-vulkan-proxy-smoke bytes=5920
rootfs libvulkan.so.1 bytes=5256
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
GUEST VULKAN SURFACE CLEAR REQUEST EXECUTION: PASS
GUEST VULKAN PROXY SURFACE CLEAR EXECUTION: PASS
surface vulkan clear request source=guest-request
surface vulkan clear request tag=guest-vulkan-proxy-clear-0001
surface vulkan device=Mali-G615 MC2
surface vulkan api version=1.3.247
surface vulkan graphics present queue=0
surface vulkan present mode=mailbox
surface vulkan swapchain image count=7
surface vulkan clear command=ok color=0.33,0.22,0.88,1
surface vulkan queue submit=ok
surface vulkan present=ok
surface vulkan hardware render=true
surface vulkan render elapsed us=19552
surface gl renderer=Mali-G615 MC2
surface gles shim vs native average ratio pct=100
```

V81 adds the first guest-side Vulkan-shaped loader artifact. The rootfs now
contains a minimal `libvulkan.so.1` proxy plus an installed-package smoke binary
that `dlopen`s the library, calls `vkEnumerateInstanceVersion`, and asks the
host bridge for a Vulkan Surface clear. The host still executes the real
Android Vulkan WSI work, but the request now originates behind the guest
Vulkan ABI name instead of a standalone discovery-only client.

Current V82 bounded binary Vulkan proxy bridge snapshot:

```text
build: 0.4.82-vulkan-binary-proxy-bridge
versionCode=82
versionName=0.4.82-vulkan-binary-proxy-bridge
rootfs_version=bookworm-slim-2026-05-gui-gpu-v82
rootfs sha256=0c5bfa6d15b09ebc074222f0f8a8f11be4de49ad2f5223e3155798e169aee6a3
rootfs size bytes=36087296
installed alr-package-vulkan-proxy-smoke bytes=6016
installed libvulkan.so.1 bytes=5952
ALR_VK_BINARY_BRIDGE_ACK status=PASS protocol=alr-vk-bin-v1
ALR_VK_PROXY_BINARY_BRIDGE ok
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
GUEST VULKAN SURFACE CLEAR REQUEST EXECUTION: PASS
GUEST VULKAN PROXY SURFACE CLEAR EXECUTION: PASS
surface vulkan clear request source=guest-request
surface vulkan clear request tag=guest-vulkan-proxy-clear-0001
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST ... protocol=binary-frame-v1
surface vulkan device=Mali-G615 MC2
surface vulkan api version=1.3.247
surface vulkan graphics present queue=0
surface vulkan present mode=mailbox
surface vulkan swapchain image count=7
surface vulkan clear command=ok color=0.33,0.22,0.88,1
surface vulkan queue submit=ok
surface vulkan present=ok
surface vulkan hardware render=true
surface vulkan render elapsed us=19296
surface gl renderer=Mali-G615 MC2
surface gles shim vs native average ratio pct=99
```

V82 replaces the proxy smoke's line-oriented clear request path with a bounded
binary-framed request and response. The guest still emits a one-line hello for
human diagnostics, then sends an `ALVB` frame containing millesimal clear color,
tag, and source fields. The host replies with an `ALVR` frame that carries
bounded discovery/device/feature/clear-accepted records. This is still a small
proxy protocol, not full Vulkan command forwarding, but it removes the
unbounded read-line request shape from the first Vulkan proxy path.

Current V83 Vulkan ICD manifest discovery snapshot:

```text
build: 0.4.83-vulkan-icd-manifest-smoke
versionCode=83
versionName=0.4.83-vulkan-icd-manifest-smoke
rootfs_version=bookworm-slim-2026-05-gui-gpu-v83
rootfs sha256=0a6df36f01d6acba6fd6a11f0916563c95f8491711aa7a6b0790da198b50242e
rootfs size bytes=36106240
installed alr-package-vulkan-icd-manifest-smoke bytes=7224
installed alr_vulkan_icd.aarch64.json bytes=120
installed libvulkan.so.1 bytes=5952
ALR_VK_ICD_MANIFEST path=/usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json
ALR_VK_ICD_LIBRARY_PATH libvulkan.so.1
ALR_VK_ICD_API_VERSION 1.3.247
ALR_VK_ICD_BINARY_BRIDGE ok
ALR_VK_ICD_SURFACE_CLEAR_REQUEST_ACCEPTED ok
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
GUEST VULKAN SURFACE CLEAR REQUEST EXECUTION: PASS
GUEST VULKAN PROXY SURFACE CLEAR EXECUTION: PASS
GUEST VULKAN ICD MANIFEST SURFACE CLEAR EXECUTION: PASS
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.33 green=0.22 blue=0.88 alpha=1.0 tag=guest-vulkan-proxy-clear-0001 source=libvulkan-proxy protocol=binary-frame-v1
surface vulkan device=Mali-G615 MC2
surface vulkan present mode=mailbox
surface vulkan present=ok
surface vulkan hardware render=true
surface vulkan render elapsed us=19776
surface gl renderer=Mali-G615 MC2
surface gles shim vs native average ratio pct=100
```

V83 adds rootfs and installed-package Vulkan ICD discovery metadata at
`/usr/share/vulkan/icd.d/alr_vulkan_icd.aarch64.json`. The new glibc smoke reads
that manifest, extracts `library_path=libvulkan.so.1`, loads the guest proxy
through the same library path ordinary Vulkan loader setups use, and then
reuses the V82 binary bridge to request an Android-native Vulkan Surface clear.
This is still a manifest-level discovery proof rather than a full guest Vulkan
loader integration, but it removes the hardcoded proxy library name from the
latest installed Vulkan smoke entrypoint.

Current V84 Vulkan loader-info discovery snapshot:

```text
build: 0.4.84-vulkan-loader-info-smoke
versionCode=84
versionName=0.4.84-vulkan-loader-info-smoke
rootfs_version=bookworm-slim-2026-05-gui-gpu-v84
rootfs sha256=060298937b387063b5c6fd7dddfcf2459e29e77912e3790f0bb1da8e89197e70
rootfs size bytes=36116480
installed alr-package-vulkan-loader-info bytes=7896
installed alr-package-vulkan-icd-manifest-smoke bytes=7224
installed alr_vulkan_icd.aarch64.json bytes=120
installed libvulkan.so.1 bytes=5952
GUEST VULKAN LOADER INFO SURFACE CLEAR EXECUTION: PASS
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.33 green=0.22 blue=0.88 alpha=1.0 tag=guest-vulkan-proxy-clear-0001 source=libvulkan-proxy protocol=binary-frame-v1
surface vulkan device=Mali-G615 MC2
surface vulkan present mode=mailbox
surface vulkan present=ok
surface vulkan hardware render=true
surface vulkan render elapsed us=19415
surface gles shim vs native average ratio pct=100
```

V84 adds an installed-package `alr-package-vulkan-loader-info` probe. The guest
probe selects an ICD manifest through `VK_DRIVER_FILES`/`VK_ICD_FILENAMES`,
loads `libvulkan.so.1` from the glibc rootfs library path, calls
`vkEnumerateInstanceVersion`, records vulkaninfo-style manifest/device/feature
checks, and then reuses the binary bridge for a guest-requested Android Vulkan
Surface clear. This is still a loader-info smoke rather than the Khronos Vulkan
loader, but it proves the rootfs packaging and Android runner now exercise the
same discovery shape a real loader/vulkaninfo path will need.

Current V85 Unix-domain Vulkan loader bridge snapshot:

```text
build: 0.4.85-vulkan-unix-loader-bridge
versionCode=85
versionName=0.4.85-vulkan-unix-loader-bridge
rootfs_version=bookworm-slim-2026-05-gui-gpu-v85
rootfs sha256=6d782ed01d9dae8e071fe805b8121cb3ee93bb92842900a302f652f37cddadc3
rootfs size bytes=36116480
installed alr-package-vulkan-loader-info bytes=7896
installed libvulkan.so.1 bytes=6368
installed alr_vulkan_icd.aarch64.json bytes=120
GUEST VULKAN UNIX SOCKET LOADER INFO SURFACE CLEAR EXECUTION: PASS
VULKAN BRIDGE UNIX TRANSPORT EXECUTION: PASS
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.33 green=0.22 blue=0.88 alpha=1.0 tag=guest-vulkan-proxy-clear-0001 source=libvulkan-proxy protocol=binary-frame-v1 transport=unix-abstract
surface vulkan device=Mali-G615 MC2
surface vulkan present=ok
surface vulkan hardware render=true
vulkan bridge transport tcp loader elapsed ms=303
vulkan bridge transport unix loader elapsed ms=202
vulkan bridge transport unix vs tcp ratio pct=66
vulkan bridge transport unix faster than tcp=true
surface gles shim vs native average ratio pct=100
```

V85 keeps the TCP binary bridge as a fallback but adds `ALR_VK_BRIDGE_SOCKET`
support to the guest `libvulkan.so.1` proxy. When set to an `@name`, the proxy
connects to a Linux abstract Unix-domain socket. Android hosts the peer through
`LocalServerSocket`, parses the same bounded `ALVB` request, emits the same
`ALVR` response, and now feeds the Unix-backed loader-info clear request into
the native Vulkan Surface renderer. The first device-side timing sample is not a
benchmark suite, but it proves the lower-overhead transport path is viable and
measurably shorter for this installed loader-info workload.

Current V86 GLES Unix-domain bridge snapshot:

```text
build: 0.4.86-gles-unix-bridge
versionCode=86
versionName=0.4.86-gles-unix-bridge
rootfs_version=bookworm-slim-2026-05-gui-gpu-v86
rootfs sha256=4fa773507f52dd94a8ec22f36fba98e28ce6b0f0b399bdae5c99a213de001fbe
rootfs size bytes=36126720
installed libalr_gles_shim.so bytes=16464
installed libEGL.so bytes=16448
installed libGLESv2.so bytes=16448
installed alr-package-gles-demo bytes=8264
GLES BRIDGE UNIX TRANSPORT EXECUTION: PASS
GUEST VULKAN UNIX SOCKET LOADER INFO SURFACE CLEAR EXECUTION: PASS
VULKAN BRIDGE UNIX TRANSPORT EXECUTION: PASS
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.33 green=0.22 blue=0.88 alpha=1.0 tag=guest-vulkan-proxy-clear-0001 source=libvulkan-proxy protocol=binary-frame-v1 transport=unix-abstract
surface vulkan present=ok
surface vulkan hardware render=true
gles bridge transport tcp loader elapsed ms=7555
gles bridge transport unix loader elapsed ms=12768
gles bridge transport unix vs tcp ratio pct=169
gles bridge transport unix faster than tcp=false
vulkan bridge transport unix vs tcp ratio pct=100
surface gles shim vs native average ratio pct=101
```

V86 extends the Unix-domain control path to the GLES shim. `libalr_gles_shim.so`
now honors `ALR_GPU_BRIDGE_SOCKET=@...` before falling back to
`ALR_GPU_BRIDGE_HOST`/`ALR_GPU_BRIDGE_PORT`. Android runs both the existing TCP
installed-package GLES ACK smoke and a new `LocalServerSocket` Unix ACK smoke,
then renders the Unix-fed commands on the Android Surface. The first device
sample shows correctness but not a speed win for per-frame ACK traffic. That
evidence makes the next optimization target explicit: keep Unix as the control
path, but batch GLES command/ACK traffic so the bridge does not pay one round
trip for every tiny frame command.

V87 implements that batching target for the GLES shim. `ALR_GPU_BRIDGE_BATCH=1`
switches the guest from one ACK per frame command to a bounded
`ALR_GPU_BATCH_BEGIN` / command lines / `ALR_GPU_BATCH_END` transfer over the
same abstract Unix-domain socket. Android replies with one
`ALR_GPU_BATCH_ACK`, parses the same clear/draw commands, and renders the batch
through the native Surface renderer.

```text
build: 0.4.87-gles-unix-batch-bridge
versionCode=87
versionName=0.4.87-gles-unix-batch-bridge
rootfs_version=bookworm-slim-2026-05-gui-gpu-v87
rootfs sha256=9ddee16a49e5abe8d714eb8e8ba4b4c31e10f3d578d0f7bbd07d31c87cbd35b9
rootfs size bytes=36136960
installed libalr_gles_shim.so bytes=19296
installed libEGL.so bytes=19280
installed libGLESv2.so bytes=19280
installed alr-package-gles-demo bytes=8264
GLES BRIDGE UNIX TRANSPORT EXECUTION: PASS
GLES BRIDGE UNIX BATCH TRANSPORT EXECUTION: PASS
GUEST VULKAN UNIX SOCKET LOADER INFO SURFACE CLEAR EXECUTION: PASS
VULKAN BRIDGE UNIX TRANSPORT EXECUTION: PASS
surface vulkan clear request=ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.33 green=0.22 blue=0.88 alpha=1.0 tag=guest-vulkan-proxy-clear-0001 source=libvulkan-proxy protocol=binary-frame-v1 transport=unix-abstract
surface vulkan present=ok
surface vulkan hardware render=true
gles bridge transport tcp loader elapsed ms=6849
gles bridge transport unix loader elapsed ms=15708
gles bridge transport unix batch loader elapsed ms=908
gles bridge transport unix batch vs tcp ratio pct=13
gles bridge transport unix batch vs unix ack ratio pct=5
gles bridge transport unix batch faster than unix ack=true
vulkan bridge transport unix vs tcp ratio pct=100
surface gles shim vs native average ratio pct=100
```

The device result changes the GLES bridge from "transport compatibility" to a
real optimization artifact: the old Unix per-frame ACK path is retained as an
A/B baseline, while the batch path removes almost all ACK round trips for this
bounded 60-frame workload.

V88 moves the Wayland/X11-shaped GUI smoke clients onto the same Unix-domain
control style. The GUI clients are now project-owned source-built static aarch64
ELFs (`rootfs/guest-src/gui/alr_gui_gpu_client.c`) so the transport behavior is
clean-room and reproducible. `ALR_GUI_BRIDGE_SOCKET=@...` selects Android
`LocalServerSocket`; TCP remains as the baseline.

```text
build: 0.4.88-gui-unix-bridge
versionCode=88
versionName=0.4.88-gui-unix-bridge
rootfs_version=bookworm-slim-2026-05-gui-gpu-v88
rootfs sha256=89f15a81dc62880cca64483e3dbb691f8de5240940e263dee305d4a32a0e8e90
rootfs size bytes=34263040
rootfs /usr/bin/alr-wayland-gpu-client bytes=32800
rootfs /usr/bin/alr-x11-gpu-client bytes=32800
GUI BRIDGE UNIX TRANSPORT EXECUTION: PASS
WAYLAND GUI UNIX TRANSPORT EXECUTION: PASS
X11 GUI UNIX TRANSPORT EXECUTION: PASS
gui bridge transport wayland tcp loader elapsed ms=102
gui bridge transport wayland unix loader elapsed ms=101
gui bridge transport wayland unix vs tcp ratio pct=99
gui bridge transport x11 tcp loader elapsed ms=103
gui bridge transport x11 unix loader elapsed ms=102
gui bridge transport x11 unix vs tcp ratio pct=99
gui bridge wayland unix frames=4/4
gui bridge x11 unix frames=4/4
GLES BRIDGE UNIX BATCH TRANSPORT EXECUTION: PASS
GUEST VULKAN UNIX SOCKET LOADER INFO SURFACE CLEAR EXECUTION: PASS
surface wayland frames rendered=16
surface x11 frames rendered=16
surface vulkan present=ok
surface vulkan hardware render=true
```

V89 moves the GUI path one step closer to a real Linux desktop entry point by
adding a minimal clean-room `WAYLAND_DISPLAY` endpoint. The new guest client is
source-built from `rootfs/guest-src/gui/alr_wayland_display_client.c`, installed
both directly as `/usr/bin/alr-wayland-display-client` and through the local
`.deb` as `/usr/local/bin/alr-package-wayland-display-client`. Android hosts an
app-private `LocalServerSocket`, exports `WAYLAND_DISPLAY=alr-wayland-0`,
`XDG_RUNTIME_DIR=/tmp/alr-wayland-runtime`, and
`ALR_WAYLAND_DISPLAY_SOCKET=@...`, then translates bounded
`ALR_WL_SURFACE_COMMIT` records into the existing Surface renderer queue.

```text
build: 0.4.89-wayland-display-bridge
versionCode=89
versionName=0.4.89-wayland-display-bridge
rootfs_version=bookworm-slim-2026-05-wayland-display-v89
rootfs sha256=51d8795a26ef91f371b580db6b00fd088cc2eec12b00fe95d8cf9408afbdae3c
rootfs size bytes=34078720
rootfs /usr/bin/alr-wayland-display-client bytes=30312
rootfs installed alr wayland display client bytes=30312
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
alr installed package wayland display ipc received frames=3/3
alr installed package wayland display ipc ack raw=ALR_WL_DISPLAY_ACK display=alr-wayland-0 commits=3 expected=3 lossless=true transport=unix-abstract-wayland
alr installed package wayland display ipc error=none
surface wayland frames rendered=19
surface x11 frames rendered=16
surface vulkan present=ok
surface vulkan hardware render=true
```

This is not a full Wayland compositor yet. It is the smallest useful bridge
shape for the next clean-room phase: real guest programs should see a
`WAYLAND_DISPLAY`-style endpoint first, while Plib keeps Android-native
Surface/EGL/Vulkan ownership on the host side and avoids PRoot-style syscall
mediation for every graphics operation.

V90 adds the first verified Wayland buffer payload path. The guest creates an
app-private RGBA staging payload, advertises it through `ALR_WL_SHM_POOL_CREATE`
and `ALR_WL_BUFFER_ATTACH`, and reuses that `wl_buffer` across three commits.
Android verifies payload size and FNV-1a checksum before treating the commits as
Surface-renderable frames. This is still a file-backed staging bridge, not yet
FD-passed ashmem or `AHardwareBuffer`, but it proves the control path no longer
has to carry all image payload data inline.

```text
build: 0.4.90-wayland-shared-payload
versionCode=90
versionName=0.4.90-wayland-shared-payload
rootfs_version=bookworm-slim-2026-05-wayland-shared-payload-v90
rootfs sha256=53a6b74e5881d92cd98e1ecb88c9c00e5a8710c953ada44896bf29dce1ad5699
rootfs size bytes=34088960
rootfs /usr/bin/alr-wayland-display-client bytes=38032
rootfs installed alr wayland display client bytes=38032
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
alr installed package wayland display ipc received frames=3/3
wayland display shared payload frames=3/3
wayland display shared payload bytes=691200
alr installed package wayland display ipc ack raw=ALR_WL_DISPLAY_ACK display=alr-wayland-0 commits=3 expected=3 lossless=true payloads=3 payload_bytes=691200 payload_verified=true transport=unix-abstract-wayland-shared-file
surface wayland frames rendered=19
surface x11 frames rendered=16
surface vulkan present=ok
surface vulkan hardware render=true
```

V91 moved the same payload contract onto Unix `SCM_RIGHTS`: the source-built
guest creates a `memfd`, sends it over the Android-hosted `WAYLAND_DISPLAY`
`LocalServerSocket`, and Android verifies the received FD contents before ACKing
the surface commits.

V92 expands that from one reusable FD to a triple-buffer layout. The guest now
creates three distinct RGBA payloads, sends three memfds in one ancillary-data
preamble, emits per-frame `fd_index`/checksum metadata, and requires Android to
match every commit to the correct received FD. The file-backed payload remains
as a fallback evidence lane, but the display bridge PASS condition now depends
on the FD-backed path too.

```text
build: 0.4.92-wayland-triple-fd
versionCode=92
versionName=0.4.92-wayland-triple-fd
rootfs_version=bookworm-slim-2026-05-wayland-triple-fd-v92
rootfs sha256=521a3c0f565a171f92bf5f260fbacfae8cf1a8ac2c7953d7f73441962fab6282
rootfs size bytes=34561024
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
alr installed package wayland display ipc received frames=3/3
wayland display shared payload frames=3/3
wayland display shared payload bytes=691200
wayland display fd payload frames=3/3
wayland display fd payload bytes=691200
alr installed package wayland display ipc ack raw=ALR_WL_DISPLAY_ACK display=alr-wayland-0 commits=3 expected=3 lossless=true payloads=3 payload_bytes=691200 payload_verified=true fd_payloads=3 fd_payload_bytes=691200 fd_payload_verified=true fd_received=3 layout=triple-buffer transport=unix-abstract-wayland-scm-rights
surface wayland frames rendered=19
surface x11 frames rendered=16
surface vulkan present=ok
surface vulkan hardware render=true
```

V93 adds the Android-owned native-buffer half of the graphics bridge. The APK
allocates three `AHardwareBuffer` objects with CPU read/write and GPU
sample/color-output usage, writes deterministic RGBA payloads, verifies each
visible payload by checksum, and imports every buffer through
`eglGetNativeClientBufferANDROID`/`eglCreateImageKHR`/`glEGLImageTargetTexture2DOES`.
The v92 memfd display bridge remains the guest-to-host transport evidence; v93
proves the host can also own GPU-importable triple buffers under an ordinary APK
UID.

```text
build: 0.4.93-ahardwarebuffer-host
versionCode=93
versionName=0.4.93-ahardwarebuffer-host
rootfs_version=bookworm-slim-2026-05-wayland-triple-fd-v92
ANDROID HOST AHARDWAREBUFFER EXECUTION: PASS
ahardwarebuffer allocated buffers=3
ahardwarebuffer cpu verified buffers=3
ahardwarebuffer egl imported buffers=3
ahardwarebuffer visible payload bytes=691200
ahardwarebuffer host managed triple buffer=true
ahardwarebuffer egl image import=ok
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
surface vulkan present=ok
surface vulkan hardware render=true
```

V94 wires that native-buffer capability into the Wayland display evidence path.
Android now takes the parsed `ALR_WL_SURFACE_COMMIT` records from the installed
package display bridge, recreates those three frames as host-owned
`AHardwareBuffer` objects, verifies the payload bytes against the same 691200
bytes used by the v92 FD payload path, and imports all three as EGLImages. This
is still an evidence bridge rather than a full compositor allocator, but the
important shape is now joined: guest commits arrive through `WAYLAND_DISPLAY`,
guest payload descriptors are verified through `SCM_RIGHTS`, and matching
host-owned GPU buffers are proven for those commits.

```text
build: 0.4.94-wayland-ahb-backing
versionCode=94
versionName=0.4.94-wayland-ahb-backing
rootfs_version=bookworm-slim-2026-05-wayland-triple-fd-v92
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS
ahardwarebuffer source=wayland-display-commits
ahardwarebuffer requested buffers=3
ahardwarebuffer cpu verified buffers=3
ahardwarebuffer egl imported buffers=3
ahardwarebuffer visible payload bytes=691200
ahardwarebuffer wayland display backing=true
ahardwarebuffer egl image import=ok
surface vulkan present=ok
surface vulkan hardware render=true
```

V95 moves the AHardwareBuffer decision from a parallel evidence replay into the
Wayland bridge state model. The guest display client now advertises
`ALR_WL_AHB_BACKING_ADVERTISE`, marks each `ALR_WL_BUFFER_ATTACH` with
`backing=host-ahardwarebuffer`, emits `ALR_WL_DAMAGE` dirty rectangles, and
keeps the v92 memfd payloads as a verified fallback/control comparison. Android
parses attach state by sequence number, requires three host-backed partial
updates, ACKs `ahb_state_ready=true`, and sends the parsed dirty-rect metadata
into the native `AHardwareBuffer` probe. The native side updates and verifies
only the dirty rectangles, so the AHB write path accounts for 172800 dirty bytes
instead of recopying all 691200 fallback payload bytes.

```text
build: 0.4.95-wayland-ahb-dirty-state
versionCode=95
versionName=0.4.95-wayland-ahb-dirty-state
rootfs_version=bookworm-slim-2026-05-wayland-ahb-dirty-v95
rootfs sha256=20493ea66546a74bfe79e384b7461f25891fa0474ab9fc6ea8226128873b75ac
rootfs size bytes=35293184
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS
alr installed package wayland display ipc ack raw=... backing=host-ahardwarebuffer ahb_backed=3 dirty_rects=3 dirty_bytes=172800 partial_updates=3 ahb_state_ready=true zero_copy_candidate=true
wayland display ahardwarebuffer backed frames=3/3
wayland display dirty rect frames=3/3
wayland display dirty rect bytes=172800
wayland display partial upload ratio pct=25
ahardwarebuffer source=wayland-display-commits
ahardwarebuffer backing mode=host-ahardwarebuffer
ahardwarebuffer dirty rect frames=3/3
ahardwarebuffer dirty rect bytes=172800
ahardwarebuffer partial upload ratio pct=25
ahardwarebuffer visible payload bytes=172800
ahardwarebuffer wayland state machine backing=true
ahardwarebuffer wayland display backing=true
surface vulkan present=ok
surface vulkan hardware render=true
```

### V96 Wayland AHardwareBuffer Surface Compositor

Build `0.4.96-wayland-ahb-surface` connects the v95 host-backed Wayland dirty
state to the visible Android Surface path. Android still owns allocation and
presentation: each parsed `backing=host-ahardwarebuffer` commit allocates an
`AHardwareBuffer`, locks only the `ALR_WL_DAMAGE` rectangle for CPU population,
imports the buffer as an `EGLImage`, samples it as a GLES texture, and swaps the
result through the app Surface. This keeps the clean-room boundary explicit:
the Linux-side display stream describes surface state and dirty regions, while
Android native EGL/GLES remains the renderer and presentation owner.

```text
build: 0.4.96-wayland-ahb-surface
versionCode=96
versionName=0.4.96-wayland-ahb-surface
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS
WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS
wayland ahardwarebuffer surface compositor=egl-image-texture-to-android-surface
wayland ahardwarebuffer surface imported textures=3
wayland ahardwarebuffer surface sampled frames=3
wayland ahardwarebuffer surface presented frames=3
wayland ahardwarebuffer surface host-backed frames=3/3
wayland ahardwarebuffer surface dirty rect bytes=172800
wayland ahardwarebuffer surface partial upload ratio pct=25
wayland ahardwarebuffer surface sync fence accounting=ok
wayland ahardwarebuffer surface hardware render=true
surface vulkan hardware render=true
```

### V97 Wayland AHardwareBuffer Pool Reuse

Build `0.4.97-wayland-ahb-pool` stops treating the visible compositor pass as a
fresh allocation benchmark. The compositor now builds a three-slot host
`AHardwareBuffer` pool keyed by the guest `buffer_slot`, imports each slot once,
and replays the same three Wayland commits through two presentation passes.
That gives direct evidence that Android can keep the native buffers and EGLImage
textures resident while later commits reuse them and hand any returned fence FD
back into the next lock on the same slot.

```text
build: 0.4.97-wayland-ahb-pool
versionCode=97
versionName=0.4.97-wayland-ahb-pool
WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS
wayland ahardwarebuffer surface replay passes=2
wayland ahardwarebuffer surface total frame submissions=6
wayland ahardwarebuffer surface buffer pool mode=slot-reuse
wayland ahardwarebuffer surface buffer pool slots=3
wayland ahardwarebuffer surface buffer pool misses=3
wayland ahardwarebuffer surface buffer pool reuses=3
wayland ahardwarebuffer surface allocated buffers=3
wayland ahardwarebuffer surface imported textures=3
wayland ahardwarebuffer surface sampled frames=6
wayland ahardwarebuffer surface presented frames=6
wayland ahardwarebuffer surface dirty rect bytes=345600
wayland ahardwarebuffer surface partial upload ratio pct=25
wayland ahardwarebuffer surface fence wait candidates=3
wayland ahardwarebuffer surface fence pacing mode=reuse-slot-fence-handoff
wayland ahardwarebuffer surface sync fence accounting=ok
wayland ahardwarebuffer surface hardware render=true
```

### V98 Wayland Wire-Subset Trace

Build `0.4.98-wayland-wire-subset` adds a stricter clean-room Wayland wire
trace beside the existing `ALR_WL_*` compatibility records. The guest display
client now emits bounded `ALR_WL_WIRE` records using the real Wayland message
shape: object id, opcode, size/opcode header word, and ordered arguments for
`wl_display.get_registry`, `wl_registry.bind`, `wl_compositor.create_surface`,
`wl_shm.create_pool`, `wl_shm_pool.create_buffer`, `wl_surface.attach`,
`wl_surface.damage_buffer`, and `wl_surface.commit`. Android validates the
ordered wire subset before ACKing `wire_subset_ready=true`.

```text
build: 0.4.98-wayland-wire-subset
versionCode=98
versionName=0.4.98-wayland-wire-subset
rootfs_version=bookworm-slim-2026-05-wayland-wire-v98
rootfs sha256=3be38a2787dc7e4c0e825500a23cce08810879e91b9e24256bed6ef8a56648f3
rootfs size bytes=35296256
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
wayland display wire messages=15
wayland display wire subset ready=true
wayland display wire surface lifecycle=true
alr installed package wayland display ipc ack raw=... wire_messages=15 wire_subset_ready=true wire_surface_lifecycle=true ...
WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS
wayland ahardwarebuffer surface buffer pool reuses=3
wayland ahardwarebuffer surface presented frames=6
wayland ahardwarebuffer surface hardware render=true
```

### V99 Wayland Binary Request Decode

Build `0.4.99-wayland-binary-decode` keeps the v98 textual `ALR_WL_WIRE`
records as human-readable diagnostics, but adds a bounded raw binary Wayland
request stream before the display lifecycle records. The stream uses the real
Wayland request header layout for the current subset: little-endian object id,
little-endian `(size << 16) | opcode`, then request payload bytes. Android reads
the declared byte count, verifies the checksum, decodes every message boundary,
and only ACKs `binary_subset_ready=true` when the decoded request sequence still
matches the registry/compositor/shm/surface lifecycle.

```text
build: 0.4.99-wayland-binary-decode
versionCode=99
versionName=0.4.99-wayland-binary-decode
rootfs_version=bookworm-slim-2026-05-wayland-binary-v99
rootfs sha256=4df118e1666e36c78dd7f4854ed25b7f02a38796ee30b3ef13114d4c167a3853
rootfs size bytes=35300352
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
wayland display wire messages=15
wayland display binary messages=15
wayland display binary bytes=308
wayland display binary subset ready=true
alr installed package wayland display ipc ack raw=... binary_messages=15 binary_header_ready=true binary_subset_ready=true ...
WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS
wayland ahardwarebuffer surface buffer pool reuses=3
wayland ahardwarebuffer surface presented frames=6
wayland ahardwarebuffer surface hardware render=true
```

### V100 Wayland Continuous GUI Stream

Build `0.4.100-wayland-continuous-gui` turns the previous host-side replay
proof into a guest-driven continuous stream. The display client now emits eight
Wayland-shaped commits in one connection, and the binary Wayland request stream
grows to 30 decoded requests and 568 bytes. Android validates the continuous
stream markers, payload checksums, FD payloads, dirty rectangles, and
AHardwareBuffer state before ACKing.

```text
build: 0.4.100-wayland-continuous-gui
versionCode=100
versionName=0.4.100-wayland-continuous-gui
rootfs_version=bookworm-slim-2026-05-wayland-continuous-v100
rootfs sha256=84713c829832154e004e40090d40e632f439118848be3baa78c64a5d7c8d292e
rootfs size bytes=35307520
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
wayland display continuous stream ready=true
wayland display wire messages=30
wayland display binary messages=30
wayland display binary bytes=568
wayland display dirty rect bytes=460800
alr installed package wayland display ipc ack raw=... commits=8 expected=8 continuous_stream_ready=true ...
WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS
wayland ahardwarebuffer surface replay passes=1
wayland ahardwarebuffer surface continuous guest commits=true
wayland ahardwarebuffer surface simple gui demo candidate=true
wayland ahardwarebuffer surface buffer pool reuses=5
wayland ahardwarebuffer surface presented frames=8
wayland ahardwarebuffer surface hardware render=true
```

Build `0.4.101-simple-gui-demo` promotes that fixture into a named glibc app
launch track:

```text
build: 0.4.101-simple-gui-demo
versionCode=101
versionName=0.4.101-simple-gui-demo
rootfs_version=bookworm-slim-2026-05-simple-gui-demo-v101
rootfs_sha256=3ccd54fd7df06e703c8328306f592e3af058935314a13a96063f82a02c98e7e6
rootfs_size=35348480
SIMPLE GUI DEMO EXECUTION: PASS
SIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION: PASS
simple gui demo glibc dynamic=true
simple gui demo display commits=8/8
simple gui demo android surface candidate=true
```

The V101 demo is still a controlled raw-Wayland subset rather than GIMP. Its
purpose is to lock down the same Android process constraints GIMP will need:
dynamic glibc launch through the rootfs loader, no root, abstract Unix display
socket, SCM_RIGHTS memfd buffer passing, and Android-native AHardwareBuffer /
EGLImage / Surface presentation. The user-facing V118 demo target is now GIMP,
so subsequent backend work should grow rootfs packaging, GTK/GIO/font/module
coverage, and Wayland protocol coverage toward `gimp --new-instance` rather than
inventing another tiny final fixture.

Build `0.4.103-gimp-materialized` promotes the GIMP path from a lock/profile
artifact into a real Debian arm64 rootfs payload. The materializer downloads and
SHA-256 verifies the 246-package strict dependency closure, extracts it into the
Android rootfs, overlays the Plib launcher/profile/lock artifacts, and records a
minimal installed-package status snapshot. It still does not claim a rendered
GIMP window, but it does prove that the Android app can see `/usr/bin/gimp` and
run a glibc `gimp --version` probe through the ALR trampoline.

```text
build: 0.4.103-gimp-materialized
versionCode=103
versionName=0.4.103-gimp-materialized
rootfs_version=bookworm-slim-2026-05-gimp-materialized-v103
rootfs_sha256=41a737724a1f67c2c9ad0aa31598c770163edc3ab3b0c9d99380ae9ff3e332fd
rootfs_size=473436160
GIMP DEMO PROFILE EXECUTION: PASS
GIMP DEMO BUNDLE LOCK: PASS
ALR_GIMP_DEMO_PROFILE_READY target=gimp
ALR_GIMP_DEMO_BUNDLE_LOCK present=true package_count=246 download_size_mib=122.27
ALR_GIMP_DEMO_MATERIALIZED present=true package_count=246
ALR_GIMP_DEMO_BINARY present=true path=/usr/bin/gimp
ALR_GIMP_DEMO_LAUNCH_MODE version-probe
ALR_GIMP_DEMO_VERSION_EXIT 0
ALR_GIMP_DEMO_VERSION_STDOUT GNU Image Manipulation Program version
ALR_GIMP_DEMO_EXEC_READY true mode=version-probe
```

The bundle lock is generated from the official Debian `bookworm/main` arm64
`Packages.xz` index by `tools/gimp_bundle_resolver.py`. The default profile is
strict `Pre-Depends` + `Depends` only, currently 246 packages and 122.27 MiB of
`.deb` downloads. `tools/gimp_bundle_materializer.py` is the reproducible host
step that turns that lock into the larger V103 rootfs tar.

Build `0.4.104-gimp3-wayland` switches the named GIMP target from Debian
bookworm/GIMP 2.10/X11 staging to Debian trixie/GIMP 3.0 native Wayland
staging. The lock now resolves the trixie arm64 strict dependency closure,
including GTK3 and `libwayland-client0`, and the materialized rootfs records the
GIMP package version in `gimp-demo-materialized.txt`.

V104 also makes GIMP 3 on Wayland the primary GUI target. X11 remains useful as
an older bridge comparison, but the GIMP demo path is now explicitly
`GDK_BACKEND=wayland` with filesystem Unix-domain sockets in the rootfs runtime
directory.

The GIMP 3 gate is split into staged probes so startup failures are attributable
instead of hidden behind one long launch timeout:

- `gimp --help` proves the GIMP 3 binary, dynamic loader, and ALR handoff can
  run beyond a version probe.
- `gimp-console --version` proves the console binary entry path independently
  of GUI and batch interpreter startup.
- `gimp --no-interface --no-data --no-fonts --no-splash --no-shm --quit`
  isolates GIMP core quit startup. On the connected device this still times out,
  so the blocker is now known to be earlier than Script-Fu batch evaluation.
- `gimp-console --batch-interpreter plug-in-script-fu-eval --batch
  "(gimp-quit 0)" --quit` is the current GIMP 3 core/plugin startup probe.
  On the connected device it still does not reach a passing ALR handoff, so it
  is tracked as the next non-Wayland blocker rather than counted as GUI progress.
- The GTK/PyGObject probe at `$rootfs/tmp/alr-gimp-gtk-0` talks to a tiny
  clean-room Wayland responder rather than a passive socket. Android advertises
  `wl_compositor`, `wl_shm`, `xdg_wm_base`, `wl_seat`, and `wl_output`, answers
  the initial `wl_display.sync`, and lets GTK finish `Gtk.init([])` through ALR.
- The full GIMP probes at `$rootfs/tmp/alr-gimp-quit-0` and
  `$rootfs/tmp/alr-gimp-0` remain separate deep evidence lines so the next
  blocker is visible while the fast verifier still skips the long interactive
  launch.

```text
build: 0.4.104-gimp3-wayland
versionCode=104
versionName=0.4.104-gimp3-wayland
rootfs_version=trixie-slim-2026-05-gimp3-wayland-v104
rootfs_sha256=9ed659c149510393662754f2508805f84edef5721a49539c26fe820481fcd75e
rootfs_size=1365166080
GIMP DEMO PROFILE EXECUTION: PASS
GIMP CLI HELP PROBE EXECUTION: PASS
GIMP CONSOLE VERSION PROBE EXECUTION: PASS
GIMP CORE QUIT PROBE EXECUTION: FAIL
GIMP CORE QUIT BLOCKER: CORE_QUIT_TIMEOUT
GIMP CONSOLE BATCH QUIT PROBE EXECUTION: FAIL
GIMP CONSOLE BATCH QUIT BLOCKER: CORE_BATCH_TIMEOUT
full gimp probe mode=skipped
GIMP GTK WAYLAND PROBE EXECUTION: PASS
GIMP GUI QUIT WAYLAND PROBE EXECUTION:
GIMP GUI WAYLAND PROBE EXECUTION:
GIMP GUI WAYLAND BLOCKER: FAST_VERIFIER_SKIPPED
GIMP DEMO BUNDLE LOCK: PASS
ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=wayland WAYLAND_DISPLAY=alr-gimp-0 XDG_RUNTIME_DIR=/tmp
ALR_GIMP_DEMO_BUNDLE_LOCK present=true suite=trixie package_count=313
ALR_GIMP_DEMO_MATERIALIZED present=true package_count=313 gimp_version=3.0.4-3+deb13u7
ALR_GIMP_DEMO_VERSION_STDOUT GNU Image Manipulation Program version 3.
gimp cli help handoff=ALR STATIC ENTRY HANDOFF: PASS
gimp console version handoff=ALR STATIC ENTRY HANDOFF: PASS
gimp console version stdout=GNU Image Manipulation Program version 3.
gimp core quit blocker=core-quit-timeout
gimp console batch quit handoff=ALR STATIC ENTRY HANDOFF: FAIL
gimp console batch quit interpreter=plug-in-script-fu-eval
gimp console batch quit blocker=core-batch-timeout
gimp gtk wayland connected=true
gimp gtk wayland object=1
gimp gtk wayland opcode=1
gimp gtk wayland size=12
gimp gtk wayland request=wl_display.get_registry
gimp gtk wayland server requests=10
gimp gtk wayland server response bytes=316
gimp gtk wayland server globals=wl_compositor,wl_shm,xdg_wm_base,wl_seat,wl_output
gimp gtk wayland handoff=ALR STATIC ENTRY HANDOFF: PASS
gimp gtk wayland stdout=ALR_GIMP3_GTK_WAYLAND_PROBE ok
gimp gui wayland blocker=fast-verifier-skipped
```

Report:

```text
backend
device
android_sdk
rootfs_version
workload
iterations
elapsed_ms
relative_to_proot
pass_fail
```

## Security Constraints

- Runtime translation is not a security boundary by itself.
- Rootfs archives must remain verified and safely extracted.
- Host path escapes must be rejected unless represented by explicit bind mapping.
- Guest input must not directly select arbitrary host executable paths.
- Guest-visible `/proc` should not leak sensitive app or system details beyond required compatibility.
- IPC bridge parsers must be bounded and robust against malformed guest input.

## Implementation Order

1. Expand path/env tests.
2. Add backend report schema for ALR runtime even before execution.
3. Implement launcher skeleton.
4. Implement hook library with open/stat/readlink/getcwd/chdir.
5. Add hello execution path.
6. Add shell and child execution.
7. Add procfs minimal virtualization.
8. Add fakeroot identity.
9. Add package-manager preflight.
10. Add benchmark harness.
11. Investigate direct syscall coverage only after measured bypass.

## Open Questions

- Can the first ALR dynamic execution path use the guest glibc loader directly from rootfs under Android constraints, or is a packaged clean-room loader required?
- Which Android SDK/device combinations allow the needed child process and memory mapping behavior?
- Is seccomp user notification available from ordinary APK UIDs on target devices?
- How much procfs virtualization is required before Python, Node, Git, and package tools are practical?
- What hardlink strategy is acceptable for package managers without copying external metadata formats?
