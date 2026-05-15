#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
GRADLE_VERSION="8.10.2"
NDK_VERSION="27.2.12479018"
CMAKE_VERSION="3.22.1"

arch="$(uname -m)"
if [[ "$arch" != "x86_64" ]]; then
  cat >&2 <<MSG
Unsupported build-host architecture: $arch

Android SDK build-tools are distributed for Linux x86_64 and are not a reliable native APK build target on this host.
Use an x86_64 Linux build host, Android Studio machine, or CI runner for :app:assembleDebug.

This repository can still run host-side policy tests here:
  ./scripts/validate-host.sh
MSG
  exit 64
fi

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 127
  }
}

need_cmd curl
need_cmd unzip
need_cmd java

mkdir -p "$SDK_DIR/cmdline-tools"
if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  tmp_zip="$(mktemp -t android-commandlinetools.XXXXXX.zip)"
  tmp_dir="$(mktemp -d -t android-commandlinetools.XXXXXX)"
  curl -L "$CMDLINE_TOOLS_ZIP_URL" -o "$tmp_zip"
  unzip -q "$tmp_zip" -d "$tmp_dir"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp_dir/cmdline-tools"/* "$SDK_DIR/cmdline-tools/latest/"
  rm -rf "$tmp_zip" "$tmp_dir"
fi

export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_HOME="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null
sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "ndk;$NDK_VERSION" \
  "cmake;$CMAKE_VERSION"

cat > "$ROOT/local.properties" <<EOF2
sdk.dir=$SDK_DIR
ndk.version=$NDK_VERSION
cmake.version=$CMAKE_VERSION
EOF2

if [[ ! -x "$ROOT/gradlew" ]]; then
  gradle_tmp="$(mktemp -d -t gradle-bootstrap.XXXXXX)"
  curl -L "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$gradle_tmp/gradle.zip"
  unzip -q "$gradle_tmp/gradle.zip" -d "$gradle_tmp"
  "$gradle_tmp/gradle-${GRADLE_VERSION}/bin/gradle" -p "$ROOT" wrapper --gradle-version "$GRADLE_VERSION"
  rm -rf "$gradle_tmp"
fi

"$ROOT/gradlew" -p "$ROOT" :app:assembleDebug
