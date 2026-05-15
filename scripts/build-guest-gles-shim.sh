#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -n "${ZIG:-}" ]]; then
  ZIG_BIN="$ZIG"
elif command -v zig >/dev/null 2>&1; then
  ZIG_BIN="$(command -v zig)"
else
  ZIG_BIN="/Users/naen/.local/zig/zig-aarch64-macos-0.16.0/zig"
fi
SRC_DIR="$ROOT_DIR/rootfs/guest-src/gles-shim"
OUT_DIR="$ROOT_DIR/build/guest-gles-shim"

mkdir -p "$OUT_DIR"

if [[ ! -x "$ZIG_BIN" ]]; then
  echo "missing zig compiler: set ZIG=/path/to/zig or install zig 0.16.0" >&2
  exit 1
fi

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s -fPIC -shared \
  "$SRC_DIR/alr_gles_shim.c" \
  -Wl,-soname,libalr_gles_shim.so \
  -o "$OUT_DIR/libalr_gles_shim.so"

for abi_lib in libEGL.so libGLESv2.so; do
  "$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s -fPIC -shared \
    "$SRC_DIR/alr_gles_shim.c" \
    -Wl,-soname,"$abi_lib" \
    -o "$OUT_DIR/$abi_lib"
done

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_gles_api_smoke.c" \
  -L"$OUT_DIR" \
  -lalr_gles_shim \
  -Wl,-rpath,/usr/lib/androlinux \
  -o "$OUT_DIR/alr-gles-shim-smoke"

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_gles_abi_smoke.c" \
  -L"$OUT_DIR" \
  -Wl,--no-as-needed \
  -lEGL \
  -lGLESv2 \
  -Wl,-rpath,/usr/lib/androlinux \
  -o "$OUT_DIR/alr-gles-abi-smoke"

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_gles_demo_gears.c" \
  -L"$OUT_DIR" \
  -Wl,--no-as-needed \
  -lEGL \
  -lGLESv2 \
  -Wl,-rpath,/usr/lib/androlinux \
  -o "$OUT_DIR/alr-gles-demo-gears"

file \
  "$OUT_DIR/libalr_gles_shim.so" \
  "$OUT_DIR/libEGL.so" \
  "$OUT_DIR/libGLESv2.so" \
  "$OUT_DIR/alr-gles-shim-smoke" \
  "$OUT_DIR/alr-gles-abi-smoke" \
  "$OUT_DIR/alr-gles-demo-gears"
