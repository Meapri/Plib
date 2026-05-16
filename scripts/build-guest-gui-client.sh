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
SRC_DIR="$ROOT_DIR/rootfs/guest-src/gui"
OUT_DIR="$ROOT_DIR/build/guest-gui-client"

mkdir -p "$OUT_DIR"

if [[ ! -x "$ZIG_BIN" ]]; then
  echo "missing zig compiler: set ZIG=/path/to/zig or install zig 0.16.0" >&2
  exit 1
fi

for client in alr-wayland-gpu-client alr-x11-gpu-client; do
  "$ZIG_BIN" cc -target aarch64-linux-musl -O2 -s \
    "$SRC_DIR/alr_gui_gpu_client.c" \
    -static \
    -o "$OUT_DIR/$client"
done

"$ZIG_BIN" cc -target aarch64-linux-musl -O2 -s \
  "$SRC_DIR/alr_wayland_display_client.c" \
  -static \
  -o "$OUT_DIR/alr-wayland-display-client"

"$ZIG_BIN" cc -target aarch64-linux-gnu -O2 -s \
  -DALR_SIMPLE_GUI_DEMO=1 \
  "$SRC_DIR/alr_wayland_display_client.c" \
  -o "$OUT_DIR/alr-simple-gui-demo"

file \
  "$OUT_DIR/alr-wayland-gpu-client" \
  "$OUT_DIR/alr-x11-gpu-client" \
  "$OUT_DIR/alr-wayland-display-client" \
  "$OUT_DIR/alr-simple-gui-demo"
