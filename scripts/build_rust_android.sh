#!/bin/bash
# Build Rust crate for Android. Sets NDK in PATH for linker.
# Usage: ./scripts/build_rust_android.sh [debug|release]

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MODE="${1:-debug}"

NDK_ROOT="${ANDROID_NDK_HOME:-$ANDROID_NDK_ROOT}"
if [ -z "$NDK_ROOT" ]; then
  if [ -d "$HOME/Android/Sdk/ndk" ]; then
    NDK_ROOT="$(ls -d "$HOME/Android/Sdk/ndk"/*/ 2>/dev/null | head -1)"
  fi
fi
if [ -z "$NDK_ROOT" ] || [ ! -d "$NDK_ROOT" ]; then
  echo "Error: Android NDK not found. Set ANDROID_NDK_HOME."
  exit 1
fi

HOST_OS="$(uname -s)"
case "$HOST_OS" in
  Darwin) HOST_TUPLE="darwin-x86_64" ;;
  *) HOST_TUPLE="linux-x86_64" ;;
esac
NDK_BIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TUPLE/bin"
if [ ! -d "$NDK_BIN" ]; then
  NDK_BIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
fi
if [ ! -d "$NDK_BIN" ]; then
  echo "Error: NDK bin not found at $NDK_BIN"
  exit 1
fi

export PATH="$NDK_BIN:$PATH"
export ANDROID_NDK_HOME="$NDK_ROOT"

cd "$PROJECT_ROOT/rust"
if [ "$MODE" = "release" ]; then
  cargo build --target aarch64-linux-android --release
  SO_PATH="target/aarch64-linux-android/release/libconnectias_rust.so"
else
  cargo build --target aarch64-linux-android
  SO_PATH="target/aarch64-linux-android/debug/libconnectias_rust.so"
fi
if [ -f "$SO_PATH" ]; then
  cp "$SO_PATH" "$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a/"
  echo "Built and copied libconnectias_rust.so to jniLibs/arm64-v8a/"
else
  echo "Warning: $SO_PATH not found (check CARGO_TARGET_DIR)"
  exit 1
fi
