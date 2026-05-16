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
require_log "SIMPLE GUI DEMO EXECUTION: PASS"
require_log "SIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION: PASS"
require_log "simple gui demo glibc dynamic=true"
require_log "simple gui demo display commits=8/8"
require_log "simple gui demo binary messages=30"
require_log "simple gui demo continuous stream ready=true"
require_log "simple gui demo android surface candidate=true"
require_log "ALR_SIMPLE_GUI_DEMO ok"
require_log "wayland display continuous stream ready=true"
require_log "wayland display wire messages=30"
require_log "wayland display wire subset ready=true"
require_log "wayland display wire surface lifecycle=true"
require_log "wayland display binary messages=30"
require_log "wayland display binary bytes=568"
require_log "wayland display binary subset ready=true"
require_log "ANDROID HOST AHARDWAREBUFFER EXECUTION: PASS"
require_log "WAYLAND DISPLAY AHARDWAREBUFFER BACKING EXECUTION: PASS"
require_log "WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS"
require_log "wayland ahardwarebuffer surface compositor=egl-image-texture-to-android-surface"
require_log "wayland ahardwarebuffer surface replay passes=1"
require_log "wayland ahardwarebuffer surface continuous guest commits=true"
require_log "wayland ahardwarebuffer surface simple gui demo candidate=true"
require_log "wayland ahardwarebuffer surface total frame submissions=8"
require_log "wayland ahardwarebuffer surface buffer pool mode=slot-reuse"
require_log "wayland ahardwarebuffer surface buffer pool slots=3"
require_log "wayland ahardwarebuffer surface buffer pool reuses=5"
require_log "wayland ahardwarebuffer surface imported textures=3"
require_log "wayland ahardwarebuffer surface sampled frames=8"
require_log "wayland ahardwarebuffer surface presented frames=8"
require_log "wayland ahardwarebuffer surface hardware render=true"
require_log "wayland ahardwarebuffer surface dirty rect bytes=460800"
require_log "wayland ahardwarebuffer surface partial upload ratio pct=25"
require_log "wayland ahardwarebuffer surface fence wait candidates=5"
require_log "wayland ahardwarebuffer surface fence pacing mode=reuse-slot-fence-handoff"
require_log "wayland ahardwarebuffer surface sync fence accounting=ok"
require_log "surface vulkan hardware render=true"
require_pkg "versionCode=101"
require_pkg "versionName=0.4.101-simple-gui-demo"

printf '%s\n' "Android v101 simple GUI demo verification passed."
