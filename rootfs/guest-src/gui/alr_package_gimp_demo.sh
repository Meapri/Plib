#!/bin/sh
set -eu

PROFILE="/usr/share/androlinux/gimp-demo-profile.json"
LOCK="/usr/share/androlinux/gimp-demo-bundle.lock.json"
GIMP_BIN="/usr/bin/gimp"
WAYLAND_NAME="${WAYLAND_DISPLAY:-alr-gimp-0}"
RUNTIME_DIR="${XDG_RUNTIME_DIR:-/usr/share/alr-smoke/alr-wayland-runtime}"

echo "ALR_GIMP_DEMO_PROFILE_READY target=gimp version=v102 profile=$PROFILE lock=$LOCK"
echo "ALR_GIMP_DEMO_PROFILE_PROGRAM path=$GIMP_BIN argv=gimp,--new-instance,--no-data,--no-fonts"
echo "ALR_GIMP_DEMO_PROFILE_ENV GDK_BACKEND=${GDK_BACKEND:-wayland} WAYLAND_DISPLAY=$WAYLAND_NAME XDG_RUNTIME_DIR=$RUNTIME_DIR NO_AT_BRIDGE=${NO_AT_BRIDGE:-1}"

if [ -r "$LOCK" ]; then
  echo "ALR_GIMP_DEMO_BUNDLE_LOCK present=true package_count=246 download_size_mib=122.27"
else
  echo "ALR_GIMP_DEMO_BUNDLE_LOCK present=false package_count=0 download_size_mib=0"
fi

if [ -x "$GIMP_BIN" ]; then
  echo "ALR_GIMP_DEMO_BINARY present=true path=$GIMP_BIN"
  echo "ALR_GIMP_DEMO_EXEC_READY true"
  export GDK_BACKEND="${GDK_BACKEND:-wayland}"
  export WAYLAND_DISPLAY="$WAYLAND_NAME"
  export XDG_RUNTIME_DIR="$RUNTIME_DIR"
  export NO_AT_BRIDGE="${NO_AT_BRIDGE:-1}"
  exec "$GIMP_BIN" --new-instance --no-data --no-fonts "$@"
fi

echo "ALR_GIMP_DEMO_BINARY present=false path=$GIMP_BIN"
echo "ALR_GIMP_DEMO_EXEC_READY false reason=gimp-bundle-not-installed"
echo "ALR_GIMP_DEMO_NEXT_STEP install_debian_arm64_bundle_from_lock"
exit 0
