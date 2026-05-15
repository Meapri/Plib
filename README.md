# AndroLinux Runtime Lab

Goal: build a non-root Android APK runtime that can launch a glibc Linux arm64 rootfs and bridge Linux graphics/media/hardware calls to Android public APIs.

Working product definition:

> GPU-vendor-independent, Android-API-backed, hardware-accelerated Linux-on-Android runtime.

## Current strategy

1. Prove an APK-owned runtime can launch a bundled native loader from the package native library directory.
2. Keep writable rootfs data in app-private storage, but do not directly execute app-data binaries on modern Android.
3. Use a launcher/loader/proot-style runtime as the initial rootfs execution substrate.
4. Add GUI output through an Android-owned Surface.
5. Start graphics acceleration with OpenGL/GLES through virglrenderer/ANGLE/EGL-style host rendering.
6. Research Vulkan through Venus or a custom proxy ICD after OpenGL MVP.
7. Research a proroot-style low-overhead runtime separately from the graphics bridge.

## Repository layout

```text
app/                         Android APK skeleton
app/src/main/cpp/            Native loader/runtime entrypoint
app/src/main/java/           Kotlin UI entrypoint
docs/                        Architecture, risks, and PoC roadmap
scripts/                     Bootstrap and validation scripts
tests/                       Host-side policy/model tests
tools/                       Host-side planning helpers
```

## First PoC target

PoC 1: APK launches a native loader from `nativeLibraryDir`, points it at `files/rootfs/debian-arm64`, and emits a deterministic launch plan for `/bin/bash` without directly execing app-data binaries.

## Host tests

```bash
python3 -m pytest tests/ -q
```
