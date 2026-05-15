#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 -m pytest tests/ -q

required=(
  "settings.gradle.kts"
  "build.gradle.kts"
  "app/build.gradle.kts"
  "app/src/main/AndroidManifest.xml"
  "app/src/main/java/dev/chanwoo/androlinux/MainActivity.kt"
  "app/src/main/cpp/CMakeLists.txt"
  "app/src/main/cpp/runtime_report.cpp"
  "docs/architecture.md"
  "docs/build-environment.md"
  "docs/poc-roadmap.md"
  "rootfs/manifests/debian-arm64-bookworm-slim.json"
)

for path in "${required[@]}"; do
  test -f "$path" || { echo "missing: $path" >&2; exit 1; }
done

echo "host validation ok"
