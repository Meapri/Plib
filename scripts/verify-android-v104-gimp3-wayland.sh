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
if [[ "${ALR_VERIFY_CLEAR_APP_DATA:-0}" == "1" ]]; then
  "$ADB" shell pm clear "$PKG" >/dev/null 2>&1 || true
fi
"$ADB" shell am start -n "$PKG/.MainActivity" --es ALR_VERIFY_MODE gimp3-wayland >/dev/null
deadline=$((SECONDS + ${ALR_VERIFY_TIMEOUT_SECONDS:-600}))
while (( SECONDS < deadline )); do
  LOG_SNAPSHOT="$("$ADB" logcat -d)"
  if grep -Fq "ALR_DEVICE_EVIDENCE" <<<"$LOG_SNAPSHOT"; then
    break
  fi
  sleep "${ALR_VERIFY_POLL_SECONDS:-10}"
done

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

require_log "build: 0.4.104-gimp3-wayland"
require_log "GIMP DEMO PROFILE EXECUTION: PASS"
require_log "GIMP CLI HELP PROBE EXECUTION: PASS"
require_log "GIMP CONSOLE VERSION PROBE EXECUTION: PASS"
require_log "GIMP CORE QUIT PROBE EXECUTION:"
require_log "GIMP CORE QUIT BLOCKER: CORE_QUIT_TIMEOUT"
require_log "GIMP CONSOLE BATCH QUIT PROBE EXECUTION:"
require_log "GIMP CONSOLE BATCH QUIT BLOCKER: CORE_BATCH_TIMEOUT"
require_log "full gimp probe mode=fast-scout"
require_log "GIMP GTK WAYLAND PROBE EXECUTION: PASS"
require_log "GIMP GTK WINDOW WAYLAND PROBE EXECUTION: PASS"
require_log "GIMP GUI QUIT WAYLAND PROBE EXECUTION: PASS"
require_log "GIMP GUI WAYLAND PROBE EXECUTION:"
require_log "GIMP GUI WAYLAND BLOCKER: PRE_WAYLAND_CONNECT"
require_log "GIMP DEMO BUNDLE LOCK: PASS"
require_log "ALR_GIMP_DEMO_PROFILE_READY target=gimp"
require_log "ALR_GIMP_DEMO_PROFILE_PROGRAM path=/usr/bin/gimp"
require_log "ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=wayland WAYLAND_DISPLAY=alr-gimp-0 XDG_RUNTIME_DIR=/tmp"
require_log "ALR_GIMP_DEMO_BUNDLE_LOCK present=true suite=trixie package_count=313"
require_log "ALR_GIMP_DEMO_MATERIALIZED present=true package_count=313 gimp_version=3."
require_log "ALR_GIMP_DEMO_BINARY present=true path=/usr/bin/gimp"
require_log "ALR_GIMP_DEMO_LAUNCH_MODE version-probe"
require_log "ALR_GIMP_DEMO_VERSION_EXIT 0"
require_log "ALR_GIMP_DEMO_VERSION_STDOUT GNU Image Manipulation Program version 3."
require_log "ALR_GIMP_DEMO_EXEC_READY true mode=version-probe"
require_log "gimp cli help handoff=ALR STATIC ENTRY HANDOFF: PASS"
require_log "gimp cli help stdout=Usage:"
require_log "gimp console version handoff=ALR STATIC ENTRY HANDOFF: PASS"
require_log "gimp console version stdout=GNU Image Manipulation Program version 3."
require_log "gimp core quit handoff=ALR STATIC ENTRY HANDOFF: FAIL"
require_log "gimp core quit exit=0"
require_log "gimp core quit blocker=core-quit-timeout"
require_log "gimp core quit timed out=alr handoff timed out=true"
require_log "gimp core quit child signaled=alr handoff child signaled=true"
require_log "gimp core quit handoff exit=alr handoff exit code=-1"
require_log "gimp core quit handoff signal=alr handoff signal=9"
require_log "gimp core quit fault syscall=alr handoff fault syscall=439"
require_log "gimp core quit path rewrite syscalls=alr handoff path rewrite syscall count="
require_log "gimp console batch quit handoff=ALR STATIC ENTRY HANDOFF: FAIL"
require_log "gimp console batch quit interpreter=plug-in-script-fu-eval"
require_log "gimp console batch quit blocker=core-batch-timeout"
require_log "gimp console batch quit timed out=alr handoff timed out=true"
require_log "rootfs gimp demo materialized exists=true"
require_log "rootfs /usr/bin/gimp exists=true executable=true"
require_log "gimp gtk wayland connected=true"
require_log "gimp gtk wayland setup bytes="
require_log "gimp gtk wayland object=1"
require_log "gimp gtk wayland opcode=1"
require_log "gimp gtk wayland size=12"
require_log "gimp gtk wayland request=wl_display.get_registry"
require_log "gimp gtk wayland raw prefix=0100000001000c00"
require_log "gimp gtk wayland server requests="
require_log "gimp gtk wayland server response bytes="
require_log "gimp gtk wayland server globals=wl_compositor,wl_shm,xdg_wm_base,wl_seat,wl_output"
require_log "gimp gtk wayland server request trace=wl_display.get_registry,wl_display.sync,wl_registry.bind:wl_compositor,wl_registry.bind:wl_shm,wl_registry.bind:wl_output,wl_display.sync,wl_shm.create_pool,wl_shm_pool.resize"
require_log "gimp gtk wayland server bind trace=wl_compositor,wl_shm,wl_output"
require_log "gimp gtk wayland server last request=wl_shm_pool.resize"
require_log "gimp gtk wayland server fd count=1"
require_log "gimp gtk wayland server fd bytes="
require_log "gimp gtk wayland server fd verified=1"
require_log "gimp gtk wayland server shm create pools=1"
require_log "gimp gtk wayland server shm pool resizes=3"
require_log "gimp gtk wayland server shm pool buffers=0"
require_log "gimp gtk wayland server surface attaches=0"
require_log "gimp gtk wayland server surface commits=0"
require_log "gimp gtk wayland server keyboard keymaps=0"
require_log "gimp gtk wayland handoff=ALR STATIC ENTRY HANDOFF: PASS"
require_log "gimp gtk wayland stdout=ALR_GIMP3_GTK_WAYLAND_PROBE ok"
require_log "gimp gtk window wayland connected=true"
require_log "gimp gtk window wayland server requests=10"
require_log "gimp gtk window wayland server globals=wl_compositor,wl_shm,xdg_wm_base,wl_seat,wl_output"
require_log "gimp gtk window wayland server request trace=wl_display.get_registry,wl_display.sync,wl_registry.bind:wl_compositor,wl_registry.bind:wl_shm,wl_registry.bind:wl_output,wl_display.sync,wl_shm.create_pool,wl_shm_pool.resize"
require_log "gimp gtk window wayland server bind trace=wl_compositor,wl_shm,wl_output"
require_log "gimp gtk window wayland server last request=wl_shm_pool.resize"
require_log "gimp gtk window wayland server fd count=1"
require_log "gimp gtk window wayland server fd bytes="
require_log "gimp gtk window wayland server fd verified=1"
require_log "gimp gtk window wayland server shm create pools=1"
require_log "gimp gtk window wayland server shm pool resizes=3"
require_log "gimp gtk window wayland server shm pool buffers=0"
require_log "gimp gtk window wayland server surface attaches=0"
require_log "gimp gtk window wayland server surface commits=0"
require_log "gimp gtk window wayland server keyboard keymaps=0"
require_log "gimp gtk window wayland handoff=ALR STATIC ENTRY HANDOFF: FAIL"
require_log "gimp gui quit wayland connected=true"
require_log "gimp gui quit wayland object=1"
require_log "gimp gui quit wayland opcode=1"
require_log "gimp gui quit wayland size=12"
require_log "gimp gui quit wayland request=wl_display.get_registry"
require_log "gimp gui quit wayland raw prefix=0100000001000c00"
require_log "gimp gui quit wayland handoff=ALR STATIC ENTRY HANDOFF: FAIL"
require_log "gimp gui quit wayland server requests=10"
require_log "gimp gui quit wayland server request trace=wl_display.get_registry,wl_display.sync,wl_registry.bind:wl_compositor,wl_registry.bind:wl_shm,wl_registry.bind:wl_output,wl_display.sync,wl_shm.create_pool,wl_shm_pool.resize"
require_log "gimp gui quit wayland server bind trace=wl_compositor,wl_shm,wl_output"
require_log "gimp gui quit wayland server last request=wl_shm_pool.resize"
require_log "gimp gui quit wayland server globals="
require_log "gimp gui quit wayland server fd count=1"
require_log "gimp gui quit wayland server fd bytes="
require_log "gimp gui quit wayland server fd verified=1"
require_log "gimp gui quit wayland server shm create pools=1"
require_log "gimp gui quit wayland server shm pool resizes=3"
require_log "gimp gui quit wayland server shm pool buffers=0"
require_log "gimp gui quit wayland server surface attaches=0"
require_log "gimp gui quit wayland server surface commits=0"
require_log "gimp gui wayland connected="
require_log "gimp gui wayland blocker=pre-wayland-connect"
require_log "gimp gui wayland handoff=ALR STATIC ENTRY HANDOFF: FAIL"
require_log "gimp gui wayland server globals="
require_log "gimp gui wayland server fd count=0"
require_pkg "versionCode=104"
require_pkg "versionName=0.4.104-gimp3-wayland"

printf '%s\n' "Android v104 GIMP 3 Wayland verification passed."
