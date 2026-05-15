#!/usr/bin/env bash
set -euo pipefail

cat <<'MSG'
This project needs an Android build host with:

- JDK 17+
- Android SDK commandline-tools
- Android SDK platform 35
- Android SDK build-tools
- Android NDK
- CMake 3.22.1+

Recommended next command on a build host:

  ./gradlew :app:assembleDebug

This repository intentionally keeps the first host validation independent of Android SDK so architectural tests can run on any Linux server.
MSG
