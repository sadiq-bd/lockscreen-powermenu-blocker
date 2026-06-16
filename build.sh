#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$ROOT_DIR/build"
ZIP_NAME="lockscreen-powermenu-blocker.zip"
ZIP_PATH="$BUILD_DIR/$ZIP_NAME"
MODULE_DIR="$ROOT_DIR/src/module"

mkdir -p "$BUILD_DIR"

die() {
    echo "build.sh: $*" >&2
    exit 1
}

find_android_home() {
    if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
        echo "$ANDROID_HOME"
        return
    fi

    if [[ -n "${ANDROID_NDK_ROOT:-}" && -d "$ANDROID_NDK_ROOT" ]]; then
        echo "$ANDROID_NDK_ROOT"
        return
    fi

    if [[ -d "$HOME/android-ndk" ]]; then
        echo "$HOME/android-ndk"
        return
    fi

    die "Android NDK not found. Set ANDROID_HOME or ANDROID_NDK_ROOT."
}

latest_dir() {
    local parent="$1"
    [[ -d "$parent" ]] || die "missing directory: $parent"

    find "$parent" -mindepth 1 -maxdepth 1 -type d \
        | sort -V \
        | tail -n 1
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

clean() {
    rm -rf "$BUILD_DIR"
}

if [[ "${1:-}" == "clean" ]]; then
    clean
    echo "Cleaned build outputs."
    exit 0
fi

require_cmd zip

ANDROID_HOME="$(find_android_home)"
ANDROID_CLANG="$ANDROID_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang"
BIN_PATH="src/module/system/bin"
APP_DAEMON="powerblockerd"
DAEMON_PATH="$BIN_PATH/$APP_DAEMON"

mkdir -p "$BIN_PATH"

echo "Compiling $APP_DAEMON.c..."
rm -f "$DAEMON_PATH"
"$ANDROID_CLANG" \
    "src/$APP_DAEMON.c" \
    -o "$DAEMON_PATH"

[[ -s "$DAEMON_PATH" ]] || die "failed to create $DAEMON_PATH"

echo "Packaging Magisk module..."
rm -f "$ZIP_PATH"
(
    cd "$MODULE_DIR"
    zip -qr "$ZIP_PATH" .
)

[[ -s "$ZIP_PATH" ]] || die "failed to create $ZIP_PATH"

echo "Built:"
echo "  $ZIP_PATH "
