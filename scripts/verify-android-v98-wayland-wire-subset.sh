#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-/Users/naen/Library/Android/sdk/platform-tools/adb}"
PKG="dev.chanwoo.androlinux"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -x "$ADB" ]]; then
  ADB="adb"
fi

cd "$ROOT"
"$ADB" install -r "$APK" >/dev/null
"$ADB" logcat -c
"$ADB" shell am force-stop "$PKG" >/dev/null 2>&1 || true
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep "${ALR_VERIFY_SLEEP_SECONDS:-95}"

LOG="$("$ADB" logcat -d)"
PKG_DUMP="$("$ADB" shell dumpsys package "$PKG")"

require_log() {
  local needle="$1"
  if ! grep -Fq "$needle" <<<"$LOG"; then
    echo "missing log evidence: $needle" >&2
    exit 1
  fi
}

require_pkg() {
  local needle="$1"
  if ! grep -Fq "$needle" <<<"$PKG_DUMP"; then
    echo "missing package evidence: $needle" >&2
    exit 1
  fi
}

require_log "WAYLAND DISPLAY SOCKET AVAILABLE: PASS"
require_log "WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS"
require_log "wayland display wire messages=15"
require_log "wayland display wire subset ready=true"
require_log "wayland display wire surface lifecycle=true"
require_log "ANDROID HOST AHARDWAREBUFFER EXECUTION: PASS"
require_log "WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS"
require_log "WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS"
require_log "wayland ahardwarebuffer surface compositor=egl-image-texture-to-android-surface"
require_log "wayland ahardwarebuffer surface replay passes=2"
require_log "wayland ahardwarebuffer surface total frame submissions=6"
require_log "wayland ahardwarebuffer surface buffer pool mode=slot-reuse"
require_log "wayland ahardwarebuffer surface buffer pool slots=3"
require_log "wayland ahardwarebuffer surface buffer pool reuses=3"
require_log "wayland ahardwarebuffer surface imported textures=3"
require_log "wayland ahardwarebuffer surface sampled frames=6"
require_log "wayland ahardwarebuffer surface presented frames=6"
require_log "wayland ahardwarebuffer surface hardware render=true"
require_log "wayland ahardwarebuffer surface dirty rect bytes=345600"
require_log "wayland ahardwarebuffer surface partial upload ratio pct=25"
require_log "wayland ahardwarebuffer surface fence wait candidates=3"
require_log "wayland ahardwarebuffer surface fence pacing mode=reuse-slot-fence-handoff"
require_log "wayland ahardwarebuffer surface sync fence accounting=ok"
require_log "surface vulkan hardware render=true"
require_pkg "versionCode=98"
require_pkg "versionName=0.4.98-wayland-wire-subset"

printf '%s\n' "Android v98 Wayland wire subset verification passed."
