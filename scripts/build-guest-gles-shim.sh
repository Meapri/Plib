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

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_gles_api_smoke.c" \
  -L"$OUT_DIR" \
  -lalr_gles_shim \
  -Wl,-rpath,/usr/lib/androlinux \
  -o "$OUT_DIR/alr-gles-shim-smoke"

file "$OUT_DIR/libalr_gles_shim.so" "$OUT_DIR/alr-gles-shim-smoke"
