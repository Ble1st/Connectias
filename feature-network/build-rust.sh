#!/bin/bash
# Build script for Rust port scanner library
# This script builds the Rust library for all Android ABIs

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUST_DIR="${SCRIPT_DIR}/src/main/rust"
NDK_VERSION="26.1.10909125"

echo "Building Rust port scanner for Android..."

# Check if cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "Installing cargo-ndk..."
    cargo install cargo-ndk
fi

cd "${RUST_DIR}"

# Build for all Android ABIs
ABIS=("aarch64-linux-android" "armv7-linux-androideabi" "x86_64-linux-android" "i686-linux-android")

for ABI in "${ABIS[@]}"; do
    echo "Building for ${ABI}..."
    cargo ndk \
        --target "${ABI}" \
        --platform 33 \
        -- build --release
    
    # Copy to expected location for CMake
    TARGET_DIR="target/${ABI}/release"
    OUTPUT_DIR="target/${ABI}/release"
    
    if [ -f "${TARGET_DIR}/libconnectias_port_scanner.so" ]; then
        echo "✓ Built successfully for ${ABI}"
    else
        echo "✗ Build failed for ${ABI}"
        exit 1
    fi
done

echo "✓ All Rust builds completed successfully"

