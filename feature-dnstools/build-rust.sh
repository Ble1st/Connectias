#!/bin/bash
# Build script for Rust DNS tools library
# This script builds the Rust library for all Android ABIs

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUST_DIR="${SCRIPT_DIR}/src/main/rust"
NDK_VERSION="26.1.10909125"

echo "Building Rust DNS tools for Android..."

# Check if cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "Installing cargo-ndk..."
    cargo install cargo-ndk
fi

cd "${RUST_DIR}"

# Map Rust targets to Android ABIs
declare -A ABI_MAP
ABI_MAP["aarch64-linux-android"]="arm64-v8a"
ABI_MAP["armv7-linux-androideabi"]="armeabi-v7a"
ABI_MAP["x86_64-linux-android"]="x86_64"
ABI_MAP["i686-linux-android"]="x86"

for RUST_ABI in "${!ABI_MAP[@]}"; do
    ANDROID_ABI="${ABI_MAP[${RUST_ABI}]}"
    echo "Building for ${RUST_ABI} (Android ABI: ${ANDROID_ABI})..."
    cargo ndk \
        --target "${RUST_ABI}" \
        --platform 33 \
        -- build --release
    
    TARGET_DIR="target/${RUST_ABI}/release"
    OUTPUT_LIB_DIR="${SCRIPT_DIR}/src/main/jniLibs/${ANDROID_ABI}"
    mkdir -p "${OUTPUT_LIB_DIR}"
    
    if [ -f "${TARGET_DIR}/libconnectias_dns_tools.so" ]; then
        cp "${TARGET_DIR}/libconnectias_dns_tools.so" "${OUTPUT_LIB_DIR}/"
        echo "✓ Built and copied successfully for ${ANDROID_ABI}"
    else
        echo "✗ Build failed for ${RUST_ABI}"
        exit 1
    fi
done

echo "✓ All Rust builds completed successfully"

