#!/bin/bash
# Build libdvdcss and libdvdread for Android using Meson.
# Requires: meson, ninja, Android NDK (ANDROID_NDK_HOME or ANDROID_NDK_ROOT)
# Usage: ./scripts/build_dvdlibs.sh [arm64-v8a|armeabi-v7a|x86|x86_64]
# Run this before: cargo build --target aarch64-linux-android

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build/dvdlibs"
OUTPUT_DIR="$PROJECT_ROOT/rust/dvdlibs"
TARGET="${1:-arm64-v8a}"

# Map target to meson cpu
case "$TARGET" in
  arm64-v8a) MESON_CPU="aarch64" ;;
  armeabi-v7a) MESON_CPU="arm" ;;
  x86) MESON_CPU="x86" ;;
  x86_64) MESON_CPU="x86_64" ;;
  *) echo "Unknown target: $TARGET"; exit 1 ;;
esac

# Find NDK
NDK_ROOT="${ANDROID_NDK_HOME:-$ANDROID_NDK_ROOT}"
if [ -z "$NDK_ROOT" ]; then
  if [ -d "$HOME/Android/Sdk/ndk" ]; then
    NDK_ROOT="$(ls -d "$HOME/Android/Sdk/ndk"/*/ 2>/dev/null | head -1)"
  fi
fi
if [ -z "$NDK_ROOT" ] || [ ! -d "$NDK_ROOT" ]; then
  echo "Error: Android NDK not found. Set ANDROID_NDK_HOME or ANDROID_NDK_ROOT."
  exit 1
fi

# Detect host for NDK prebuilt path (linux-x86_64, darwin-x86_64, darwin-arm64)
HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in
  x86_64|amd64) HOST_ARCH="x86_64" ;;
  aarch64|arm64) HOST_ARCH="aarch64" ;;
  *) HOST_ARCH="x86_64" ;;
esac
case "$HOST_OS" in
  darwin) HOST_TUPLE="${HOST_OS}-${HOST_ARCH}" ;;
  *) HOST_TUPLE="linux-x86_64" ;;
esac
NDK_PREBUILT="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TUPLE"
if [ ! -d "$NDK_PREBUILT" ]; then
  NDK_PREBUILT="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
fi
if [ ! -d "$NDK_PREBUILT" ]; then
  echo "Error: NDK prebuilt not found at $NDK_PREBUILT"
  exit 1
fi

mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"
cd "$BUILD_DIR"

CROSS_FILE="$BUILD_DIR/android_$TARGET.ini"
cat > "$CROSS_FILE" << EOF
[binaries]
c = '$NDK_PREBUILT/bin/${MESON_CPU}-linux-android21-clang'
cpp = '$NDK_PREBUILT/bin/${MESON_CPU}-linux-android21-clang++'
ar = '$NDK_PREBUILT/bin/llvm-ar'
strip = '$NDK_PREBUILT/bin/llvm-strip'

[host_machine]
system = 'android'
cpu_family = '${MESON_CPU}'
cpu = '${MESON_CPU}'
endian = 'little'
EOF

# Build libdvdcss
echo "Building libdvdcss for $TARGET..."
meson setup "dvdcss_$TARGET" "$PROJECT_ROOT/third_party/libdvdcss" \
  --cross-file "$CROSS_FILE" \
  --buildtype release \
  -Ddefault_library=static \
  --prefix "$OUTPUT_DIR" \
  --wipe
ninja -C "dvdcss_$TARGET" install
# Meson installs to prefix/lib/libdvdcss.a - copy to our naming
if [ -f "$OUTPUT_DIR/lib/libdvdcss.a" ]; then
  cp "$OUTPUT_DIR/lib/libdvdcss.a" "$OUTPUT_DIR/libdvdcss_$TARGET.a"
fi

# Build libdvdread (depends on libdvdcss - use pkg-config from dvdcss build)
export PKG_CONFIG_PATH="$BUILD_DIR/dvdcss_$TARGET/meson-uninstalled"
echo "Building libdvdread for $TARGET..."
meson setup "dvdread_$TARGET" "$PROJECT_ROOT/third_party/libdvdread" \
  --cross-file "$CROSS_FILE" \
  --buildtype release \
  -Ddefault_library=static \
  -Dlibdvdcss=enabled \
  --prefix "$OUTPUT_DIR" \
  --wipe
ninja -C "dvdread_$TARGET" install
if [ -f "$OUTPUT_DIR/lib/libdvdread.a" ]; then
  cp "$OUTPUT_DIR/lib/libdvdread.a" "$OUTPUT_DIR/libdvdread_$TARGET.a"
fi

echo "Built: $OUTPUT_DIR/libdvdcss_$TARGET.a, $OUTPUT_DIR/libdvdread_$TARGET.a"
