# 2026-05-15 Hermes Optional Backend A/B and GUI/GPU Evidence

Related issues:
- #1 Optional low-overhead backend A/B probe and device evidence: https://github.com/Meapri/Plib/issues/1
- #3 GUI/GPU Surface bridge baseline evidence: https://github.com/Meapri/Plib/issues/3

Workstream owner: Hermes

## Clean-room statement

This evidence pass followed `docs/prompts/hermes-proroot-ab-and-device-evidence.md` and `docs/clean-room-protocol.md`.

- Optional proroot-class runtimes are treated only as black-box probes.
- No disassembly, decompilation, control-flow reconstruction, or proprietary algorithm inference was performed.
- No optional low-overhead/proroot-class binary was provided or added in this pass.
- No files under `app/src/main/cpp/alr_runtime/` were edited.
- Observations below are limited to repo state, host command results, Android build/device availability, and report strings that should be captured on a real device.

## Environment

Host:
- OS: `Linux rizi 6.17.0-1011-oracle #11~24.04.1-Ubuntu SMP Fri Apr 10 02:09:09 UTC 2026 aarch64 aarch64 aarch64 GNU/Linux`
- Python: `Python 3.11.15`
- Java: `openjdk version "17.0.18" 2026-01-20`
- Git commit at evidence refresh: `d5aea90` (`origin/main`, Codex Bundle F merged)
- Branch/worktree: `hermes/proroot-ab-probe` at `/home/ubuntu/work/Plib`

Android tooling/device:
- Android SDK: `/home/ubuntu/Android/Sdk` bootstrapped by `./scripts/bootstrap-android-build-host.sh`
- `local.properties`: `sdk.dir=/home/ubuntu/Android/Sdk`, `cmake.dir=/usr` (ignored/local only)
- `adb`: `/home/ubuntu/Android/Sdk/platform-tools/adb`
- Connected Android devices: **SKIP** — `adb devices` listed no attached devices
- APK build: **PASS** — `./gradlew --no-daemon :app:assembleDebug` completed after SDK bootstrap
- APK artifact: `app/build/outputs/apk/debug/app-debug.apk`
- APK SHA-256: `29b300d422f34a0b135a8ddd440535d78a975d267b26d3fbc06f617b3b5b697e`
- APK size: `25567116 bytes`
- APK install/run: **SKIP** — no connected Android device

APK/source build stamp visible in current source:
- `build: 0.4.45-runtime-probe-scaffold-v45` in `app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt`

## Host verification commands

- Command: `python3 -m pytest tests -q`
  - Exit code: `0`
  - Summary: `156 passed in 0.49s`
  - Classification: **PASS**

- Command: `scripts/test-native-core.sh`
  - Exit code: `1` on this Linux/aarch64 VPS
  - Stdout/stderr summary:
    - `native core report and launch plan test ok`
    - `native backend policy test ok`
    - `alr runtime path/env native test ok`
    - compile then failed in `tests/native_alr_runtime_config_test.cpp` with `-Werror=missing-field-initializers` for partially initialized `RuntimeConfig` fields (`env`, `binds`, `hook_path`, `interposer_path`, `bridge_path`)
  - Classification: **BLOCKED/FAIL on this VPS** after Codex Bundle F; Codex reported PASS on its local host, so this is a platform/compiler strictness blocker to resolve in a later implementation PR.

- Command: `./gradlew --no-daemon :app:assembleDebug`
  - Exit code: `0`
  - Summary: Bundle F baseline built successfully on the VPS after SDK bootstrap.
  - APK: `app/build/outputs/apk/debug/app-debug.apk`
  - SHA-256: `29b300d422f34a0b135a8ddd440535d78a975d267b26d3fbc06f617b3b5b697e`
  - Size: `25567116 bytes`
  - Packaged ALR native libs: `libalr_runtime_launcher.so`, `libalr_runtime_hook.so`, and `libalr_runtime_interposer.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
  - Classification: **PASS** for APK build on this VPS

- Command: `./gradlew :app:assembleDebug` before SDK bootstrap
  - Exit code: `1`
  - Stderr/stdout summary:
    - `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file at '/home/ubuntu/work/Plib/local.properties'.`
  - Classification: **HISTORICAL_BLOCKER_RESOLVED** by `./scripts/bootstrap-android-build-host.sh`

- Command: `probe_optional_runtime_backend()` from `tools.runtime_launch_plan`
  - Exit code: `0`
  - Output summary:
    - `framework_status=PASS`
    - `available_status=SKIP`
    - `source=none`
    - `backend=none`
    - `candidate_path=`
    - `can_execute=False`
    - `reason=no optional external runtime backend configured`
  - Classification: **PASS** for safe absent-optional-backend handling; **SKIP** for optional backend execution

## Issue #1: Optional low-overhead backend A/B probe

Current baseline from this host:
- `PROOT BACKEND EXECUTION`: **SKIP**
  - Reason: requires APK install plus Android device execution. This host can now build the debug APK but has no attached Android device.
  - APK build evidence: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `29b300d422f34a0b135a8ddd440535d78a975d267b26d3fbc06f617b3b5b697e`, size `25567116 bytes`.
  - The app source contains detailed PRoot report collection for static hello, shell, glibc hello, identity/NSS, dpkg/apt version probes, local deb install, and installed package smoke, but no fresh device stdout/stderr/exit code was captured in this pass.

- `OPTIONAL LOW-OVERHEAD BACKEND AVAILABLE`: **SKIP**
  - Reason: no optional external low-overhead/proroot-class backend is configured in repo or supplied to this host.
  - Source: `probe_optional_runtime_backend()` returned `available_status=SKIP`, `source=none`, `backend=none`.

- `OPTIONAL LOW-OVERHEAD VERSION EXECUTION`: **SKIP**
  - Reason: no optional backend binary was provided. No binary was run or inspected.

- `OPTIONAL LOW-OVERHEAD ROOTFS EXECUTION`: **SKIP**
  - Reason: no optional backend binary was provided and no Android device is connected.

- `OPTIONAL LOW-OVERHEAD DPKG LOCAL INSTALL`: **SKIP**
  - Reason: no optional backend binary was provided and no Android device is connected.

Required device A/B command list for the next Android run:
- PRoot baseline:
  - `/bin/hello`
  - `/bin/sh -c "echo shell-c ok; /bin/hello"`
  - `/bin/glibc-hello`
  - `/usr/bin/id` with fake-root mode where supported
  - `/usr/bin/dpkg --version`
  - `/usr/bin/dpkg --print-architecture`
  - `/usr/bin/apt --version`
  - local deb install smoke if available
- Optional backend, only if externally supplied and metadata is recorded:
  - same rootfs and same command list as above
  - record source URL, version/tag, SHA-256, and license note before any run

Device evidence still required for #1 acceptance:
- Device model
- Android version and SDK
- APK version/build stamp
- Exact command inputs
- Stdout/stderr
- Exit code
- Elapsed time
- Logcat path or screenshot path where available

## Issue #3: GUI/GPU Surface bridge baseline

Current baseline from this host:
- `HOST GPU EGL/GLES EXECUTION`: **SKIP**
  - Reason: app-native EGL/GLES probe requires Android APK execution on a device/emulator. Android SDK is available on this host, but no Android device is connected.

- `HOST GPU SURFACE EXECUTION`: **SKIP**
  - Reason: requires Android `SurfaceView` lifecycle and `SurfaceHolder.Callback` on a device/emulator.

- `GUEST GPU IPC BRIDGE EXECUTION`: **SKIP**
  - Reason: requires rootfs execution through packaged PRoot in the APK plus loopback IPC inside the app process/device environment.

- `GUEST WAYLAND GUI GPU BRIDGE EXECUTION`: **SKIP**
  - Reason: requires device APK run and Surface/GPU report capture.

- `GUEST X11 GUI GPU BRIDGE EXECUTION`: **SKIP**
  - Reason: requires device APK run and Surface/GPU report capture.

Report sections already present in source and should be captured on the next device run:
- `ALR RUNTIME LAUNCHER AVAILABLE: ...`
- `ALR RUNTIME CONFIG BUILD: ...`
- `ALR RUNTIME DIRECT APP-DATA EXEC POLICY: ...`
- `ALR HOOK LOAD: ...`
- `ALR HOOK CONFIG BUILD: ...`
- `ALR HOOK MODE: ...`
- `ALR HOOK GUEST PATH: ...`
- `ALR HOOK HOST PATH: ...`
- `ALR HOOK STAT ROOTFS FILE: ...` or `ALR STAT ROOTFS FILE: ...` depending on report source
- `ALR HOOK OPEN ROOTFS FILE: ...` or `ALR OPEN ROOTFS FILE: ...` depending on report source
- `ALR INTERPOSER LOAD: ...`
- `ALR INTERPOSER CONFIG BUILD: ...`
- `ALR INTERPOSER MODE: translated-open-stat-smoke`
- `ALR INTERPOSER STAT PATH: ...`
- `ALR INTERPOSER OPEN PATH: ...`
- `ALR CONFIG SERIALIZE: ...`
- `ALR CONFIG PARSE: ...`
- `ALR_CONFIG_FORMAT=alr-config-v1`
- `ALR_INTERPOSER_PATH=...`
- `ALR_TRACE_PATH=...`
- `ALR_TRACE_EXEC=...`
- `HOST GPU EGL/GLES EXECUTION: ...`
- `HOST GPU SURFACE EXECUTION: PENDING_SURFACE_CALLBACK` initially, followed by Surface callback report if the `SurfaceView` lifecycle fires
- `GUEST GPU IPC BRIDGE EXECUTION: ...`
- `GUEST GPU MULTI-FRAME SURFACE EXECUTION: PENDING_SURFACE_CALLBACK`
- `GUEST WAYLAND GUI GPU BRIDGE EXECUTION: ...`
- `GUEST X11 GUI GPU BRIDGE EXECUTION: ...`
- `GUEST GUI GPU SURFACE EXECUTION: PENDING_SURFACE_CALLBACK`
- Renderer fields:
  - `host gpu renderer=...`
  - `host gpu vendor=...`
  - `host gpu software renderer=...`
  - `host gpu hardware candidate=...`
- Frame fields:
  - `guest gpu ipc expected frames=...`
  - `guest gpu ipc received frames=...`
  - `guest gpu ipc dropped frames=...`
  - `guest gpu ipc lossless=...`
  - `surface frames rendered=...` from the Surface callback report
  - `surface frames dropped=...` from the Surface callback report

## Device-run checklist for Codex/local Android host

Run from a host with Android SDK and a connected device:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
# Launch the app, wait for the report and Surface callback section, then capture:
adb logcat -d > docs/evidence/<date>-device-logcat.txt
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
```

Manual capture:
- Screenshot the top execution summary and Surface callback section.
- Copy/select app report text if possible.
- Record elapsed time for each backend command if the local runner/report exposes it.

## Classification summary

- Host tests: **PASS**
- Native host tests: **BLOCKED/FAIL on this VPS** (`scripts/test-native-core.sh` fails in Bundle F config test under `-Werror=missing-field-initializers`)
- APK build on this VPS: **PASS** (`./gradlew --no-daemon :app:assembleDebug`, APK SHA-256 `29b300d422f34a0b135a8ddd440535d78a975d267b26d3fbc06f617b3b5b697e`)
- Device install/run on this VPS: **SKIP** (`adb devices` lists no attached device)
- PRoot backend execution evidence from this pass: **SKIP** (device required)
- Optional low-overhead backend availability: **SKIP** (no optional backend configured/provided)
- Optional low-overhead execution evidence: **SKIP** (no optional backend configured/provided)
- GUI/GPU Surface bridge evidence from this pass: **SKIP** (device required)

## Blockers

- Android SDK is now installed/configured locally enough to build the debug APK, but no Android device is connected for install/run evidence.
- `adb devices` lists no attached device, so no Android runtime/GPU evidence can be collected here.
- No optional low-overhead/proroot-class backend artifact or metadata was provided.
- `scripts/test-native-core.sh` is blocked on this VPS after Bundle F by strict missing-field-initializer warnings treated as errors in `tests/native_alr_runtime_config_test.cpp`; Hermes did not edit Codex-owned implementation/test files in this evidence PR.

## Next recommended action

- Codex/local Android host should run the existing APK report on a connected Android device and append a follow-up evidence file with real stdout/stderr/exit code/elapsed time and screenshots/logcat.
- If an optional low-overhead/proroot-class backend is supplied later, add only metadata and black-box run results: source URL, version/tag, SHA-256, license note, command input, stdout, stderr, exit code, elapsed time, and logs.
