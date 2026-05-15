# Android Device Evidence Capture Template

Use this template when collecting real Android evidence for Hermes-owned device/probe workstreams.

Related issues:
- #1 Optional low-overhead backend A/B probe and device evidence
- #3 GUI/GPU Surface bridge baseline evidence

## Clean-room rules

- Optional proroot-class runtimes are black-box probes only.
- Do not disassemble, decompile, patch, or reconstruct proprietary implementation details.
- Record only command inputs, stdout, stderr, exit codes, elapsed time, device logs, screenshots, and public metadata.
- Do not edit `app/src/main/cpp/alr_runtime/` based on optional backend behavior.

## Session metadata

- Date/time:
- Operator/agent:
- Branch:
- Git commit:
- APK path:
- APK build stamp visible in app:
- Device model:
- Android release:
- Android SDK:
- ABI:
- GPU/vendor/renderer if visible:

## Build/install commands

```bash
./gradlew :app:assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
```

Results:
- Gradle exit code:
- Install exit code:
- Install stdout/stderr summary:

## App report capture

Attach or paste:
- Screenshot path(s):
- Logcat path:
- App report text path:

Minimum report lines to capture:

```text
build: <stamp>
ALR RUNTIME LAUNCHER AVAILABLE: PASS/FAIL/SKIP
ALR RUNTIME CONFIG BUILD: PASS/FAIL/SKIP
ALR RUNTIME DIRECT APP-DATA EXEC POLICY: PASS/FAIL/SKIP
ALR HOOK LOAD: PASS/FAIL/SKIP
ALR HOOK CONFIG BUILD: PASS/FAIL/SKIP
ALR HOOK STAT/OPEN ROOTFS FILE: PASS/FAIL/SKIP
ALR INTERPOSER LOAD: PASS/FAIL/SKIP
ALR INTERPOSER CONFIG BUILD: PASS/FAIL/SKIP
ALR INTERPOSER STAT PATH: PASS/FAIL/SKIP
ALR INTERPOSER OPEN PATH: PASS/FAIL/SKIP
ALR CONFIG SERIALIZE: PASS/FAIL/SKIP
ALR CONFIG PARSE: PASS/FAIL/SKIP
ALR_CONFIG_FORMAT: alr-config-v1/other
ALR_INTERPOSER_PATH: <path>
ALR_TRACE_PATH: 0/1
ALR_TRACE_EXEC: 0/1
ROOTFS EXECUTION: PASS/FAIL/SKIP
SHELL SCRIPT EXECUTION: PASS/FAIL/SKIP
SHELL -C EXECUTION: PASS/FAIL/SKIP
GLIBC DYNAMIC EXECUTION: PASS/FAIL/SKIP
IDENTITY NSS EXECUTION: PASS/FAIL/SKIP
DPKG VERSION EXECUTION: PASS/FAIL/SKIP
DPKG ARCH EXECUTION: PASS/FAIL/SKIP
APT VERSION EXECUTION: PASS/FAIL/SKIP
DPKG LOCAL INSTALL EXECUTION: PASS/FAIL/SKIP
INSTALLED PACKAGE EXECUTION: PASS/FAIL/SKIP
HOST GPU EGL/GLES EXECUTION: PASS/FAIL/SKIP
HOST GPU SURFACE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>/PENDING_SURFACE_CALLBACK
GUEST GPU IPC BRIDGE EXECUTION: PASS/FAIL/SKIP
GUEST WAYLAND GUI GPU BRIDGE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>
GUEST X11 GUI GPU BRIDGE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>
GUEST GUI GPU SURFACE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>/PENDING_SURFACE_CALLBACK
```

## PRoot baseline command results

For each command, record exact command/report block, exit code, stdout, stderr, and elapsed time if available.

### `/bin/hello`

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:

### `/bin/sh -c "echo shell-c ok; /bin/hello"`

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:

### `/bin/glibc-hello`

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:

### `/usr/bin/id` with fake root mode

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:

### `/usr/bin/dpkg --version`

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:

### `/usr/bin/dpkg --print-architecture`

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:

### `/usr/bin/apt --version`

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:

### Local deb install smoke

- Classification:
- Exit code:
- Elapsed:
- Stdout summary:
- Stderr summary:
- Installed command smoke result:

## Optional low-overhead backend metadata

Only fill this section if an optional external backend is supplied.

- Backend display name:
- Upstream/source URL:
- Version/tag:
- Original filename(s):
- SHA-256:
- License note:
- Storage path:
- Was binary modified? Must be `no`.
- Why this is clean-room safe:

## Optional low-overhead backend A/B results

Run only the same rootfs commands as the PRoot baseline.

- `OPTIONAL LOW-OVERHEAD BACKEND AVAILABLE`: PASS/FAIL/SKIP
- `OPTIONAL LOW-OVERHEAD VERSION EXECUTION`: PASS/FAIL/SKIP
- `OPTIONAL LOW-OVERHEAD ROOTFS EXECUTION`: PASS/FAIL/SKIP
- `OPTIONAL LOW-OVERHEAD DPKG LOCAL INSTALL`: PASS/FAIL/SKIP

For each command, record exact command/report block, exit code, stdout, stderr, and elapsed time.

## GUI/GPU evidence

### Host EGL/GLES

- `HOST GPU EGL/GLES EXECUTION`:
- GL vendor:
- GL renderer:
- GL version:
- Software renderer detected:
- Hardware candidate:

### Android Surface

- `HOST GPU SURFACE EXECUTION`:
- Surface frames submitted:
- Surface frames rendered:
- Surface frames dropped:
- Surface frame lossless:
- Known failure reason, if any:

### Guest GPU IPC bridge

- `GUEST GPU IPC BRIDGE EXECUTION`:
- Expected frames:
- Received frames:
- Dropped frames:
- Lossless:
- Raw IPC lines summary:

### Wayland-style GUI bridge

- `GUEST WAYLAND GUI GPU BRIDGE EXECUTION`:
- Expected frames:
- Received frames:
- Dropped frames:
- Lossless:
- Seq gaps/duplicates/out-of-order:
- Known failure reason, if any:

### X11-style GUI bridge

- `GUEST X11 GUI GPU BRIDGE EXECUTION`:
- Expected frames:
- Received frames:
- Dropped frames:
- Lossless:
- Seq gaps/duplicates/out-of-order:
- Known failure reason, if any:

## Final classification summary

```text
PROOT BACKEND EXECUTION: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD BACKEND AVAILABLE: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD VERSION EXECUTION: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD ROOTFS EXECUTION: PASS/FAIL/SKIP
OPTIONAL LOW-OVERHEAD DPKG LOCAL INSTALL: PASS/FAIL/SKIP
HOST GPU EGL/GLES EXECUTION: PASS/FAIL/SKIP
HOST GPU SURFACE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>
GUEST GPU IPC BRIDGE EXECUTION: PASS/FAIL/SKIP
GUEST WAYLAND GUI GPU BRIDGE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>
GUEST X11 GUI GPU BRIDGE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>
GUEST GUI GPU SURFACE EXECUTION: PASS/FAIL/SKIP/KNOWN_FAIL:<reason>
```

## Blockers and next action

- Blockers:
- Next recommended action for Codex:
- Next recommended action for Hermes:
