#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2025 Connectias
#
# Lokaler Build-Script für Rust-Libraries
# Basierend auf .github/workflows/release.yml

set -e  # Exit on error

echo "========================================="
echo "Building Rust Libraries for Connectias"
echo "========================================="
echo ""

# Navigate to Rust directory
cd "$(dirname "$0")/core/src/main/rust"

# ABI Mapping (Rust target -> Android ABI)
declare -A ABI_MAP=(
  ["aarch64-linux-android"]="arm64-v8a"
  ["armv7-linux-androideabi"]="armeabi-v7a"
  ["x86_64-linux-android"]="x86_64"
  ["i686-linux-android"]="x86"
)

# Build for all Android ABIs
RUST_ABIS=("aarch64-linux-android" "armv7-linux-androideabi" "x86_64-linux-android" "i686-linux-android")

# Base directories
JNI_LIBS_BASE="../jniLibs"

echo "Workspace: $(pwd)"
echo "jniLibs directory: ${JNI_LIBS_BASE}"
echo ""

# Create jniLibs directory structure
mkdir -p "${JNI_LIBS_BASE}"

for RUST_ABI in "${RUST_ABIS[@]}"; do
  echo "----------------------------------------"
  echo "Building for ${RUST_ABI}..."
  echo "----------------------------------------"
  
  # Build with cargo-ndk
  cargo ndk \
    --target "${RUST_ABI}" \
    --platform 33 \
    -- build --release
  
  # Verify build succeeded
  TARGET_DIR="target/${RUST_ABI}/release"
  LIB_FILE="${TARGET_DIR}/libconnectias_root_detector.so"
  
  if [ ! -f "${LIB_FILE}" ]; then
    echo "✗ Build failed for ${RUST_ABI} - library not found at ${LIB_FILE}"
    exit 1
  fi
  
  echo "✓ Built successfully for ${RUST_ABI}"
  
  # Get Android ABI name
  ANDROID_ABI="${ABI_MAP[${RUST_ABI}]}"
  
  # Copy to jniLibs directory (for direct APK inclusion)
  JNI_LIBS_DIR="${JNI_LIBS_BASE}/${ANDROID_ABI}"
  mkdir -p "${JNI_LIBS_DIR}"
  cp "${LIB_FILE}" "${JNI_LIBS_DIR}/libconnectias_root_detector.so"
  
  # Verify copy succeeded
  if [ -f "${JNI_LIBS_DIR}/libconnectias_root_detector.so" ]; then
    echo "✓ Copied to ${JNI_LIBS_DIR}/libconnectias_root_detector.so"
    ls -lh "${JNI_LIBS_DIR}/libconnectias_root_detector.so"
  else
    echo "✗ Failed to copy library to ${JNI_LIBS_DIR}/"
    exit 1
  fi
  
  # Also create copy for CMake (CMake expects Android ABI names in target/)
  CMAKE_TARGET_DIR="target/${ANDROID_ABI}/release"
  mkdir -p "${CMAKE_TARGET_DIR}"
  cp "${LIB_FILE}" "${CMAKE_TARGET_DIR}/libconnectias_root_detector.so"
  echo "✓ Copied to ${CMAKE_TARGET_DIR}/libconnectias_root_detector.so (for CMake)"
  echo ""
done

echo "========================================="
echo "✓ All Rust builds completed successfully"
echo "========================================="
echo ""

echo "Verifying jniLibs directory structure:"
find "${JNI_LIBS_BASE}" -name "*.so" -type f | sort
echo ""

echo "Library sizes:"
find "${JNI_LIBS_BASE}" -name "*.so" -type f -exec ls -lh {} \;
echo ""

echo "========================================="
echo "Build Summary:"
echo "========================================="
echo "✓ Libraries built for: ${RUST_ABIS[*]}"
echo "✓ Output directory: ${JNI_LIBS_BASE}"
echo "✓ CMake directories: target/*/release/"
echo ""
echo "Ready for Gradle build!"
