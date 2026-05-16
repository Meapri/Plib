#!/bin/sh
set -eu

PROFILE="/usr/share/androlinux/gimp-demo-profile.json"
LOCK="/usr/share/androlinux/gimp-demo-bundle.lock.json"
MATERIALIZED="/usr/share/androlinux/gimp-demo-materialized.txt"
GIMP_BIN="/usr/bin/gimp"
WAYLAND_NAME="${WAYLAND_DISPLAY:-alr-gimp-0}"
DISPLAY_NAME="${DISPLAY:-:0}"
RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"

echo "ALR_GIMP_DEMO_PROFILE_READY target=gimp version=v104 profile=$PROFILE lock=$LOCK"
echo "ALR_GIMP_DEMO_PROFILE_PROGRAM path=$GIMP_BIN argv=gimp,--new-instance,--no-data,--no-fonts"
echo "ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=${GDK_BACKEND:-wayland} WAYLAND_DISPLAY=$WAYLAND_NAME XDG_RUNTIME_DIR=$RUNTIME_DIR NO_AT_BRIDGE=${NO_AT_BRIDGE:-1}"

if [ -r "$LOCK" ]; then
  LOCK_PACKAGE_COUNT="$(sed -n 's/.*\"package_count\": \\([0-9][0-9]*\\).*/\\1/p' "$LOCK" | head -n 1)"
  LOCK_DOWNLOAD_MIB="$(sed -n 's/.*\"download_size_mib\": \\([0-9.][0-9.]*\\).*/\\1/p' "$LOCK" | head -n 1)"
  echo "ALR_GIMP_DEMO_BUNDLE_LOCK present=true package_count=${LOCK_PACKAGE_COUNT:-unknown} download_size_mib=${LOCK_DOWNLOAD_MIB:-unknown}"
else
  echo "ALR_GIMP_DEMO_BUNDLE_LOCK present=false package_count=0 download_size_mib=0"
fi

if [ -r "$MATERIALIZED" ]; then
  MATERIALIZED_PACKAGE_COUNT="$(sed -n 's/^package_count=//p' "$MATERIALIZED" | head -n 1)"
  MATERIALIZED_GIMP_VERSION="$(sed -n 's/^gimp_version=//p' "$MATERIALIZED" | head -n 1)"
  echo "ALR_GIMP_DEMO_MATERIALIZED present=true package_count=${MATERIALIZED_PACKAGE_COUNT:-unknown} gimp_version=${MATERIALIZED_GIMP_VERSION:-unknown}"
else
  echo "ALR_GIMP_DEMO_MATERIALIZED present=false package_count=0"
fi

if [ -x "$GIMP_BIN" ]; then
  echo "ALR_GIMP_DEMO_BINARY present=true path=$GIMP_BIN"
  export GDK_BACKEND="${GDK_BACKEND:-wayland}"
  unset DISPLAY
  export WAYLAND_DISPLAY="$WAYLAND_NAME"
  export XDG_RUNTIME_DIR="$RUNTIME_DIR"
  export NO_AT_BRIDGE="${NO_AT_BRIDGE:-1}"
  if [ "${1:-}" = "--launch-gui" ]; then
    shift
    echo "ALR_GIMP_DEMO_GUI_LAUNCH_ATTEMPT true"
    exec "$GIMP_BIN" --new-instance --no-data --no-fonts "$@"
  fi
  echo "ALR_GIMP_DEMO_LAUNCH_MODE version-probe"
  set +e
  VERSION_OUTPUT="$("$GIMP_BIN" --version 2>&1)"
  VERSION_EXIT=$?
  set -e
  echo "ALR_GIMP_DEMO_VERSION_EXIT $VERSION_EXIT"
  printf '%s\n' "$VERSION_OUTPUT" | while IFS= read -r line; do
    echo "ALR_GIMP_DEMO_VERSION_STDOUT $line"
  done
  if [ "$VERSION_EXIT" -eq 0 ]; then
    echo "ALR_GIMP_DEMO_EXEC_READY true mode=version-probe"
    exit 0
  fi
  echo "ALR_GIMP_DEMO_EXEC_READY false reason=version-probe-failed"
  exit "$VERSION_EXIT"
fi

echo "ALR_GIMP_DEMO_BINARY present=false path=$GIMP_BIN"
echo "ALR_GIMP_DEMO_EXEC_READY false reason=gimp-bundle-not-installed"
echo "ALR_GIMP_DEMO_NEXT_STEP install_debian_arm64_bundle_from_lock"
exit 0
