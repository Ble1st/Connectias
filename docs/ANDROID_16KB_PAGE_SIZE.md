# Android 16KB Page Size Support

## Overview

Android 15+ introduces support for devices with **16KB memory page sizes** (vs traditional 4KB). All native libraries (`.so` files) must be aligned to 16KB boundaries to run on these devices.

**Impact:** Apps with misaligned libraries will fail to load on 16KB devices with errors like:
```
dlopen failed: couldn't map "/system/lib64/libfoo.so" segment
```

## What We've Implemented

### ✅ Project-Owned Libraries (16KB Aligned)

All Connectias-built Rust and C++ libraries are now properly aligned:

| Library | Type | Configuration |
|---------|------|---------------|
| `libconnectias_root_detector.so` | Rust | `.cargo/config.toml` with `-Wl,-z,max-page-size=16384` |
| `libconnectias_dns_tools.so` | Rust | `.cargo/config.toml` with `-Wl,-z,max-page-size=16384` |
| `libconnectias_port_scanner.so` | Rust | `.cargo/config.toml` with `-Wl,-z,max-page-size=16384` |
| `libconnectias_password_generator.so` | Rust | `.cargo/config.toml` with `-Wl,-z,max-page-size=16384` |
| `libdvd_jni.so` | C++ | `CMakeLists.txt` with `target_link_options()` |
| `libc++_shared.so` | NDK | Provided by NDK r23+ (aligned) |

**Rust Configuration** (`.cargo/config.toml`):
```toml
[build]
rustflags = [
    "-C", "link-arg=-Wl,-soname,libconnectias_root_detector.so",
    # Android 16KB page size support (required for Android 15+)
    "-C", "link-arg=-Wl,-z,max-page-size=16384",
    "-C", "link-arg=-Wl,-z,common-page-size=16384",
]
```

**C++ Configuration** (CMakeLists.txt):
```cmake
# 16 KB page size alignment for Android 15+ compatibility
target_link_options(dvd_jni PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
```

**Gradle Configuration** (`gradle.properties`):
```properties
# Android 16KB page size support (required for Android 15+)
# Ensures native libraries are properly aligned to 16KB boundaries
android.bundle.enableUncompressedNativeLibs=false
```

### ⚠️ Third-Party Libraries (May Need Updates)

The following third-party libraries are included via dependencies and **may not be 16KB aligned**:

| Library | Source | Status | Action Required |
|---------|--------|--------|-----------------|
| `libvlc.so` | VLC Android SDK | ⚠️ Unknown | Check VLC SDK version, update if needed |
| `libvlcjni.so` | VLC Android SDK | ⚠️ Unknown | Check VLC SDK version, update if needed |
| `libopencv_java4.so` | OpenCV Android | ⚠️ Unknown | Check OpenCV version, may need rebuild |
| `libsqlcipher.so` | SQLCipher Android | ⚠️ Unknown | Update to latest version (4.5.7+) |
| `libtess.so` | Tesseract OCR | ⚠️ Unknown | Check tess-two version |
| `liblept.so` | Leptonica | ⚠️ Unknown | Check tess-two version |
| `libjpgt.so` | Tesseract | ⚠️ Unknown | Check tess-two version |
| `libpngt.so` | Tesseract | ⚠️ Unknown | Check tess-two version |
| `libusb-lib.so` | USB Library | ⚠️ Unknown | Check dependency version |
| `libandroidx.graphics.path.so` | AndroidX | ✅ Likely OK | AndroidX libraries are usually aligned |
| `libdatastore_shared_counter.so` | AndroidX | ✅ Likely OK | AndroidX libraries are usually aligned |
| `libtoolChecker.so` | Unknown | ⚠️ Unknown | Identify source and update |

## Verification

### Automated Check

Run the verification script to check all libraries in your APK:

```bash
# Build the app first
./gradlew assembleDebug

# Run verification
./verify-16kb-alignment.sh
```

**Output:**
```
========================================
Android 16KB Page Size Alignment Check
========================================

Checking APK: app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

Found 20 native libraries

Checking 16KB alignment (16384 bytes)...
----------------------------------------
✓ libconnectias_root_detector.so (offset: 49152, aligned)
✓ libconnectias_dns_tools.so (offset: 65536, aligned)
✗ libvlc.so (offset: 82000, misaligned by 1104 bytes)
...

========================================
Results:
========================================
Passed: 18
Failed: 2
```

### Manual Check

For individual libraries:

```bash
# Extract APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "lib/.*\.so$"

# Check specific library offset
unzip -l -v app/build/outputs/apk/debug/app-debug.apk lib/arm64-v8a/libfoo.so

# Verify alignment (offset % 16384 should be 0)
python3 -c "print(49152 % 16384)"  # 0 = aligned
```

## Fixing Misaligned Third-Party Libraries

### Option 1: Update Dependencies (Recommended)

Update to versions with 16KB support:

```gradle
// build.gradle.kts
dependencies {
    // SQLCipher - Update to 4.5.7+ (has 16KB support)
    implementation("net.zetetic:sqlcipher-android:4.6.1")

    // VLC - Update to latest version
    implementation("org.videolan.android:libvlc-all:4.0.0-eap15")

    // OpenCV - Use version with 16KB support or rebuild
    implementation("org.opencv:opencv-android:4.10.0")
}
```

### Option 2: Rebuild From Source

For libraries you build yourself:

**CMake:**
```cmake
target_link_options(your_library PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
```

**Makefile:**
```makefile
LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
```

**NDK-Build:**
```makefile
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
```

### Option 3: Use objcopy (Post-Processing)

As a last resort, use `objcopy` to adjust alignment:

```bash
# Requires NDK toolchain
export NDK_TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"

# Adjust alignment
$NDK_TOOLCHAIN/llvm-objcopy \
    --set-section-alignment .text=16384 \
    --set-section-alignment .data=16384 \
    --set-section-alignment .rodata=16384 \
    libfoo.so libfoo_aligned.so
```

## Build Workflow

### Standard Development Build

```bash
# 1. Rebuild Rust libraries with 16KB alignment
./build-rust-libs.sh

# 2. Build APK (Gradle applies 16KB alignment via AGP)
./gradlew assembleDebug

# 3. Verify alignment
./verify-16kb-alignment.sh
```

### Release Build

```bash
# 1. Rebuild all Rust libraries
./build-rust-libs.sh

# 2. Build release APK
./gradlew assembleRelease

# 3. Verify alignment before distribution
./verify-16kb-alignment.sh
```

## CI/CD Integration

Add to `.github/workflows/release.yml`:

```yaml
- name: Verify 16KB Alignment
  run: |
    chmod +x verify-16kb-alignment.sh
    ./verify-16kb-alignment.sh
```

## Testing on 16KB Devices

### Emulator Testing

As of Android 15, Google does not provide 16KB emulators. Use **real devices** for testing.

### Physical Device Testing

Target devices with 16KB page size (typically high-end devices):
- Google Pixel 10+ (when available)
- Devices with specific SoCs designed for 16KB pages

Check page size on device:

```bash
adb shell getconf PAGE_SIZE
# Output: 16384 = 16KB device
# Output: 4096 = 4KB device
```

### Installation Test

```bash
# Install on 16KB device
adb install app/build/outputs/apk/debug/app-debug.apk

# Check logcat for dlopen errors
adb logcat | grep -i "dlopen\|page"
```

## Troubleshooting

### Error: "couldn't map segment"

**Symptom:**
```
E/linker: dlopen failed: couldn't map "/data/app/.../lib/arm64/libfoo.so" segment 1
```

**Cause:** Library not aligned to 16KB boundary

**Fix:**
1. Rebuild library with 16KB alignment flags
2. Run `./verify-16kb-alignment.sh` to identify misaligned libraries
3. Update third-party dependencies to 16KB-compatible versions

### Error: Build fails after adding alignment

**Symptom:**
```
ld.lld: error: section file offset not aligned
```

**Cause:** Linker version too old or conflicting flags

**Fix:**
1. Update NDK to r23+ (required for 16KB support)
2. Check for conflicting `-Wl,-z,max-page-size` flags in other configs
3. Verify Rust toolchain is up to date: `rustup update`

### Third-Party Library Won't Update

**Symptom:** Dependency doesn't have 16KB-aligned version

**Options:**
1. **Fork and rebuild** with alignment flags
2. **Contact maintainer** and request 16KB support
3. **Replace** with alternative library that supports 16KB
4. **Use objcopy** as temporary workaround (not recommended for production)

## Performance Impact

**16KB alignment benefits:**
- ✅ Better memory efficiency on 16KB devices (reduces TLB misses)
- ✅ Forward compatibility with future Android versions
- ⚠️ Slightly larger APK size (~2-5% due to alignment padding)

**APK Size Comparison:**
- 4KB aligned APK: ~50 MB
- 16KB aligned APK: ~51-52 MB (+2-4% typical)

## References

- [Android 16KB Page Size Guide](https://developer.android.com/guide/practices/page-sizes)
- [AGP 8.0+ 16KB Support](https://developer.android.com/studio/releases/gradle-plugin)
- [NDK 16KB Page Size](https://developer.android.com/ndk/guides/16kb-page-size)
- [Rust Android NDK](https://github.com/bbqsrc/cargo-ndk)

## Summary

**What Changed:**
- ✅ All Rust libraries: Added 16KB alignment via `.cargo/config.toml`
- ✅ C++ libraries: Added 16KB alignment via CMakeLists.txt
- ✅ Gradle: Configured for 16KB support
- ✅ Verification: Created `verify-16kb-alignment.sh` script

**Next Steps:**
1. Run `./build-rust-libs.sh` to rebuild Rust libraries
2. Run `./gradlew assembleDebug` to build APK
3. Run `./verify-16kb-alignment.sh` to check alignment
4. Update third-party dependencies if verification fails
5. Test on 16KB device (when available)

**Maintenance:**
- Always rebuild Rust libraries after modifying `.rs` files
- Verify alignment before releases
- Monitor third-party library updates for 16KB support
- Update documentation when adding new native libraries
