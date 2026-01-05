# 16 KB Page Size Compatibility

## Problem

Android 15+ requires all native libraries to support 16 KB page sizes. Starting November 1st, 2025, all new apps and updates targeting Android 15+ must support 16 KB page sizes.

### Current Status

**Our own native libraries** (built from source):
- ✅ `dvd_jni.so` - Configured with 16 KB alignment
- ✅ `dvdcss` (static) - Configured with 16 KB alignment
- ✅ `dvdread` (static) - Configured with 16 KB alignment
- ✅ `dvdnav` (static) - Configured with 16 KB alignment

**Pre-built libraries** (from LibVLC dependency):
- ⚠️ `libvlc.so` - May not be aligned to 16 KB boundaries
- ⚠️ `libvlcjni.so` - May not be aligned to 16 KB boundaries
- ⚠️ `libc++_shared.so` - May not be aligned to 16 KB boundaries

## Current LibVLC Version

- **Current**: 3.6.4 (updated from 3.6.0)
- **Status**: Unclear if 3.6.4 includes 16 KB page size support
- **Known Issue**: [VLC Android Issue #3242](https://code.videolan.org/videolan/vlc-android/-/issues/3242)
- **Fix in Progress**: [Merge Request #140](https://goldeneye2.videolan.org/videolan/libvlcjni/-/merge_requests/140)

## Solutions

### Option 1: Wait for LibVLC Update (Recommended for now)

Monitor LibVLC releases for official 16 KB support:
- Check [Maven Central](https://mvnrepository.com/artifact/org.videolan.android/libvlc-all)
- Monitor [VLC Android Issues](https://code.videolan.org/videolan/vlc-android/-/issues)

### Option 2: Build LibVLC from Source

If LibVLC doesn't release a fixed version before November 2025:

1. Clone VLC Android repository
2. Add 16 KB alignment flags to build configuration:
   ```cmake
   target_link_options(libvlc PRIVATE
       -Wl,-z,max-page-size=16384
       -Wl,-z,common-page-size=16384
   )
   ```
3. Build and include the `.so` files in `jniLibs/`

### Option 3: Use Alternative Library

Consider alternatives if LibVLC support is delayed:
- ExoPlayer (limited DVD menu support)
- Custom decoder implementation
- Other media frameworks

### Option 4: Temporary Workaround

Until November 2025, apps can still be published without 16 KB support. However, this is only a temporary solution.

## Verification

To check if libraries are aligned:

```bash
# Check library alignment
readelf -l libvlc.so | grep LOAD
# Look for alignment values - should be 0x4000 (16384 = 16 KB) or higher
```

## References

- [Android 16 KB Page Size Requirements](https://developer.android.com/16kb-page-size)
- [VLC Android Issue #3242](https://code.videolan.org/videolan/vlc-android/-/issues/3242)
- [LibVLC JNI Merge Request #140](https://goldeneye2.videolan.org/videolan/libvlcjni/-/merge_requests/140)

