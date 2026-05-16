# Zero-Overhead Linux GPU Runtime Roadmap

> **Codex-owned workflow:** Work in bounded implementation/evidence bundles, deliver APKs at bundle boundaries, and keep device evidence as the source of truth.

**Goal:** Run real Linux/glibc apps inside a non-root Android APK with minimal execution overhead and Android-native GPU/GUI acceleration.

**Architecture:** Separate the runtime execution backend from graphics acceleration. The execution backend should evolve from ptrace-based PRoot to a zero/low-ptrace proroot-style backend, while GPU/GUI acceleration should terminate on Android public graphics APIs: Surface + EGL/GLES first, Vulkan later. Linux apps should see Linux-like APIs, but rendering must be proxied to Android host GPU services instead of relying on Linux DRM/KMS device nodes.

**Tech Stack:** Kotlin/Android Activity, Android NDK/C++, PRoot/optional proroot-style backend, glibc rootfs, localhost/Unix IPC, EGL/GLES Surface renderer, future Vulkan host renderer, guest-side GL/EGL/Wayland/X11 shims.

---

## Current State

Device-proven milestones:

- Linux/glibc rootfs execution through packaged PRoot: PASS.
- Android host EGL/GLES hardware renderer on Mali-G615 MC2: PASS.
- Android Surface EGL/GLES hardware render: PASS.
- Guest GPU command controlling host Surface renderer: PASS.
- Guest TCP IPC multi-frame bridge: PASS.
- Guest GLES shim smoke: PASS.
- Guest Wayland/X11-style GUI frame bridge to Android Surface GPU: functionally PASS.

Latest V50 device evidence:

```text
surface frames rendered=230
surface frames dropped=0
surface gles shim render elapsed us=576046
surface gles shim average frame render us=16001
surface gles shim draw frames rendered=140
surface gles shim draw render elapsed us=1656444
surface gles shim draw average frame render us=11831
surface native gles frames rendered=32
surface native gles render elapsed us=348498
surface native gles average frame render us=10890
surface gles shim vs native average ratio pct=146
surface frame lossless=true
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
guest gui gpu compositor hardware render=true
guest gles draw via android surface=true
guest wayland/x11 gui gpu surface hardware render=true
```

Latest V54 CPU baseline evidence:

```text
build: 0.4.54-cpu-baseline-bench
native bionic fork benchmark=NATIVE BIONIC FORK BENCHMARK: PASS
native bionic fork benchmark average us=native fork repeat average elapsed us=953
alr static handoff benchmark average ms=alr handoff repeat average elapsed ms=4
proot static hello loop benchmark average ms=10
alr static handoff vs native fork ratio pct=419
alr static handoff vs proot loop ratio pct=40
alr static handoff faster than proot loop=true
alr dynamic glibc handoff benchmark average ms=alr handoff repeat average elapsed ms=4
proot dynamic glibc loop benchmark average ms=20
alr dynamic glibc handoff vs native fork ratio pct=419
alr dynamic glibc handoff vs proot loop ratio pct=20
alr dynamic glibc handoff faster than proot loop=true
alr loop hot path measured faster count=2/2
alr loop hot path perf evidence=PASS
```

Latest V55 syscall-heavy evidence:

```text
build: 0.4.55-syscall-bench
ALR SYSCALL STAT BENCH EXECUTION: PASS
ALR SYSCALL OPENREAD BENCH EXECUTION: PASS
ALR SYSCALL SPAWN BENCH EXECUTION: PASS
PROOT SYSCALL STAT BENCH EXECUTION: PASS
PROOT SYSCALL OPENREAD BENCH EXECUTION: PASS
PROOT SYSCALL SPAWN BENCH EXECUTION: PASS
rootfs /usr/bin/alr-syscall-bench exists=true executable=true bytes=6672
alr syscall stat benchmark average us=2760
proot syscall stat benchmark average us=283
alr syscall stat vs proot ratio pct=975
alr syscall openread benchmark average us=8073
proot syscall openread benchmark average us=214
alr syscall openread vs proot ratio pct=3772
alr syscall spawn benchmark average us=12928
proot syscall spawn benchmark average us=1593
alr syscall spawn vs proot ratio pct=811
alr syscall hot path measured faster count=0/3
alr syscall hot path perf evidence=NEEDS_WORK
```

This is the current highest-priority CPU backend issue: ALR repeated entry
handoff beats PRoot loop baselines, but the path-rewrite ptrace syscall path is
not yet competitive for filesystem-heavy or child-spawn-heavy guest workloads.

Latest V56 preload path-fastpath evidence:

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

The V56 result is the first strong evidence that common guest filesystem calls
must move out of global ptrace mediation. The raw path-rewrite ptrace backend is
still slower than PRoot for repeated open/read, but the guest preload shim runs
the same open/read workload at about 2% of the measured PRoot cost on the test
device. This is not a complete syscall backend yet: the stat preload path still
fails and must remain excluded from pass/faster counts until fixed.

Latest V57 preload stat-fastpath evidence:

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

The V57 result fixes the V56 stat preload failure by covering glibc's
`__xstat`/`__fxstatat` compatibility entry points, not only the plain `stat`
symbol. On the measured device, both preload filesystem microbenchmarks now run
far below the PRoot baseline while the older ptrace path remains much slower.
The immediate backend priority moves from proving the approach to widening
coverage: `access`, `readlink`, `getcwd`, `chdir`, metadata patching, and
process/package-manager edge cases.

Latest V58 preload fsmeta-fastpath evidence:

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

The V58 result widens the measured preload fast path from `stat/open/read` to a
metadata workload using guest `access` and `readlink` over a rootfs symlink
fixture. It also adds safe relative-symlink extraction support to the Android
rootfs installer, which is required for real Linux rootfs compatibility. The
preload fsmeta path measured about 5% of PRoot cost on the device while the
ptrace path remained far slower.

Latest V59 preload userland-dpkg evidence:

```text
build: 0.4.59-preload-userland-dpkg
ALR DPKG ARCH PRELOAD EXECUTION: PASS
alr dpkg --print-architecture preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr dpkg --print-architecture preload path rewrite=alr handoff path rewrite count=0
alr dpkg --print-architecture preload stdout=arm64
alr syscall preload hot path measured faster count=3/3
alr syscall preload hot path perf evidence=PASS
```

The V59 result moves the preload path out of synthetic syscall fixtures and
into a real glibc userland command: `dpkg --print-architecture` runs through the
ALR trampoline with `LD_PRELOAD` enabled and global ptrace path rewriting
disabled. The next missing userland coverage observed during probing is
directory iteration (`opendir`/`readdir`/`closedir`), which blocks some `apt`
commands from running under preload-only path virtualization.

Latest V60 preload apt-config evidence:

```text
build: 0.4.60-preload-aptconfig
ALR APT-CONFIG PRELOAD EXECUTION: PASS
alr apt-config --version preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr apt-config --version preload path rewrite=alr handoff path rewrite count=0
alr apt-config --version preload stdout=apt 2.8.3 (arm64)
alr syscall preload hot path measured faster count=3/3
alr syscall preload hot path perf evidence=PASS
```

The V60 result widens preload-only path virtualization with directory and
file-stream entry points: `opendir`, `fopen`, and `fopen64`. This is enough for
`apt-config --version` to run through the ALR trampoline with `LD_PRELOAD` and
without the global ptrace path rewrite loop. The rootfs payload also carries a
glibc-compatible `libdl.so.2 -> libc.so.6` symlink so the source-built preload
shim can resolve `dlsym` on modern Debian/glibc rootfs layouts.

Latest V61 preload apt-family evidence:

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
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
```

The V61 result records the broader apt-family proof in the Android app itself:
`apt`, `apt-get`, `apt-cache`, and `apt-config` all execute under the ALR
trampoline with `LD_PRELOAD` path virtualization and with global ptrace path
rewrite disabled. This keeps the package-manager smoke path moving toward
proroot-class behavior while preserving the Android-native Surface/GLES GPU
proof in the same device run.

Latest V64 preload apt-cache state evidence:

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

The V64 result moves from version banners into package-manager state/cache
handling. `apt-cache policy`, `apt-cache stats`, and `apt-cache pkgnames` now
succeed under preload-only path virtualization. The underlying v63 preload work
added `realpath`/`canonicalize_file_name`, `mkstemp`/`mkstemp64`, and
`rename`/`renameat`/`renameat2` coverage, proving apt can canonicalize
`/var/lib/dpkg/status`, create cache temp files under `/var/cache/apt`, commit
them, and query the cache without falling back to the global ptrace path rewrite
loop.

Latest V65 preload dpkg install evidence:

```text
build: 0.4.65-preload-dpkg-install
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
alr dpkg -i local deb preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr dpkg -i local deb preload execve loader rewrites=alr handoff execve loader rewrite count=5
alr dpkg -i local deb preload traced processes=alr handoff traced process count=10
alr dpkg -i local deb preload stdout=Selecting previously unselected package alr-smoke.\n(Reading database ... 0 files and directories currently installed.)\nPreparing to unpack .../alr-smoke_1.0_arm64.deb ...\nUnpacking alr-smoke (1.0) ...\nSetting up alr-smoke (1.0) ...
```

The V65 result moves from read-only package-manager cache queries into a real
local `.deb` install. This path uses the preload filesystem layer for hot file
operations plus ALR execve loader rewriting for helper programs such as
`dpkg-split`, `dpkg-deb`, `tar`, and `rm`. The preload shim now also covers
`mkdir`, `unlink`, `rmdir`, fake-root identity, and fake-root ownership changes
so package extraction and dpkg status updates can complete inside the Android
app-private rootfs.

Latest V67 installed package preload evidence:

```text
build: 0.4.67-preload-installed-package
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
alr dpkg -i local deb preload stdout=Selecting previously unselected package alr-smoke.\n(Reading database ... 0 files and directories currently installed.)\nPreparing to unpack .../alr-smoke_1.0_arm64.deb ...\nUnpacking alr-smoke (1.0) ...\nSetting up alr-smoke (1.0) ...
alr installed package preload handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package preload stdout=alr local deb package smoke ok\nALR_SMOKE_PACKAGE_SCRIPT=1
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
```

The V67 result proves the next package-manager step: after the local `.deb`
install completes, the installed package entrypoint itself runs through
ALR/preload without falling back to PRoot. ALR exec rewrite now recognizes
shebang scripts and can map a script execution request to the guest
interpreter. The installed smoke package intentionally keeps this entrypoint
free of nested package-manager child calls so package installation and package
execution remain separately diagnosable.

Latest V68 shell child exec evidence:

```text
build: 0.4.68-preload-shell-child-exec
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
ALR SHELL DPKG ARCH PRELOAD EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
alr shell dpkg --print-architecture preload execve attempts=alr handoff execve attempt count=1
alr shell dpkg --print-architecture preload execve loader rewrites=alr handoff execve loader rewrite count=1
alr shell dpkg --print-architecture preload traced processes=alr handoff traced process count=2
alr shell dpkg --print-architecture preload stdout=arm64
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
```

The V68 result removes the next shell-wrapper blocker. Android app-process
seccomp traps guest `faccessat2` during dash PATH probing; ALR now emulates that
access check against the rootfs view, allowing `/bin/dash -c "dpkg
--print-architecture"` to locate `/usr/bin/dpkg`, rewrite the child exec through
the glibc loader, and complete under ALR/preload.

Latest V69 installed package child exec evidence:

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
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
```

The V69 result proves the installed package entrypoint can now run a nested
glibc child command through the ALR/preload path. This is closer to real Linux
desktop app launchers and package-installed wrapper scripts than the earlier
single-process smoke, because the installed script performs PATH lookup and a
child `execve` for `/usr/bin/dpkg` before reporting success.

Latest V70 env-mediated child chain evidence:

```text
build: 0.4.70-preload-env-child-chain
ALR DPKG LOCAL INSTALL PRELOAD EXECUTION: PASS
ALR SHELL DPKG ARCH PRELOAD EXECUTION: PASS
ALR INSTALLED PACKAGE PRELOAD EXECUTION: PASS
alr installed package preload execve attempts=alr handoff execve attempt count=6
alr installed package preload execve loader rewrites=alr handoff execve loader rewrite count=3
alr installed package preload traced processes=alr handoff traced process count=5
alr installed package preload stdout=alr local deb package smoke ok\nALR_SMOKE_PACKAGE_SCRIPT=1\nALR_SMOKE_ARCH=arm64\nALR_SMOKE_ENV_ARCH=arm64
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
```

The V70 result adds an env-mediated launch path to the installed package smoke.
This exercises a launcher shape common in package-installed Linux tools:
script entrypoint, direct child command, `/usr/bin/env` dispatch, and final
target child command. ALR keeps the preload/rootfs context alive across the
chain and rewrites the necessary child execs through the glibc loader.

Known issue:

- V35 summary says `GUEST WAYLAND GUI GPU BRIDGE EXECUTION: FAIL` and `GUEST X11 GUI GPU BRIDGE EXECUTION: FAIL` because ACK writing happens after socket input is closed. Frames were received and rendered losslessly; this is a report/ACK lifecycle bug, not a GPU-path failure.

Major unresolved runtime issue:

- `dpkg -i local deb` still fails under current PRoot backend:

```text
dpkg-split: Function not implemented
status-old: Permission denied
```

This points to PRoot/fakeroot/path/syscall limitations and is one reason to evaluate a proroot-style backend.

---

## Architecture Target

```text
Android APK
├─ UI / Activity / Surface owner
├─ Execution backend manager
│  ├─ proot backend: current compatibility baseline
│  ├─ proroot backend probe: optional binary-backed experiment
│  └─ alr runtime backend: our future open implementation
├─ Rootfs manager
│  ├─ verified tar payloads
│  ├─ app-private extracted rootfs
│  └─ package-manager state
├─ Graphics host services
│  ├─ EGL/GLES Surface renderer
│  ├─ Vulkan Surface renderer, later
│  ├─ GUI compositor bridge
│  └─ frame/latency/loss metrics
└─ Linux guest rootfs
   ├─ userland apps: shell, apt, Python, Node, Chromium, etc.
   ├─ Wayland/X11 client/proxy tools
   ├─ libEGL/libGLES shim
   ├─ future Vulkan ICD shim
   └─ IPC clients to Android host services
```

Core rule:

```text
Linux execution should be low-overhead.
GPU presentation must end on Android Surface/EGL/Vulkan.
Do not rely on guest Linux /dev/dri as the generic solution.
```

---

## Difference From coderredlab/proroot

`coderredlab/proroot` is useful but solves a different layer.

### proroot

Purpose:

- Rootless Linux execution runtime for Android.
- Drop-in PRoot replacement.
- Avoid ptrace overhead with LD_PRELOAD, binary patching, syscall trampolines, and a clean-room glibc-compatible linker.

Relevant components:

```text
libproroot.so           Android/Bionic launcher
libproroot-runtime.so   glibc LD_PRELOAD runtime
libproroot-bridge.so    child exec trampoline
libproroot-linker.so    clean-room glibc-compatible dynamic linker
```

Strength:

- Potentially much faster than ptrace PRoot.
- Claims tested Node, Python, Git, npm, Chromium/Playwright, XFCE/VNC, apt-style workloads.
- Includes wrappers for open/stat/exec/fork/clone/connect/dlopen/fakeroot/procfs/link handling.

Limitations:

- Source is not public.
- Proprietary license; modified binary redistribution not allowed.
- Does not directly solve Android Surface GPU bridge.
- Debuggability is limited to black-box tests, symbols, strings, and runtime logs.

### Our runtime

Purpose:

- Non-root APK Linux runtime plus Android-native GPU/GUI acceleration.
- Guest GUI/GPU commands proxy to Android host renderer.
- Long-term target: Linux apps run with low overhead and render through Android GPU APIs.

Therefore:

```text
proroot can become an optional execution backend.
It does not replace our GPU bridge.
The ideal system may use proroot-style execution + our Android GPU host bridge.
```

---

## Roadmap by Five-Version Bundles

## V36–V40: Make GUI GPU Proof Clean and Trustworthy

**Goal:** Convert V35 functional GPU proof into clean summary PASS with ACK, seq, loss, and per-protocol reporting.

### Task V36: Fix GUI IPC ACK lifecycle

**Objective:** Remove false `SocketException: Socket is closed` after frame reception.

**Files:**

- Modify: `app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt`
- Test: `tests/test_android_guest_gpu_bridge.py`

**Implementation:**

- Do not use `socket.getInputStream().bufferedReader().useLines { ... }` if ACK must be written after reading.
- Keep socket open until after ACK write.
- Read lines manually or use reader without closing the socket prematurely.
- Write ACK before closing socket/output.

Expected report:

```text
guest wayland gui ipc error=none
guest x11 gui ipc error=none
```

### Task V37: Add explicit GUI ACK stdout

**Objective:** Make guest clients print ACK evidence.

**Files:**

- Modify rootfs payload generation for:
  - `/usr/bin/alr-wayland-gpu-client`
  - `/usr/bin/alr-x11-gpu-client`
- Modify tests checking rootfs content.

Expected guest stdout:

```text
ALR_GUI_IPC_CLIENT ok sent=4 transport=tcp-loopback ack=ALR_GUI_IPC_ACK protocol=WAYLAND received=4 lossless=true
ALR_GUI_IPC_CLIENT ok sent=4 transport=tcp-loopback ack=ALR_GUI_IPC_ACK protocol=X11 received=4 lossless=true
```

### Task V38: Add seq gap and loss metrics

**Objective:** Track loss beyond simple count matching.

Metrics:

```text
guest wayland gui ipc expected seq=1..4
guest wayland gui ipc seq gaps=0
guest wayland gui ipc duplicate seq=0
guest wayland gui ipc out of order=false
guest wayland gui ipc lossless=true
```

Same for X11.

### Task V39: Add per-protocol Surface render counts

**Objective:** Prove Wayland-style and X11-style frames both rendered on host GPU.

Expected Surface report:

```text
surface wayland frames rendered=4
surface x11 frames rendered=4
surface gui total frames rendered=8
surface frames dropped=0
surface render elapsed us=2930426
surface average frame render us=12740
surface gles shim render elapsed us=548132
surface gles shim average frame render us=16121
surface native gles frames rendered=32
surface native gles render elapsed us=490015
surface native gles average frame render us=15312
surface gles shim vs native average ratio pct=105
surface frame lossless=true
```

### Task V40: Build, verify, and deliver APK

Commands:

```bash
python3 -m pytest tests -q
scripts/test-native-core.sh
./gradlew :app:assembleDebug --no-daemon
```

APK name:

```text
dist/androlinux-gui-gpu-proof-clean-v40.apk
```

Acceptance criteria on device:

```text
GUEST WAYLAND GUI GPU BRIDGE EXECUTION: PASS
GUEST X11 GUI GPU BRIDGE EXECUTION: PASS
GUEST GUI GPU SURFACE EXECUTION: PASS or Surface callback PASS section present
guest wayland/x11 gui gpu surface hardware render=true
surface gpu hardware render=true
surface frames dropped=0
surface render elapsed us=2930426
surface average frame render us=12740
surface gles shim render elapsed us=548132
surface gles shim average frame render us=16121
surface native gles frames rendered=32
surface native gles render elapsed us=490015
surface native gles average frame render us=15312
surface gles shim vs native average ratio pct=105
```

Latest device check for build `0.4.46-gpu-surface-callback-evidence` satisfies this
gate through the callback update section:

```text
HOST GPU SURFACE EXECUTION UPDATE: PASS
GUEST GPU MULTI-FRAME SURFACE EXECUTION UPDATE: PASS
GUEST GUI GPU SURFACE EXECUTION UPDATE: PASS
surface callback frames rendered=16
surface callback hardware render=true
surface gl renderer=Mali-G615 MC2
surface frames rendered=16
surface frames dropped=0
surface render elapsed us=2930426
surface average frame render us=12740
surface gles shim render elapsed us=548132
surface gles shim average frame render us=16121
surface native gles frames rendered=32
surface native gles render elapsed us=490015
surface native gles average frame render us=15312
surface gles shim vs native average ratio pct=105
surface frame lossless=true
surface gpu hardware render=true
guest wayland/x11 gui gpu surface hardware render=true
surface wayland frames rendered=8
surface x11 frames rendered=8
```

---

## V41–V45: Add Optional Proroot Backend Probe

**Goal:** Evaluate whether proroot-style execution reduces overhead and fixes dpkg/package-manager mutation failures.

### Task V41: Package proroot binaries as optional backend

**Files:**

- Add optional prebuilts under a clearly named directory, preserving upstream filenames.
- Record source URL, release tag, sha256, and license note in docs.

Important:

- Do not modify proroot binaries.
- Mark as optional/proprietary external backend.
- Keep current PRoot backend as baseline.

### Task V42: Add backend selector and report lines

Expected summary:

```text
PROOT BACKEND EXECUTION: PASS
PROROOT BACKEND AVAILABLE: PASS/FAIL
PROROOT VERSION/HELP EXECUTION: PASS/FAIL
```

### Task V43: Run minimal rootfs hello through proroot

Expected report:

```text
proroot hello exit=0
proroot hello stdout=hello from static arm64 rootfs
PROROOT ROOTFS EXECUTION: PASS
```

### Task V44: A/B test dpkg/apt workload

Compare:

```text
proot dpkg -i local deb exit=2
proroot dpkg -i local deb exit=?
```

Acceptance:

- If proroot passes local deb install, prioritize it for package-manager workloads.
- If proroot fails, collect exact stderr and continue with our own backend research.

### Task V45: Deliver A/B runtime APK

APK:

```text
dist/androlinux-proroot-ab-runtime-v45.apk
```

Device acceptance:

```text
PROROOT ROOTFS EXECUTION: PASS
PROROOT DPKG LOCAL INSTALL EXECUTION: PASS/FAIL with detailed reason
```

---

## V46–V50: Start Our Own Clean-Room Low-Overhead Runtime Backend

**Goal:** Begin replacing ptrace-heavy PRoot for hot paths with our own open implementation, inspired only by independently observed behavior and public Linux/Android/glibc interfaces. Do not copy proroot binaries, private code, or non-public implementation details.

### Clean-room rule

Use a two-track process:

```text
Spec track:
  Observe public behavior from Linux APIs, Android constraints, PRoot behavior, device logs, and optional black-box proroot A/B outputs.
  Write ALR runtime requirements and tests without implementation code from proroot.

Implementation track:
  Implement from our own tests/specs using Android NDK, public syscall ABI docs, glibc behavior, and our existing rootfs/runtime code.
```

Allowed references:

- Public Linux syscall ABI and man pages.
- Android NDK/Bionic behavior.
- glibc dynamic loader behavior as observed from public docs and our own rootfs tests.
- Our own device logs and tests.
- Black-box command input/output comparisons, without disassembling or copying proprietary algorithms.

Do not depend on proroot as the long-term implementation. Use it only as an optional benchmark/probe to validate whether the class of approach is worthwhile.

### Design

Create `alr-runtime` as an open, incremental backend:

```text
libalr_runtime_launcher.so   Android/Bionic launcher packaged in nativeLibraryDir
libalr_runtime_hook.so       glibc LD_PRELOAD path/syscall shim inside guest process
libalr_runtime_bridge.so     child exec trampoline for execve/posix_spawn continuity
libalr_runtime_linker.so     optional later clean-room loader only if glibc loader pathing requires it
```

Initial scope is deliberately smaller than a full proroot-style runtime:

- `open/openat/stat/newfstatat/access/readlink/getcwd/chdir`
- `execve`, `execvp`, `posix_spawn` wrapping for child processes
- fake root identity for `getuid/getgid/geteuid/getegid/stat/chown` basics
- procfs minimal virtualization for `/proc/self`, `/proc/self/exe`, `/proc/mounts`, `/proc/cpuinfo`
- path translation for rootfs, cwd, bind-like mappings, and app-private temp dirs
- no aggressive binary patching until LD_PRELOAD wrapper coverage proves insufficient

### Overhead target

The intended overhead reduction is:

```text
current PRoot:
  ptrace trap/context-switch per intercepted syscall

ALR runtime v1:
  in-process wrapper + direct syscall for common filesystem/process calls
  V56 measured open/read preload fast path: 6 us vs PRoot 201 us
  V57 measured stat preload fast path: 2 us vs PRoot 251 us

ALR runtime v2:
  selective raw-syscall patch/trampoline only for calls that bypass libc wrappers
```

Expected win:

- CLI/userland startup becomes faster.
- npm/node/python/git child-process workloads improve.
- dpkg/apt mutation may avoid some PRoot `Function not implemented` failures.
- GUI/GPU bridge becomes less CPU-bound because guest command producers spend less time in ptrace.

### Acceptance

```text
ALR LOW-OVERHEAD RUNTIME HELLO EXECUTION: PASS
ALR LOW-OVERHEAD RUNTIME SHELL EXECUTION: PASS
ALR LOW-OVERHEAD RUNTIME CHILD EXECUTION: PASS
ALR LOW-OVERHEAD RUNTIME DPKG PREFLIGHT: PASS/FAIL with reason
ALR LOW-OVERHEAD RUNTIME SYSCALL OVERHEAD SMOKE: lower than PRoot baseline
```

### Non-goals for V46–V50

- Full Chromium compatibility.
- Full binary patching coverage.
- Full dynamic linker replacement.
- Full X11/Wayland protocol compatibility.
- Full Vulkan/GL ABI compatibility.

Those come after basic shell/child-process/package-manager correctness is proven.

---

## V51–V55: Real GUI Bridge Shape

**Goal:** Move from Wayland/X11-style smoke frames to more realistic GUI protocol bridging.

Wayland path:

- Provide a guest `WAYLAND_DISPLAY` endpoint.
- Implement minimal object/message framing for a tiny subset or proxy shape.
- Translate commit-like events into host compositor frames.

X11 path:

- Provide a guest `DISPLAY` endpoint.
- Start with a tiny X11 proxy or Xvfb/VNC comparison path.
- Extract drawable/window updates into host compositor commands.

Acceptance:

```text
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
X11 DISPLAY SOCKET AVAILABLE: PASS
GUEST GUI APP FRAME COMMIT EXECUTION: PASS
ANDROID GPU SURFACE COMPOSITOR EXECUTION: PASS
```

---

## V56–V60: GLES/EGL Shim Expansion

**Goal:** Make Linux GL apps talk to a guest-side libEGL/libGLES shim that forwards commands to Android host renderer.

Start with a constrained API subset:

- `eglGetDisplay`
- `eglInitialize`
- `eglChooseConfig`
- `eglCreateContext`
- `eglMakeCurrent`
- `glClearColor`
- `glClear`
- `eglSwapBuffers`

Acceptance:

```text
GUEST EGL INIT VIA SHIM: PASS
GUEST GLES CLEAR VIA SHIM: PASS
GUEST EGL SWAP VIA ANDROID SURFACE: PASS
GUEST GLES HARDWARE RENDER: PASS
```

Current device evidence from build `0.4.53-gles-procaddr-demo`:

```text
GUEST GLES SHIM SMOKE EXECUTION: PASS
ALR GUEST GLES SHIM SMOKE EXECUTION: PASS
GUEST EGL/GLES ABI LIB EXECUTION: PASS
ALR GUEST EGL/GLES ABI LIB EXECUTION: PASS
GUEST GLES DEMO GEARS EXECUTION: PASS
ALR GUEST GLES DEMO GEARS EXECUTION: PASS
GUEST GLES PROCADDR DEMO EXECUTION: PASS
ALR GUEST GLES PROCADDR DEMO EXECUTION: PASS
GUEST EGL INIT VIA SHIM EXECUTION: PASS
GUEST EGL CONTEXT VIA SHIM EXECUTION: PASS
GUEST GLES CLEAR VIA SHIM EXECUTION: PASS
GUEST EGL SWAP COMMAND VIA SHIM EXECUTION: PASS
GUEST GLES SHIM FRAME WORKLOAD EXECUTION: PASS
ALR GUEST GLES SHIM FRAME WORKLOAD EXECUTION: PASS
GUEST GLES DRAW VIA SHIM EXECUTION: PASS
ALR GUEST GLES DRAW VIA SHIM EXECUTION: PASS
GUEST EGL INIT VIA SHIM UPDATE: PASS
GUEST EGL CONTEXT VIA SHIM UPDATE: PASS
GUEST GLES CLEAR VIA SHIM UPDATE: PASS
GUEST GLES DRAW VIA SHIM UPDATE: PASS
GUEST EGL SWAP VIA ANDROID SURFACE UPDATE: PASS
GUEST GLES HARDWARE RENDER UPDATE: PASS
ALR_GLES_API_STEP eglGetDisplay ok
ALR_GLES_API_STEP eglInitialize ok
ALR_GLES_API_STEP eglChooseConfig ok
ALR_GLES_API_STEP eglCreateContext ok
ALR_GLES_API_STEP eglMakeCurrent ok
ALR_GLES_API_STEP glViewport ok
ALR_GLES_API_STEP glClearColor ok
ALR_GLES_API_STEP glClear ok
ALR_GLES_API_STEP glUseProgram ok
ALR_GLES_API_STEP glEnableVertexAttribArray ok
ALR_GLES_API_STEP glVertexAttribPointer ok
ALR_GLES_API_STEP glDrawArrays ok
ALR_GLES_API_STEP eglSwapBuffers ok
ALR_GLES_ABI_LIBS visible libEGL.so libGLESv2.so
ALR_GLES_ABI_STEP eglGetProcAddress ok
ALR_GLES_ABI_STEP eglSwapBuffersDraw ok
ALR_GLES_DEMO_KIND es2gears-like-triangle-strip-subset
ALR_GLES_DEMO_WORKLOAD requested=60 submitted=60
ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset
ALR_GLES_PROC_DEMO_WORKLOAD requested=45 submitted=45
surface frames rendered=230
surface frames dropped=0
surface gles shim render elapsed us=576046
surface gles shim average frame render us=16001
surface gles shim draw frames rendered=140
surface gles shim draw render elapsed us=1656444
surface gles shim draw average frame render us=11831
surface native gles frames rendered=32
surface native gles render elapsed us=348498
surface native gles average frame render us=10890
surface gles shim vs native average ratio pct=146
surface frame lossless=true
surface gles shim frames rendered=36
guest egl swap via android surface=true
guest gles hardware render=true
guest gles draw via android surface=true
```

---

## V61–V70: Buffer/Texture Transport and Frame Pacing

**Goal:** Move beyond clear-color commands into real frame buffers/textures while minimizing copies.

Stages:

1. CPU shared memory frame upload.
2. Tiled/dirty-rect updates.
3. Android `AHardwareBuffer` exploration.
4. Fence/ACK frame pacing.
5. Latency measurements.

Metrics:

```text
frames submitted
frames rendered
frames dropped
avg submit-to-render ms
p95 submit-to-render ms
copy count estimate
shared buffer bytes/frame
```

Acceptance:

```text
GUEST GUI BUFFER FRAME EXECUTION: PASS
ANDROID GPU TEXTURE UPLOAD EXECUTION: PASS
FRAME PACING LOSSLESS EXECUTION: PASS
```

---

## V71–V80: Vulkan Host Renderer and ICD Proxy Research

**Goal:** Add Vulkan host path after GLES proof is reliable.

Do not begin with full DXVK/Wine. Start with:

- Android Vulkan instance/device/swapchain proof.
- Guest Vulkan ICD stub that reports basic info.
- One simple clear/present path.

Acceptance:

```text
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
GUEST VULKAN ICD SMOKE EXECUTION: PASS
GUEST VULKAN CLEAR PRESENT EXECUTION: PASS
```

---

## V81+: Real App Compatibility Track

Target app classes in this order:

1. CLI apps: shell, git, Python, Node, npm.
2. Package manager: dpkg/apt local install and network install.
3. Simple GUI: xeyes-like, GTK hello, Qt hello.
4. Browser engine: Chromium/Playwright smoke.
5. GPU demos: glmark2-style subset, es2gears equivalent.
6. Heavy apps only after metrics are stable.

Acceptance for each app:

```text
app launches
input works
frame renders via Android GPU
renderer is not SwiftShader/llvmpipe/softpipe
frame loss/dropped metrics acceptable
latency measured
```

---

## Non-Negotiable Verification Rules

- Never claim native-grade acceleration from APK build alone.
- Device logs are source of truth.
- Always report renderer string and software-renderer rejection.
- Keep PRoot and new runtime backends A/B comparable.
- Keep APK names unique per bundle.
- Deliver APK at every five-version boundary.
- If a summary says FAIL but detailed evidence says PASS, fix the summary/reporting before adding more layers.

---

## Immediate Next Step

Continue from the V71 installed-package GPU IPC proof:

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
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
```

V74 follow-up batch targets:

1. Package a source-built installed GLES shim demo binary through `.deb`, not only the tiny GPU clear client.
2. Add installed-package GLES shim IPC and Android Surface presentation gates.
3. Start replacing stale non-preload dpkg/proot install checks with clearly labeled known-fail diagnostics so the summary stays truthful.
4. Add measured installed-package GPU timing against the native GLES baseline already reported on device.

Latest V72 installed-package GLES shim evidence:

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
alr installed package gles demo command parsed count=60
alr installed package gles demo draw command count=60
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=99
```

Next implementation batch:

1. Turn the installed-package GLES proof into a TCP/Surface bridge with explicit submit/ack timing.
2. Package a second installed ELF that exercises `eglGetProcAddress` and multiple GLES symbols.
3. Make the summary separate hard known-fail diagnostics from the current PASS path, especially non-preload dpkg/proot install failures.
4. Start a small installed-app compatibility table for CLI, GPU clear, GLES demo, and GUI protocol clients.

Latest V73 installed-package GLES IPC evidence:

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

Next implementation batch:

1. Add host-to-guest ACK lines for the GLES TCP bridge and report submit-to-ack timing.
2. Package an installed `eglGetProcAddress` demo as a second `.deb` app entrypoint.
3. Add installed-package compatibility rows for shell script, GPU clear, GLES demo stdout, GLES demo TCP, procaddr, and GUI protocol clients.
4. Split known-fail legacy dpkg/proot diagnostics away from the current ALR summary so PASS/FAIL status reflects the active backend path.

Latest V75 installed-package GLES procaddr evidence:

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

Next implementation batch:

Latest V76 installed-package Wayland/X11 GUI evidence:

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
installed package compatibility table=script:PASS,gpu-clear-ipc:PASS,gles-demo:PASS,gles-tcp-ack:PASS,gles-procaddr:PASS,wayland:PASS,x11:PASS
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=100
```

Latest V80 guest-requested Vulkan Surface clear evidence:

```text
build: 0.4.80-guest-vulkan-clear-request
versionCode=80
versionName=0.4.80-guest-vulkan-clear-request
HOST VULKAN DISCOVERY EXECUTION: PASS
ALR INSTALLED PACKAGE VULKAN DISCOVERY EXECUTION: PASS
rootfs installed alr vulkan discovery client exists=true executable=true bytes=7384
alr installed package vulkan discovery raw=ALR_VK_DISCOVERY_HELLO version=1 request=instance-device
alr installed package vulkan surface clear request=ALR_VK_SURFACE_CLEAR_REQUEST version=1 red=0.12 green=0.64 blue=0.92 alpha=1.0 tag=guest-vulkan-clear-0001
alr installed package vulkan surface clear accepted=ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS request=guest-wsi-clear-v1
alr installed package vulkan discovery ack=ALR_VK_DISCOVERY_ACK status=PASS physical_devices=1 hardware=true device=Mali-G615_MC2
alr installed package vulkan discovery device record=ALR_VK_DEVICE_RECORD name=Mali-G615_MC2 api=1.3.247 type=integrated-gpu physical_devices=1 queue_families=1 graphics_queue=0
alr installed package vulkan discovery feature record=ALR_VK_FEATURE_RECORD robust_buffer_access=true geometry_shader=true sampler_anisotropy=true max_image_2d=16384 max_memory_allocations=16384
alr installed package vulkan discovery ack lines=ALR_VK_DISCOVERY_ACK status=PASS physical_devices=1 hardware=true device=Mali-G615_MC2|ALR_VK_DEVICE_RECORD name=Mali-G615_MC2 api=1.3.247 type=integrated-gpu physical_devices=1 queue_families=1 graphics_queue=0|ALR_VK_FEATURE_RECORD robust_buffer_access=true geometry_shader=true sampler_anisotropy=true max_image_2d=16384 max_memory_allocations=16384|ALR_VK_SURFACE_CLEAR_ACCEPTED status=PASS request=guest-wsi-clear-v1
alr installed package vulkan discovery handoff=ALR STATIC ENTRY HANDOFF: PASS
alr installed package vulkan discovery stdout=alr guest vulkan discovery client ok ... ALR_VK_DISCOVERY_DEVICE_RECORD ok ... ALR_VK_DISCOVERY_FEATURE_RECORD ok ... ALR_VK_SURFACE_CLEAR_REQUEST_ACCEPTED ok
host vulkan device=host vulkan device=Mali-G615 MC2
host vulkan hardware candidate=host vulkan hardware candidate=true
installed package compatibility table=script:PASS,gpu-clear-ipc:PASS,gles-demo:PASS,gles-tcp-ack:PASS,gles-procaddr:PASS,wayland:PASS,x11:PASS,vulkan-discovery:PASS
surface gl renderer=Mali-G615 MC2
surface gpu hardware render=true
surface gles shim vs native average ratio pct=100
GUEST VULKAN SURFACE CLEAR REQUEST EXECUTION: PASS
ANDROID HOST VULKAN SURFACE EXECUTION: PASS
surface vulkan clear request source=guest-request
surface vulkan clear request tag=guest-vulkan-clear-0001
surface vulkan device=Mali-G615 MC2
surface vulkan api version=1.3.247
surface vulkan graphics present queue=0
surface vulkan present mode=mailbox
surface vulkan swapchain image count=7
surface vulkan clear command=ok color=0.12,0.64,0.92,1
surface vulkan queue submit=ok
surface vulkan present=ok
surface vulkan hardware render=true
surface vulkan render elapsed us=19542
```

Next implementation batch:

Latest V81 guest Vulkan proxy smoke evidence:

```text
build: 0.4.81-guest-vulkan-proxy-smoke
versionCode=81
versionName=0.4.81-guest-vulkan-proxy-smoke
rootfs_version=bookworm-slim-2026-05-gui-gpu-v81
rootfs sha256=c67ec2fbf8e6d882d6f28cc4aab29d6e6658f1eb3ebe64b1d579b4f8a991a120
rootfs size bytes=35860480
rootfs alr-vulkan-proxy-smoke bytes=5920
rootfs libvulkan.so.1 bytes=5256
ALR_VK_PROXY_STEP vkEnumerateInstanceVersion ok api=1.3.247
ALR_VK_PROXY_SURFACE_CLEAR_REQUEST_ACCEPTED ok
ALR_VK_PROXY_DONE ok
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
surface gpu hardware render=true
surface gles shim vs native average ratio pct=100
```

Latest V82 bounded binary Vulkan proxy bridge evidence:

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
surface gpu hardware render=true
surface gles shim vs native average ratio pct=99
```

Latest V83 Vulkan ICD manifest discovery evidence:

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
surface gpu hardware render=true
surface gles shim vs native average ratio pct=100
```

Latest V84 Vulkan loader-info discovery evidence:

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

Latest V85 Unix-domain Vulkan loader bridge evidence:

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

Latest V86 GLES Unix-domain bridge evidence:

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

Latest V87 GLES Unix-domain batch bridge evidence:

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

V87 is the first GLES bridge step that clearly reduces synchronization overhead
instead of only moving transport shape. It preserves the TCP and per-frame Unix
ACK paths as baselines, while proving a single-ACK 60-frame GLES batch can still
feed the Android-native Surface renderer and coexist with the Vulkan Unix loader
path.

Latest V88 Wayland/X11 GUI Unix-domain bridge evidence:

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

V88 removes another opaque prebuilt from the graphics path by replacing the
Wayland/X11-shaped clients with source-built static clients, then proves those
clients can use Android `LocalServerSocket` Unix transport while still feeding
the Surface renderer. This makes the next GUI step less about transport and more
about moving from smoke frames toward a minimal real Wayland socket/proxy shape.

Latest V89 minimal `WAYLAND_DISPLAY` bridge evidence:

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
surface wayland frames rendered=19
surface x11 frames rendered=16
surface vulkan present=ok
surface vulkan hardware render=true
```

V89 completes the first item from the previous batch: Android now exposes a
minimal app-private `WAYLAND_DISPLAY`-style socket and a source-built guest
client performs connect, registry, bind, surface create, buffer create, and
three surface commit records through ALR. The bridge still uses a constrained
text protocol, but the app-facing shape is intentionally closer to what real
Linux GUI launchers expect: `WAYLAND_DISPLAY`, `XDG_RUNTIME_DIR`, and a Unix
socket endpoint instead of a generic frame-smoke client.

Latest V90 Wayland shared-payload bridge evidence:

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

V90 completes the first shared-payload step. The current bridge uses a
file-backed app-private staging buffer because it is available to an ordinary
APK UID and can be verified today. The next upgrade should keep the same
`ALR_WL_SHM_POOL_CREATE`/`ALR_WL_BUFFER_ATTACH` contract while replacing the
backing store with ashmem/memfd-style FD passing or Android-owned
`AHardwareBuffer` handles.

Latest V92 Wayland triple-FD payload bridge evidence:

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

V92 completes the memfd/`SCM_RIGHTS` part of the previous batch and makes it
frame-indexed instead of a single reusable smoke FD. The bridge still uses a
clean-room ALR text envelope around Wayland-shaped events, but the important
buffer-transport behavior is now closer to the real direction: control messages
carry object IDs and indexes while image bytes travel through file descriptors.

Latest V93 Android host `AHardwareBuffer` evidence:

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

V93 completes the first Android-owned native-buffer experiment from the previous
batch. The current state now has two validated halves: guest-to-host frame
payload descriptors via v92 memfd `SCM_RIGHTS`, and host-managed GPU-importable
triple buffers via v93 `AHardwareBuffer` EGLImage import.

Latest V94 Wayland display AHardwareBuffer backing evidence:

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

V94 joins those halves for the first time. The installed package
`WAYLAND_DISPLAY` bridge still receives and verifies guest FD payloads, but the
same parsed Wayland display commits are now replayed into host-owned
`AHardwareBuffer` objects and imported by EGL. The next implementation should
make that backing mode part of the bridge state machine rather than a parallel
verification pass.

Latest V95 Wayland AHardwareBuffer dirty-state evidence:

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
surface vulkan present=ok
surface vulkan hardware render=true
```

V95 finishes the two highest-priority items from the previous batch in one
implementation pass. `ALR_WL_BUFFER_ATTACH` now directly selects
`backing=host-ahardwarebuffer`, `ALR_WL_DAMAGE` carries dirty-rectangle state,
Android validates that state before ACK, and the native host buffer path updates
only the 25% dirty area while keeping the full v92 memfd payload stream as a
fallback verifier.

Latest V96 Wayland AHardwareBuffer Surface compositor evidence:

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
wayland ahardwarebuffer surface dirty rect bytes=172800
wayland ahardwarebuffer surface partial upload ratio pct=25
wayland ahardwarebuffer surface sync fence accounting=ok
wayland ahardwarebuffer surface hardware render=true
surface vulkan hardware render=true
```

V96 collapses three follow-up tasks into one implementation batch: the
AHardwareBuffer dirty state is sampled through an EGLImage texture into the real
Android Surface, AHardwareBuffer lock/unlock fence accounting is logged beside
the dirty-region counters, and the first adb verifier script captures the
minimum device evidence for the GUI/GPU path.

Latest V97 Wayland AHardwareBuffer pool evidence:

```text
build: 0.4.97-wayland-ahb-pool
versionCode=97
versionName=0.4.97-wayland-ahb-pool
WAYLAND DISPLAY SOCKET AVAILABLE: PASS
WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS
WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS
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
surface vulkan hardware render=true
```

V97 replaces the one-shot v96 Surface compositor allocation pattern with a
three-slot Android-native buffer pool. The current evidence proves the pool can
hold resident `AHardwareBuffer`/EGLImage/texture objects, reuse all three slots
on a second presentation pass, and feed slot-local fence FDs into the next
dirty-rect lock whenever the device returns them.

Latest V98 Wayland wire-subset evidence:

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
WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS
WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS
wayland ahardwarebuffer surface buffer pool mode=slot-reuse
wayland ahardwarebuffer surface buffer pool reuses=3
wayland ahardwarebuffer surface sampled frames=6
wayland ahardwarebuffer surface presented frames=6
wayland ahardwarebuffer surface hardware render=true
```

V98 keeps the existing high-level Wayland bridge stable while introducing an
ordered wire-header subset trace. This gives the next batch a concrete target:
replace textual `ALR_WL_WIRE` summaries with binary request decoding without
losing the already-verified AHardwareBuffer and Surface evidence path.

Latest V99 Wayland binary-decode evidence:

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
ALR_WL_DISPLAY_ACK ... binary_messages=15 binary_header_ready=true binary_subset_ready=true ...
WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS
wayland ahardwarebuffer surface buffer pool mode=slot-reuse
wayland ahardwarebuffer surface buffer pool reuses=3
wayland ahardwarebuffer surface sampled frames=6
wayland ahardwarebuffer surface presented frames=6
wayland ahardwarebuffer surface hardware render=true
```

V99 converts the v98 ordered wire summary into an actual raw request-byte
decoder for the current registry/compositor/shm/surface subset. This is still a
bounded clean-room subset, but it removes one layer of fake text protocol from
the Wayland path and gives later versions a real parser boundary to grow.

Android verifier:

```text
Android v99 Wayland binary decode verification passed.
```

Implementation compression window:

- v99-v118 are the forced completion window. No more small evidence-only
  versions: every remaining version must close one runtime capability or merge
  several related tasks into one shipping implementation.
- V118 is accepted only if a simple Linux/glibc GUI application demo launches
  without root on Android and visibly presents through the Plib Android
  Surface/GPU bridge. It may be a constrained fixture or tiny toolkit app; it
  does not need to prove full desktop compatibility.
- Graphics must move from replay/probe evidence into continuous guest commits,
  real Wayland client compatibility, and Android-native Vulkan/GLES paths.
- Execution must move from scattered smoke probes into a practical preload/fd
  broker backend with package-manager, procfs, identity, and child-process
  coverage that can run real app launchers without defaulting to PRoot.
- The final versions in this window must spend their budget on integration,
  correctness, and measured overhead against PRoot/proroot-class baselines, not
  on more isolated protocol demonstrations.

Next implementation batch:

1. Replace the replay-based pool proof with real continuous guest commits and long-lived buffer reuse across Surface callbacks.
2. Add the simple Linux/glibc GUI demo track now, not at the end: a small guest app should use the real binary Wayland subset and draw into the Android Surface path.
3. Replace the loader-info smoke with the real Khronos Vulkan loader or a stricter ABI-compatible loader subset.
4. Add a small real toolkit fixture target, likely a tiny GTK/Qt-independent Wayland protocol smoke before pulling in a larger GUI stack.
5. Start measuring compositor-path CPU/GPU cost against PRoot image transport and the existing Vulkan Surface clear path.
6. Keep growing adb verification around visual present, fence state, package-version evidence, and the V118 GUI demo gate so device regressions stay cheap while implementation is moving quickly.
