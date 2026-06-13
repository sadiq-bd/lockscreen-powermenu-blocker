#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$ROOT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
ZIP_NAME="lockscreen-powermenu-blocker.zip"
ZIP_PATH="$BUILD_DIR/$ZIP_NAME"
JAR_PATH="$ROOT_DIR/system/usr/share/lockscreen-powermenu-blocker/BlockerService.jar"

die() {
    echo "build.sh: $*" >&2
    exit 1
}

find_android_home() {
    if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
        echo "$ANDROID_HOME"
        return
    fi

    if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then
        echo "$ANDROID_SDK_ROOT"
        return
    fi

    if [[ -d "$HOME/android-sdk" ]]; then
        echo "$HOME/android-sdk"
        return
    fi

    die "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
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
    rm -f "$JAR_PATH"
}

if [[ "${1:-}" == "clean" ]]; then
    clean
    echo "Cleaned build outputs."
    exit 0
fi

require_cmd javac
require_cmd zip

ANDROID_HOME="$(find_android_home)"
PLATFORM_DIR="$(latest_dir "$ANDROID_HOME/platforms")"
BUILD_TOOLS_DIR="$(latest_dir "$ANDROID_HOME/build-tools")"
ANDROID_JAR="$PLATFORM_DIR/android.jar"
D8="$BUILD_TOOLS_DIR/d8"

[[ -f "$ANDROID_JAR" ]] || die "missing android.jar: $ANDROID_JAR"
[[ -x "$D8" ]] || die "missing executable d8: $D8"

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR" "$(dirname "$JAR_PATH")" "$BUILD_DIR"

echo "Compiling BlockerService.java..."
javac \
    -cp "$ANDROID_JAR" \
    -d "$CLASSES_DIR" \
    "$ROOT_DIR/BlockerService.java"

echo "Dexing BlockerService.jar..."
"$D8" "$CLASSES_DIR"/*.class \
    --lib "$ANDROID_JAR" \
    --output "$JAR_PATH"

[[ -s "$JAR_PATH" ]] || die "failed to create $JAR_PATH"

echo "Packaging Magisk module..."
rm -f "$ZIP_PATH"
(
    cd "$ROOT_DIR"
    zip -qr "$ZIP_PATH" . \
        -x ".git/*" \
        -x ".github/*" \
        -x ".gitignore" \
        -x "build/*" \
        -x "*.java" \
        -x "*.class" \
        -x "system/usr/share/lockscreen-powermenu-blocker/placeholder"
)

[[ -s "$ZIP_PATH" ]] || die "failed to create $ZIP_PATH"

echo "Built:"
echo "  $JAR_PATH"
echo "  $ZIP_PATH"
