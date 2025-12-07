# VLC Playback Issue Analysis

**Date:** 2025-12-07  
**Issue:** VLC immediately sends "Stopped" event after `player.play()` is called  
**Status:** Under Investigation

---

## Problem Description

After successfully initializing VLC and setting up custom native input callbacks for USB block device access, VLC immediately sends a "Stopped" event (type 262) when `player.play()` is called. This prevents DVD playback from starting.

### Observed Behavior

From the logcat output:
```
2025-12-07 15:38:40.928 VlcDvdPlayer: Attempting to setup Custom Native Input for USB Block Device
2025-12-07 15:38:40.948 VlcDvdPlayer: Event received - type: 262, buffering: 0.0%
2025-12-07 15:38:40.948 VlcDvdPlayer: VLC Event - Stopped
```

**Missing Logs:**
- No logs from `nativeCreateMedia()` return value
- No logs from `ioOpen()` being called
- No logs from `media_open_cb()` in native code

This suggests one of the following scenarios:
1. `nativeCreateMedia()` failed silently (returned 0)
2. `ioOpen()` callback was never invoked
3. `ioOpen()` was called but failed, causing immediate stop

---

## Root Cause Analysis

### Possible Causes

#### 1. **`ioOpen()` Callback Failure** (Most Likely)

**Symptom:** VLC calls `media_open_cb()` → `ioOpen()` → returns `false` → VLC immediately stops

**Why it might fail:**
- `currentBlockDevice` is `null` when `ioOpen()` is called
- Exception in `ioOpen()` (block size retrieval, device initialization)
- Device session was closed before VLC tried to open

**Code Location:** `VlcDvdPlayer.kt:522-554`

#### 2. **`nativeCreateMedia()` Returns 0**

**Symptom:** Custom native input setup fails, falls back to MRL which doesn't work for USB devices

**Why it might fail:**
- Invalid `libVLC` instance handle (reflection failed)
- `libvlc_media_new_callbacks()` returns NULL
- Callback method lookup fails in JNI

**Code Location:** `vlc_jni.cpp:302-389`

#### 3. **Media Options Not Applied**

**Symptom:** Media created but VLC doesn't recognize it as DVD, fails to initialize demuxer

**Why it might fail:**
- Options added after media is already parsed
- Option syntax incorrect
- DVD demuxer not available in LibVLC build

**Code Location:** `VlcDvdPlayer.kt:264-283`

#### 4. **Threading/Timing Issue**

**Symptom:** `currentBlockDevice` set on one thread, but `ioOpen()` called on different thread before assignment completes

**Why it might fail:**
- Race condition between setting `currentBlockDevice` and VLC calling `ioOpen()`
- Device session closed between `playDvd()` call and `ioOpen()` callback

---

## Diagnostic Steps

### Step 1: Verify `ioOpen()` is Called

Add more detailed logging in `ioOpen()`:

```kotlin
fun ioOpen(): Boolean {
    Timber.i("VlcDvdPlayer: ioOpen() CALLED - Thread: ${Thread.currentThread().name}")
    Timber.i("VlcDvdPlayer: ioOpen() - currentBlockDevice: ${currentBlockDevice != null}")
    if (currentBlockDevice == null) {
        Timber.e("VlcDvdPlayer: ioOpen() - currentBlockDevice is NULL - This will cause VLC to stop immediately!")
        return false
    }
    // ... rest of implementation
}
```

### Step 2: Verify `nativeCreateMedia()` Success

Check if the native media handle is valid:

```kotlin
val nativeMediaHandle = withContext(Dispatchers.IO) {
    val handle = nativeCreateMedia(instanceHandle)
    Timber.i("VlcDvdPlayer: nativeCreateMedia returned handle: $handle")
    if (handle == 0L) {
        Timber.e("VlcDvdPlayer: nativeCreateMedia FAILED - Check native logs for libvlc_media_new_callbacks errors")
    }
    handle
}
```

### Step 3: Check Native Logs

Look for native log messages in logcat:
- `VlcJni: media_open_cb() called` - Should appear when VLC tries to open
- `VlcJni: nativeCreateMedia() - libvlc_media_new_callbacks() returned: ...` - Check if NULL

### Step 4: Verify Device Session

Ensure the device session is still open when `ioOpen()` is called:

```kotlin
fun ioOpen(): Boolean {
    val device = currentBlockDevice
    if (device == null) {
        Timber.e("VlcDvdPlayer: ioOpen() - currentBlockDevice is null")
        return false
    }
    
    // Verify device is still accessible
    try {
        val testSize = device.blockSize
        Timber.d("VlcDvdPlayer: ioOpen() - Device accessible, blockSize: $testSize")
    } catch (e: Exception) {
        Timber.e(e, "VlcDvdPlayer: ioOpen() - Device not accessible!")
        return false
    }
    // ... rest
}
```

---

## Recommended Fixes

### Fix 1: Ensure Device Session Persistence

**Problem:** Device session might be closed before VLC opens the stream.

**Solution:** Ensure the session remains open throughout playback:

```kotlin
// In VlcDvdPlayer.playDvd()
// Don't rely on external session management
// Instead, ensure driver is valid and will remain valid
if (driver != null) {
    // Verify driver is still valid
    try {
        val testBlockSize = driver.blockSize
        Timber.d("VlcDvdPlayer: Driver verified, blockSize: $testBlockSize")
    } catch (e: Exception) {
        Timber.e(e, "VlcDvdPlayer: Driver is invalid or closed!")
        // Don't proceed with custom input
        driver = null
    }
}
```

### Fix 2: Add Synchronization for `currentBlockDevice`

**Problem:** Race condition between setting `currentBlockDevice` and `ioOpen()` callback.

**Solution:** Use atomic reference or ensure proper synchronization:

```kotlin
@Volatile
private var currentBlockDevice: UsbBlockDevice? = null

// In playDvd(), set it before creating media:
currentBlockDevice = driver
// Add memory barrier
kotlinx.coroutines.ensureActive()
// Then proceed with nativeCreateMedia
```

### Fix 3: Improve Error Handling in `ioOpen()`

**Problem:** Exceptions in `ioOpen()` might not be logged properly.

**Solution:** Wrap entire function in try-catch:

```kotlin
fun ioOpen(): Boolean {
    return try {
        Timber.i("VlcDvdPlayer: ioOpen() called")
        if (currentBlockDevice == null) {
            Timber.e("VlcDvdPlayer: ioOpen() - currentBlockDevice is null")
            return false
        }
        
        val blockSize = currentBlockDevice?.blockSize ?: 2048
        deviceSize = 8L * 1024 * 1024 * 1024 // 8GB placeholder
        currentOffset = 0
        readBuffer = null
        readBufferOffset = -1
        readBufferSize = 0
        
        Timber.i("VlcDvdPlayer: ioOpen() - Success")
        true
    } catch (e: Exception) {
        Timber.e(e, "VlcDvdPlayer: ioOpen() - Exception: ${e.message}")
        false
    }
}
```

### Fix 4: Verify Media Options Timing

**Problem:** Options might need to be set before media is parsed.

**Solution:** Set options immediately after creating Media wrapper, before any other operations:

```kotlin
val m = createMediaFromHandle(vlc, nativeMediaHandle)
if (m != null) {
    // Set options IMMEDIATELY, before any other operations
    m.addOption(":demux=dvdnav")
    m.addOption(":dvdnav-menu")
    m.addOption(":dvdnav-angle=1")
    Timber.d("VlcDvdPlayer: DVD options set on Media object")
}
```

### Fix 5: Add Fallback Error Recovery

**Problem:** If custom input fails, there's no user-visible error.

**Solution:** Log clear error and potentially show user message:

```kotlin
if (media == null) {
    Timber.e("VlcDvdPlayer: Could not create Media object")
    Timber.e("VlcDvdPlayer: Custom native input failed, and MRL fallback not available for USB devices")
    // Could emit error state to UI here
    return
}
```

---

## Testing Plan

1. **Enable Verbose Logging:**
   - Set Timber log level to VERBOSE
   - Filter logcat for "VlcDvdPlayer" and "VlcJni"

2. **Test `ioOpen()` Call:**
   - Add breakpoint in `ioOpen()`
   - Verify it's called when `player.play()` is invoked
   - Check `currentBlockDevice` is not null

3. **Test Native Callbacks:**
   - Check native logs for `media_open_cb()` invocation
   - Verify JNI method lookup succeeds

4. **Test Device Session:**
   - Verify session is open when `playDvd()` is called
   - Verify session remains open during `ioOpen()` callback

5. **Test Media Creation:**
   - Verify `nativeCreateMedia()` returns non-zero handle
   - Verify `createMediaFromHandle()` succeeds

---

## Next Steps

1. **Immediate:** Add comprehensive logging to identify which failure path is taken
2. **Short-term:** Implement Fix 1-3 to address most likely causes
3. **Long-term:** Add proper error reporting to UI when playback fails

---

## Related Files

- `feature-dvd/src/main/java/com/ble1st/connectias/feature/dvd/media/VlcDvdPlayer.kt`
- `feature-dvd/src/main/cpp/vlc_jni.cpp`
- `feature-dvd/src/main/java/com/ble1st/connectias/feature/dvd/storage/OpticalDriveProvider.kt`
- `feature-dvd/src/main/java/com/ble1st/connectias/feature/dvd/ui/VlcPlayerScreen.kt`
