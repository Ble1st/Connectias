# Third-Party Library 16KB Alignment Guide

This document tracks the 16KB alignment status of third-party native libraries used in Connectias.

## Current Third-Party Libraries

Based on the APK analysis, the following third-party libraries are included:

### VLC Media Player

**Files:**
- `libvlc.so`
- `libvlcjni.so`

**Current Status:** ⚠️ Unknown (requires verification)

**Update Instructions:**
```gradle
// build.gradle.kts (check for latest version with 16KB support)
dependencies {
    implementation("org.videolan.android:libvlc-all:4.0.0-eap15")
}
```

**Verification:**
```bash
# Check VLC SDK changelog for 16KB support announcement
# Test on 16KB device after updating
```

**Alternative:** Build VLC from source with custom linker flags:
```bash
# Clone VLC Android repository
git clone https://code.videolan.org/videolan/vlc-android.git
cd vlc-android

# Add to compile.sh or CMakeLists.txt:
LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
```

---

### OpenCV

**Files:**
- `libopencv_java4.so`

**Current Status:** ⚠️ Unknown (requires verification)

**Update Instructions:**
```gradle
// build.gradle.kts
dependencies {
    // Update to OpenCV 4.10.0+ which should have 16KB support
    implementation("org.opencv:opencv-android:4.10.0")
}
```

**Alternative:** Build OpenCV from source:
```bash
# Download OpenCV Android SDK
wget https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-android-sdk.zip

# Rebuild with CMake:
cmake -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" \
      ..
make -j8
```

---

### SQLCipher

**Files:**
- `libsqlcipher.so`

**Current Status:** ⚠️ Unknown (requires verification)

**Update Instructions:**
```gradle
// build.gradle.kts
dependencies {
    // SQLCipher 4.6.1+ should have Android 16KB support
    implementation("net.zetetic:sqlcipher-android:4.6.1")
}
```

**Notes:**
- SQLCipher 4.5.7+ includes 16KB page size fixes
- Verify alignment after updating
- Test database encryption/decryption after update

**Verification:**
```bash
# After updating, check library alignment:
readelf -l app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libsqlcipher.so | grep LOAD
# Should show 0x4000 (16KB) alignment
```

---

### Tesseract OCR (tess-two)

**Files:**
- `libtess.so`
- `liblept.so`
- `libjpgt.so`
- `libpngt.so`

**Current Status:** ⚠️ Unknown (requires verification)

**Update Instructions:**
```gradle
// build.gradle.kts
dependencies {
    // Check tess-two repository for 16KB support
    implementation("com.rmtheis:tess-two:9.1.0")
}
```

**Alternative:** Build from source:
```bash
# Clone tess-two repository
git clone https://github.com/rmtheis/tess-two.git
cd tess-two/tess-two

# Edit jni/Android.mk or build scripts to add:
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384

# Rebuild
ndk-build
```

---

### USB Library

**Files:**
- `libusb-lib.so`

**Current Status:** ⚠️ Unknown (requires verification)

**Action Required:**
1. Identify the exact USB library dependency (check gradle dependencies)
2. Check for updated version with 16KB support
3. If not available, rebuild from source with linker flags

**Identification:**
```bash
# Find USB dependency
./gradlew :app:dependencies | grep -i usb
```

---

### AndroidX Libraries

**Files:**
- `libandroidx.graphics.path.so`
- `libdatastore_shared_counter.so`

**Current Status:** ✅ Likely Compatible

**Notes:**
- AndroidX libraries are regularly updated by Google
- Google ensures 16KB compatibility for all AndroidX libraries
- Update to latest versions to ensure compatibility:

```gradle
// build.gradle.kts
dependencies {
    implementation("androidx.graphics:graphics-path:1.1.0")
    implementation("androidx.datastore:datastore:1.1.1")
}
```

---

### Unknown Library

**Files:**
- `libtoolChecker.so`

**Current Status:** ⚠️ Unknown (requires identification)

**Action Required:**
1. Identify the source of this library
2. Check if it's part of a dependency or custom-built
3. Update or rebuild with 16KB alignment

**Investigation:**
```bash
# Search for toolChecker in dependencies
./gradlew :app:dependencies | grep -i tool

# Check if it's in any Gradle files
rg -i "toolchecker" --type gradle
```

---

## Update Workflow

### 1. Identify Library Sources

```bash
# List all dependencies with native libraries
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E "\.(aar|jar)"

# Extract and check each AAR for native libraries
unzip -l app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/*.so
```

### 2. Update Dependencies

Update `gradle/libs.versions.toml` and module `build.gradle.kts` files with latest versions.

### 3. Verify Alignment

After each update:

```bash
# Rebuild app
./gradlew clean assembleDebug

# Run verification script
./verify-16kb-alignment.sh
```

### 4. Test on Device

```bash
# Install on 16KB device (when available)
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Check for dlopen errors
adb logcat | grep -E "dlopen|couldn't map"
```

---

## Migration Priority

**High Priority** (Core functionality):
1. ✅ **libconnectias_*.so** - All project libraries (DONE)
2. 🔄 **libsqlcipher.so** - Database encryption
3. 🔄 **libvlc.so** - Media playback

**Medium Priority** (Feature modules):
4. 🔄 **libopencv_java4.so** - Scanner feature
5. 🔄 **libtess.so**, **liblept.so** - OCR feature
6. 🔄 **libusb-lib.so** - USB feature

**Low Priority** (AndroidX - likely already compatible):
7. ⏸️ **libandroidx.*.so** - AndroidX libraries
8. ⏸️ **libtoolChecker.so** - Unknown (requires identification)

---

## Build From Source Template

For libraries without 16KB-compatible releases, use this template:

### CMake Projects

```cmake
# CMakeLists.txt
target_link_options(your_library PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
```

### Autotools Projects

```bash
# configure script
./configure \
    --host=aarch64-linux-android \
    LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
make
```

### NDK-Build Projects

```makefile
# Android.mk
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
```

### Makefile Projects

```makefile
# Makefile
LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
```

---

## Verification Checklist

Before marking a library as ✅ Compatible:

- [ ] Library version updated to latest
- [ ] `readelf -l libfoo.so | grep LOAD` shows `0x4000` alignment
- [ ] `./verify-16kb-alignment.sh` passes
- [ ] Tested on actual device (if available)
- [ ] No dlopen errors in logcat

---

## Resources

- [Android 16KB Page Size Guide](https://developer.android.com/guide/practices/page-sizes)
- [SQLCipher 16KB Support](https://github.com/sqlcipher/sqlcipher-android/issues/587)
- [VLC Android Build Guide](https://code.videolan.org/videolan/vlc-android)
- [OpenCV Android](https://github.com/opencv/opencv)
- [NDK Linker Flags](https://developer.android.com/ndk/guides/cmake)

---

## Next Steps

1. **Immediate:** Run `./verify-16kb-alignment.sh` to identify misaligned libraries
2. **Short-term:** Update high-priority dependencies (SQLCipher, VLC)
3. **Medium-term:** Update or rebuild medium-priority dependencies
4. **Long-term:** Monitor AndroidX updates, test on 16KB devices when available

**Last Updated:** 2026-02-11
**Status:** In Progress
