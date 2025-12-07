# VLC DVD Playback - Debug Summary

## Current Status: DEBUGGING IN PROGRESS

**Last Updated:** 2024-12-07

---

## 1. Problem Description

### Symptom
When attempting to play a DVD from a USB optical drive, the VLC MediaPlayer immediately sends a "Stopped" event after `player.play()` is called. The screen remains black with no video or audio output.

### Expected Behavior
VLC should read data from the USB block device via custom native callbacks and display the DVD content.

---

## 2. Architecture Overview

### Data Flow
```
USB DVD Drive → USB Block Device Driver → Native Callbacks (JNI) → VLC LibVLC → MediaPlayer → UI
```

### Key Components

1. **VlcDvdPlayer.kt** - Kotlin class managing VLC playback
   - Initializes LibVLC and MediaPlayer
   - Provides IO callbacks (ioOpen, ioRead, ioSeek, ioClose)
   - Uses reflection to get native LibVLC handle

2. **vlc_jni.cpp** - Native JNI bridge
   - Loads libvlc.so dynamically
   - Implements VLC media callbacks (media_open_cb, media_read_cb, etc.)
   - Creates libvlc_media_t with custom callbacks

3. **vlc_stub.h** - Header with VLC type definitions
   - Defines libvlc_instance_t, libvlc_media_t
   - Defines callback function signatures

---

## 3. Root Cause Analysis

### Issue Identified
The `libvlc_instance_t*` pointer obtained from the Java `LibVLC` object's `mInstance` field is **INVALID**.

### Evidence
1. The pointer value `0x800c0e3a131e090` (576672836197933200 decimal) looks suspicious
2. Calling `libvlc_media_new_callbacks()` with this pointer causes:
   - Either a crash (no return log)
   - Or VLC immediately sends "Stopped" event

### Why the Handle is Invalid
The `mInstance` field in `org.videolan.libvlc.VLCObject` does **NOT** directly contain the native `libvlc_instance_t*` pointer. It may contain:
- An encoded value
- A JNI reference
- An internal handle that needs transformation

---

## 4. Attempted Solutions

### Attempt 1: Use Java LibVLC Handle Directly
**Approach:** Extract `mInstance` field from Java `LibVLC` object via reflection
**Result:** FAILED - Pointer is invalid, causes crash in VLC

### Attempt 2: Create Own libvlc_instance_t with libvlc_new()
**Approach:** Call `libvlc_new()` directly to create our own VLC instance
**Result:** FAILED - VLC Android requires JavaVM to be registered first
**Error:** `assertion "s_jvm != NULL" failed` in `system_Configure()`

### Attempt 3: Enhanced Logging
**Approach:** Add extensive logging to identify exactly where the failure occurs
**Result:** USEFUL - Confirmed that `libvlc_media_new_callbacks()` either crashes or fails

---

## 5. Current Implementation State

### vlc_jni.cpp - Key Functions

#### nativeInit()
```cpp
// Loads libvlc.so dynamically
// Finds symbols: libvlc_media_new_callbacks, libvlc_media_release
// Initializes g_vm (JavaVM reference)
```

#### nativeCreateMedia(libVlcInstance)
```cpp
// Receives handle from Kotlin
// Creates JavaCallbackData struct with JNI method references
// Calls libvlc_media_new_callbacks() with:
//   - libvlc_instance_t* (from Java handle)
//   - media_open_cb, media_read_cb, media_seek_cb, media_close_cb
//   - opaque data pointer
```

#### Callback Functions
```cpp
// media_open_cb: Calls Java ioOpen(), gets stream size
// media_read_cb: Calls Java ioRead(), copies data to buffer
// media_seek_cb: Calls Java ioSeek()
// media_close_cb: Calls Java ioClose(), cleanup
```

### VlcDvdPlayer.kt - Key Functions

#### getLibVlcHandle()
```kotlin
// Uses reflection to find native handle in LibVLC class hierarchy
// Searches for fields: mLibVlcInstance, mInstance
// NOW: Logs ALL Long fields for debugging
```

#### IO Callbacks
```kotlin
// ioOpen(): Validates currentBlockDevice, returns size
// ioRead(): Reads from USB block device with buffering
// ioSeek(): Updates current offset
// ioClose(): Cleanup
```

---

## 6. Diagnostic Logs Added

### Native Side (vlc_jni.cpp)
```
=== CRITICAL: About to call libvlc_media_new_callbacks() ===
=== Instance ptr: 0x... ===
=== CALLING libvlc_media_new_callbacks() NOW ===
=== libvlc_media_new_callbacks() RETURNED: 0x... ===  // If we see this, call succeeded
media_open_cb() CALLED - opaque: 0x...  // If we see this, callback is invoked
```

### Kotlin Side (VlcDvdPlayer.kt)
```
=== Inspecting class X: org.videolan.libvlc.XXX ===
FIELD: mInstance (Long) = XXX (0xXXX)
SELECTED FIELD: mInstance from XXX = XXX
```

---

## 7. Next Steps

### Immediate
1. **Run app with new logging** to see all fields in LibVLC class
2. **Analyze logs** to find correct native pointer field
3. **Identify** if there's another field containing the valid pointer

### Potential Solutions

#### Option A: Find Correct Field
If LibVLC stores the native pointer in a different field, use that instead.

#### Option B: Use libvlcjni.so Native Method
The `libvlcjni.so` library may have a method that returns the correct pointer.
Could try loading it and calling its native functions.

#### Option C: Alternative Media Creation
Instead of using `libvlc_media_new_callbacks()`, try:
- Creating media from file descriptor
- Using VLC's built-in DVD support if accessible

#### Option D: Direct MediaPlayer Integration
If the Java `MediaPlayer` already has access to the native instance,
we might be able to set media through Java API instead of JNI.

---

## 8. File Changes Summary

### Modified Files

| File | Changes |
|------|---------|
| `vlc_jni.cpp` | Added extensive logging, callback implementations, symbol loading |
| `vlc_stub.h` | Added libvlc_new_t, libvlc_release_t type definitions |
| `VlcDvdPlayer.kt` | Enhanced getLibVlcHandle() to log all fields, added @Volatile |

### Key Code Sections

#### vlc_jni.cpp - Lines 37-107
Media callback implementations with JNI thread handling

#### vlc_jni.cpp - Lines 267-330
nativeInit() - Symbol loading and initialization

#### vlc_jni.cpp - Lines 332-450
nativeCreateMedia() - Media creation with callbacks

#### VlcDvdPlayer.kt - Lines 434-490
getLibVlcHandle() - Reflection-based field discovery

---

## 9. Technical Details

### VLC Android Specifics
- VLC Android uses `libvlcjni.so` as JNI bridge
- `libvlc.so` requires JavaVM to be registered via `JNI_OnLoad` in libvlcjni
- Direct calls to `libvlc_new()` fail because `s_jvm` is not set

### JNI Threading
- VLC may call callbacks from different threads
- Must use `AttachCurrentThread()` / `DetachCurrentThread()` for JNI calls
- `g_vm` (JavaVM reference) must be initialized before callbacks are invoked

### Memory Management
- `JavaCallbackData` struct holds global JNI references
- Must be cleaned up in `media_close_cb()`
- Global refs prevent garbage collection of Java objects

---

## 10. Test Procedure

1. Build and install app
2. Insert DVD in USB optical drive
3. Navigate to DVD player screen
4. Tap Play
5. Capture logcat with filter: `VlcJni|VlcDvdPlayer`
6. Look for:
   - All FIELD entries from LibVLC inspection
   - `libvlc_media_new_callbacks() RETURNED` message
   - `media_open_cb() CALLED` message
   - Any crash reports or SIGABRT

---

## 11. Dependencies

- **libvlc-android**: Version 3.6.4
- **VLC**: 3.0.22-rc1 Vetinari
- **Target Platform**: Android arm64-v8a
- **NDK**: 27.0.12077973

---

## 12. References

- [LibVLC Media Documentation](https://videolan.videolan.me/vlc/group__libvlc__media.html)
- [VLC Android Source](https://code.videolan.org/videolan/vlc-android)
- [libvlc_media_new_callbacks API](https://videolan.videolan.me/vlc-3.0/group__libvlc__media.html)
