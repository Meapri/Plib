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
"$ADB" shell pm clear "$PKG" >/dev/null 2>&1 || true
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep "${ALR_VERIFY_SLEEP_SECONDS:-150}"

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

require_log "GIMP DEMO PROFILE EXECUTION: PASS"
require_log "GIMP DEMO BUNDLE LOCK: PASS"
require_log "ALR_GIMP_DEMO_PROFILE_READY target=gimp"
require_log "ALR_GIMP_DEMO_PROFILE_PROGRAM path=/usr/bin/gimp"
require_log "ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=x11 DISPLAY=:0 WAYLAND_DISPLAY=alr-gimp-0"
require_log "ALR_GIMP_DEMO_BUNDLE_LOCK present=true package_count=246"
require_log "ALR_GIMP_DEMO_MATERIALIZED present=true package_count=246"
require_log "ALR_GIMP_DEMO_BINARY present=true path=/usr/bin/gimp"
require_log "ALR_GIMP_DEMO_LAUNCH_MODE version-probe"
require_log "ALR_GIMP_DEMO_VERSION_EXIT 0"
require_log "ALR_GIMP_DEMO_VERSION_STDOUT GNU Image Manipulation Program version"
require_log "ALR_GIMP_DEMO_EXEC_READY true mode=version-probe"
require_log "rootfs gimp demo materialized exists=true"
require_log "rootfs /usr/bin/gimp exists=true executable=true"
require_log "WAYLAND DISPLAY SOCKET AVAILABLE: PASS"
require_log "WAYLAND DISPLAY COMMIT SURFACE EXECUTION: PASS"
require_log "SIMPLE GUI DEMO EXECUTION: PASS"
require_log "SIMPLE GUI DEMO GLIBC DYNAMIC EXECUTION: PASS"
require_log "simple gui demo glibc dynamic=true"
require_log "simple gui demo display commits=8/8"
require_log "simple gui demo binary messages=30"
require_log "simple gui demo continuous stream ready=true"
require_log "simple gui demo android surface candidate=true"
require_log "wayland display continuous stream ready=true"
require_log "wayland display wire messages=30"
require_log "wayland display wire subset ready=true"
require_log "wayland display wire surface lifecycle=true"
require_log "wayland display binary messages=30"
require_log "wayland display binary bytes=568"
require_log "wayland display binary subset ready=true"
require_log "WAYLAND AHARDWAREBUFFER SURFACE COMPOSITOR EXECUTION: PASS"
require_log "wayland ahardwarebuffer surface compositor=egl-image-texture-to-android-surface"
require_log "wayland ahardwarebuffer surface replay passes=1"
require_log "wayland ahardwarebuffer surface continuous guest commits=true"
require_log "wayland ahardwarebuffer surface simple gui demo candidate=true"
require_log "wayland ahardwarebuffer surface total frame submissions=8"
require_log "wayland ahardwarebuffer surface buffer pool reuses=5"
require_log "wayland ahardwarebuffer surface presented frames=8"
require_log "wayland ahardwarebuffer surface hardware render=true"
require_log "wayland ahardwarebuffer surface dirty rect bytes=460800"
require_log "wayland ahardwarebuffer surface fence pacing mode=reuse-slot-fence-handoff"
require_log "wayland ahardwarebuffer surface sync fence accounting=ok"
require_log "surface vulkan hardware render=true"
require_pkg "versionCode=103"
require_pkg "versionName=0.4.103-gimp-materialized"

printf '%s\n' "Android v103 GIMP materialized verification passed."
