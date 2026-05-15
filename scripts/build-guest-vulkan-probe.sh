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
SRC_DIR="$ROOT_DIR/rootfs/guest-src/vulkan"
OUT_DIR="$ROOT_DIR/build/guest-vulkan-probe"

mkdir -p "$OUT_DIR"

if [[ ! -x "$ZIG_BIN" ]]; then
  echo "missing zig compiler: set ZIG=/path/to/zig or install zig 0.16.0" >&2
  exit 1
fi

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_vulkan_discovery_client.c" \
  -o "$OUT_DIR/alr-vulkan-discovery-client"

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s -fPIC -shared \
  "$SRC_DIR/alr_vulkan_proxy.c" \
  -Wl,-soname,libvulkan.so.1 \
  -o "$OUT_DIR/libvulkan.so.1"

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_vulkan_proxy_smoke.c" \
  -o "$OUT_DIR/alr-vulkan-proxy-smoke"

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_vulkan_icd_manifest_smoke.c" \
  -o "$OUT_DIR/alr-vulkan-icd-manifest-smoke"

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  "$SRC_DIR/alr_vulkan_loader_info.c" \
  -o "$OUT_DIR/alr-vulkan-loader-info"

file "$OUT_DIR/alr-vulkan-discovery-client"
file "$OUT_DIR/libvulkan.so.1"
file "$OUT_DIR/alr-vulkan-proxy-smoke"
file "$OUT_DIR/alr-vulkan-icd-manifest-smoke"
file "$OUT_DIR/alr-vulkan-loader-info"
