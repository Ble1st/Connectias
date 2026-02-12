#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2025 Connectias
#
# Verification script for Android 16KB page size alignment
# Checks if all native libraries in the APK are properly aligned to 16KB boundaries

set -e

echo "========================================="
echo "Android 16KB Page Size Alignment Check"
echo "========================================="
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Find the most recent APK
APK_PATH=$(find app/build/outputs/apk -name "*.apk" -type f -printf '%T@ %p\n' | sort -n | tail -1 | cut -f2- -d" ")

if [ -z "$APK_PATH" ]; then
    echo -e "${RED}✗ No APK found${NC}"
    echo "  Build the app first: ./gradlew assembleDebug"
    exit 1
fi

echo "Checking APK: $APK_PATH"
echo ""

# Check if zipalign is available
if ! command -v zipalign &> /dev/null; then
    echo -e "${RED}✗ zipalign not found${NC}"
    echo "  Install Android SDK build-tools"
    exit 1
fi

# Check if unzip is available
if ! command -v unzip &> /dev/null; then
    echo -e "${RED}✗ unzip not found${NC}"
    echo "  Install unzip: sudo apt-get install unzip"
    exit 1
fi

# Extract native libraries list from APK
echo "Extracting native libraries list..."
LIBS=$(unzip -l "$APK_PATH" | grep "lib/.*\.so$" | awk '{print $4}')

if [ -z "$LIBS" ]; then
    echo -e "${YELLOW}⚠ No native libraries found in APK${NC}"
    exit 0
fi

echo "Found $(echo "$LIBS" | wc -l) native libraries"
echo ""

# Check alignment for each library
FAILED_COUNT=0
PASSED_COUNT=0
PAGE_SIZE=16384  # 16KB

echo "Checking 16KB alignment (16384 bytes)..."
echo "----------------------------------------"

for LIB in $LIBS; do
    # Get library offset in APK using unzip
    OFFSET=$(unzip -l -v "$APK_PATH" "$LIB" | grep "^----" -A 1 | tail -1 | awk '{print $7}')

    if [ -z "$OFFSET" ]; then
        echo -e "${RED}✗ Could not get offset for $LIB${NC}"
        FAILED_COUNT=$((FAILED_COUNT + 1))
        continue
    fi

    # Check if offset is aligned to 16KB boundary
    REMAINDER=$((OFFSET % PAGE_SIZE))

    if [ $REMAINDER -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $(basename "$LIB") (offset: $OFFSET, aligned)"
        PASSED_COUNT=$((PASSED_COUNT + 1))
    else
        echo -e "${RED}✗${NC} $(basename "$LIB") (offset: $OFFSET, misaligned by $REMAINDER bytes)"
        FAILED_COUNT=$((FAILED_COUNT + 1))
    fi
done

echo ""
echo "========================================="
echo "Results:"
echo "========================================="
echo -e "${GREEN}Passed:${NC} $PASSED_COUNT"
echo -e "${RED}Failed:${NC} $FAILED_COUNT"
echo ""

if [ $FAILED_COUNT -gt 0 ]; then
    echo -e "${RED}✗ Some libraries are not 16KB aligned${NC}"
    echo ""
    echo "Possible fixes:"
    echo "1. Rebuild Rust libraries: ./build-rust-libs.sh"
    echo "2. Rebuild APK: ./gradlew clean assembleDebug"
    echo "3. Check third-party libraries for 16KB support"
    echo "   - libvlc.so: Update to version with 16KB support"
    echo "   - libopencv_java4.so: Rebuild with -Wl,-z,max-page-size=16384"
    echo "   - libsqlcipher.so: Update to version with 16KB support"
    echo ""
    exit 1
else
    echo -e "${GREEN}✓ All libraries are properly 16KB aligned${NC}"
    echo ""
    exit 0
fi
