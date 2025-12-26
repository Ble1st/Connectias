package com.ble1st.connectias.feature.dvd.native

import com.ble1st.connectias.feature.dvd.BuildConfig
import com.ble1st.connectias.feature.dvd.driver.UsbBlockDevice
import timber.log.Timber

/**
 * Native interface for DVD operations via libdvdread/libdvdnav/libdvdcss.
 * 
 * Uses lazy loading to prevent hard failures when native libraries are unavailable,
 * allowing graceful degradation.
 */
object DvdNative {
    private const val LOG_VERBOSE = false
    private inline fun vLog(msg: () -> String) { if (LOG_VERBOSE) Timber.d(msg()) }
    
    @Volatile
    private var libraryLoaded = false
    
    @Volatile
    private var libraryAvailable = false
    
    @Volatile
    private var cssDecryptionEnabled = false
    
    @Volatile
    private var dvdReadAvailable = false
    
    private val libraryLoadLock = Any()
    
    /**
     * Ensures the native library is loaded. Thread-safe and idempotent.
     * @return true if library is available, false otherwise
     */
    internal fun ensureLibraryLoaded(): Boolean {
        vLog { "DvdNative: ensureLibraryLoaded() called" }
        if (libraryLoaded) {
            vLog { "DvdNative: ensureLibraryLoaded() - Library already loaded, available: $libraryAvailable" }
            return libraryAvailable
        }
        
        vLog { "DvdNative: ensureLibraryLoaded() - Acquiring lock for thread-safe loading" }
        synchronized(libraryLoadLock) {
            vLog { "DvdNative: ensureLibraryLoaded() - Lock acquired" }
            if (libraryLoaded) {
                vLog { "DvdNative: ensureLibraryLoaded() - Library loaded by another thread, available: $libraryAvailable" }
                return libraryAvailable
            }
            
            try {
                vLog { "DvdNative: ensureLibraryLoaded() - Loading DVD JNI native library..." }
                vLog { "DvdNative: ensureLibraryLoaded() - Calling System.loadLibrary(\"dvd_jni\")" }
                System.loadLibrary("dvd_jni")
                vLog { "DvdNative: ensureLibraryLoaded() - System.loadLibrary() completed" }
                libraryAvailable = true
                // dvdread/dvdnav are statically linked into dvd_jni
                dvdReadAvailable = true 
                vLog { "DvdNative: ensureLibraryLoaded() - DVD JNI native library loaded successfully" }
                vLog { "DvdNative: ensureLibraryLoaded() - libraryAvailable: $libraryAvailable, dvdReadAvailable: $dvdReadAvailable" }
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "DvdNative: ensureLibraryLoaded() - Failed to load DVD JNI native library - DVD functionality will be unavailable")
                libraryAvailable = false
                dvdReadAvailable = false
                Timber.e("DvdNative: ensureLibraryLoaded() - libraryAvailable: $libraryAvailable, dvdReadAvailable: $dvdReadAvailable")
                libraryLoaded = true
                return false
            }
            
            // CSS decryption is now statically linked into libdvdread/libdvd_jni
            // No need to load libdvdcss separately - it's embedded in the native library
            vLog { "DvdNative: ensureLibraryLoaded() - Checking CSS decryption build config" }
            cssDecryptionEnabled = BuildConfig.ENABLE_DVD_CSS
            vLog { "DvdNative: ensureLibraryLoaded() - BuildConfig.ENABLE_DVD_CSS: ${BuildConfig.ENABLE_DVD_CSS}" }
            if (cssDecryptionEnabled) {
                Timber.i("DvdNative: ensureLibraryLoaded() - CSS decryption enabled (statically linked via libdvdcss)")
            } else {
                vLog { "DvdNative: ensureLibraryLoaded() - CSS decryption disabled via build config" }
            }
            
            libraryLoaded = true
            vLog { "DvdNative: ensureLibraryLoaded() - Library loading complete, returning true" }
            return true
        }
    }

    /**
     * Opens a DVD at the specified path.
     * 
     * @param path Mount point or path to the DVD device
     * @return A valid handle (!= -1L) on success, or -1L on failure
     * 
     * **Lifecycle Requirements:**
     * - Callers should call [dvdClose] for every valid handle returned by [dvdOpen] (i.e., when the return value is not -1L)
     * - [dvdClose] is safe to call even if [dvdOpen] returns -1L (will silently return)
     * - [dvdClose] is idempotent and safe to call multiple times
     * - Use try/finally blocks or the [DvdHandle.use] extension to ensure cleanup
     * - Invalid or closed handles will cause operations to fail or throw exceptions
     * 
     * **Usage Pattern:**
     * ```
     * val handle = DvdNative.dvdOpen(path)
     * if (handle != -1L) {
     *     try {
     *         // Use handle
     *     } finally {
     *         DvdNative.dvdClose(handle)
     *     }
     * }
     * ```
     */
    fun dvdOpen(path: String): Long {
        vLog { "DvdNative: dvdOpen() called - path: $path" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdOpen() - DVD JNI library not available - cannot open DVD")
            return -1L
        }
        vLog { "DvdNative: dvdOpen() - Library loaded, calling native dvdOpenNative()" }
        val handle = dvdOpenNative(path)
        vLog { "DvdNative: dvdOpen() - Native dvdOpenNative() returned handle: $handle" }
        return handle
    }
    
    private external fun dvdOpenNative(path: String): Long

    /**
     * Opens a DVD using a custom UsbBlockDevice stream.
     *
     * @param driver The UsbBlockDevice instance (e.g. ScsiDriver)
     * @return A valid handle (!= -1L) on success, or -1L on failure
     */
    fun dvdOpenStream(driver: UsbBlockDevice): Long {
        vLog { "DvdNative: dvdOpenStream() called - driver: $driver" }
        vLog { "DvdNative: dvdOpenStream() - driver.blockSize: ${driver.blockSize}" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdOpenStream() - DVD JNI library not available - cannot open DVD via Stream")
            return -1L
        }
        vLog { "DvdNative: dvdOpenStream() - Library loaded, calling native dvdOpenStreamNative()" }
        val handle = dvdOpenStreamNative(driver)
        vLog { "DvdNative: dvdOpenStream() - Native dvdOpenStreamNative() returned handle: $handle" }
        return handle
    }

    private external fun dvdOpenStreamNative(driver: UsbBlockDevice): Long
    
    /**
     * Streams a title to a file descriptor.
     * This is a blocking call.
     *
     * @param handle The DVD handle
     * @param titleNumber Title number (1-based)
     * @param outFd File descriptor to write to
     * @return Bytes written or -1
     */
    external fun dvdStreamToFdNative(handle: Long, titleNumber: Int, outFd: Int): Long
    
    /**
     * Closes a DVD handle.
     * 
     * **Behavior:**
     * - Handles <= 0 are treated as invalid and this method silently returns (no-op)
     * - This method is idempotent: calling it multiple times with the same handle is safe
     * - For valid handles (> 0), this releases native resources
     * 
     * **Lifecycle:**
     * - Should be called for every valid handle returned by [dvdOpen] (i.e., when [dvdOpen] returned a value != -1L)
     * - Safe to call even if [dvdOpen] returned -1L (will silently return)
     * - Safe to call multiple times with the same handle
     * 
     * @param handle The handle to close (valid handle from [dvdOpen], or <= 0 for no-op)
     */
    fun dvdClose(handle: Long) {
        vLog { "DvdNative: dvdClose() called - handle: $handle" }
        if (handle <= 0) {
            vLog { "DvdNative: dvdClose() - Invalid handle (<= 0), skipping" }
            return
        }
        if (!ensureLibraryLoaded()) {
            Timber.w("DvdNative: dvdClose() - DVD JNI library not available - cannot close DVD handle")
            return
        }
        vLog { "DvdNative: dvdClose() - Library loaded, calling native dvdCloseNative()" }
        dvdCloseNative(handle)
        vLog { "DvdNative: dvdClose() - Native dvdCloseNative() completed" }
    }
    
    private external fun dvdCloseNative(handle: Long)
    
    /**
     * Gets the number of titles on the DVD.
     */
    fun dvdGetTitleCount(handle: Long): Int {
        vLog { "DvdNative: dvdGetTitleCount() called - handle: $handle" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdGetTitleCount() - DVD JNI library not available - cannot get title count")
            return -1
        }
        vLog { "DvdNative: dvdGetTitleCount() - Library loaded, calling native dvdGetTitleCountNative()" }
        val count = dvdGetTitleCountNative(handle)
        vLog { "DvdNative: dvdGetTitleCount() - Native dvdGetTitleCountNative() returned: $count" }
        return count
    }
    
    private external fun dvdGetTitleCountNative(handle: Long): Int
    
    /**
     * Reads title information.
     */
    fun dvdReadTitle(handle: Long, titleNumber: Int): DvdTitleNative? {
        vLog { "DvdNative: dvdReadTitle() called - handle: $handle, titleNumber: $titleNumber" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdReadTitle() - DVD JNI library not available - cannot read title")
            return null
        }
        vLog { "DvdNative: dvdReadTitle() - Library loaded, calling native dvdReadTitleNative()" }
        val title = dvdReadTitleNative(handle, titleNumber)
        vLog { "DvdNative: dvdReadTitle() - Native dvdReadTitleNative() returned: ${title != null}" }
        return title
    }
    
    private external fun dvdReadTitleNative(handle: Long, titleNumber: Int): DvdTitleNative?
    
    /**
     * Reads chapter information.
     */
    fun dvdReadChapter(handle: Long, titleNumber: Int, chapterNumber: Int): DvdChapterNative? {
        Timber.d("DvdNative: dvdReadChapter() called - handle: $handle, titleNumber: $titleNumber, chapterNumber: $chapterNumber")
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot read chapter")
            return null
        }
        return dvdReadChapterNative(handle, titleNumber, chapterNumber)
    }
    
    private external fun dvdReadChapterNative(handle: Long, titleNumber: Int, chapterNumber: Int): DvdChapterNative?
    
    /**
     * Gets VOB offsets for a title.
     * Returns a list of cell offsets (firstSector, lastSector pairs) and VTS number.
     * 
     * @param handle The DVD handle
     * @param titleNumber Title number (1-based)
     * @return Pair of (VTS number, List of VobOffsetInfo), or null on error
     */
    fun dvdGetVobOffsets(handle: Long, titleNumber: Int): Pair<Int, List<VobOffsetInfo>>? {
        vLog { "DvdNative: dvdGetVobOffsets() called - handle: $handle, titleNumber: $titleNumber" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdGetVobOffsets() - DVD JNI library not available")
            return null
        }
        
        val offsetsArray = dvdGetVobOffsetsNative(handle, titleNumber)
        if (offsetsArray == null || offsetsArray.isEmpty()) {
            Timber.e("DvdNative: dvdGetVobOffsets() - Native function returned null or empty")
            return null
        }
        
        // Array format: [vtsN, firstSector, lastSector, firstSector, lastSector, ...]
        // First element is VTS number, rest are offset pairs
        if (offsetsArray.size < 3) {
            Timber.e("DvdNative: dvdGetVobOffsets() - Array too small: ${offsetsArray.size}")
            return null
        }
        
        // Convert array to List<VobOffsetInfo> (skip first element which is VTS number)
        val offsets = mutableListOf<VobOffsetInfo>()
        for (i in 1 until offsetsArray.size step 2) {
            if (i + 1 < offsetsArray.size) {
                offsets.add(VobOffsetInfo(offsetsArray[i], offsetsArray[i + 1]))
            }
        }
        
        val vtsN = offsetsArray[0].toInt()
        vLog { "DvdNative: dvdGetVobOffsets() - Parsed ${offsets.size} cell offsets, VTS: $vtsN" }
        return Pair(vtsN, offsets)
    }
    
    private external fun dvdGetVobOffsetsNative(handle: Long, titleNumber: Int): LongArray?
    
    /**
     * Opens a VOB file for reading.
     * 
     * @param handle The DVD handle
     * @param vtsN VTS number (Video Title Set number)
     * @return VOB handle, or -1 on error
     */
    fun dvdOpenVobFile(handle: Long, vtsN: Int): Long {
        vLog { "DvdNative: dvdOpenVobFile() called - handle: $handle, vtsN: $vtsN" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdOpenVobFile() - DVD JNI library not available")
            return -1
        }
        
        val vobHandle = dvdOpenVobFileNative(handle, vtsN)
        vLog { "DvdNative: dvdOpenVobFile() - Native function returned: $vobHandle" }
        return vobHandle
    }
    
    private external fun dvdOpenVobFileNative(handle: Long, vtsN: Int): Long
    
    /**
     * Reads VOB blocks.
     * 
     * @param vobHandle The VOB handle
     * @param block Block number (relative to VOB start)
     * @param count Number of blocks to read
     * @param buffer Buffer to read into
     * @return Number of bytes read, or -1 on error, 0 on EOF
     */
    fun dvdReadVobBlocks(vobHandle: Long, block: Int, count: Int, buffer: ByteArray): Int {
        vLog { "DvdNative: dvdReadVobBlocks() called - vobHandle: $vobHandle, block: $block, count: $count" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdReadVobBlocks() - DVD JNI library not available")
            return -1
        }
        
        val bytesRead = dvdReadVobBlocksNative(vobHandle, block, count, buffer)
        vLog { "DvdNative: dvdReadVobBlocks() - Native function returned: $bytesRead bytes" }
        return bytesRead
    }
    
    private external fun dvdReadVobBlocksNative(vobHandle: Long, block: Int, count: Int, buffer: ByteArray): Int
    
    /**
     * Closes a VOB file.
     * 
     * @param vobHandle The VOB handle to close
     */
    fun dvdCloseVobFile(vobHandle: Long) {
        vLog { "DvdNative: dvdCloseVobFile() called - vobHandle: $vobHandle" }
        if (!ensureLibraryLoaded()) {
            Timber.w("DvdNative: dvdCloseVobFile() - DVD JNI library not available")
            return
        }
        
        if (vobHandle <= 0) {
            vLog { "DvdNative: dvdCloseVobFile() - Invalid handle (<= 0), skipping" }
            return
        }
        
        dvdCloseVobFileNative(vobHandle)
        vLog { "DvdNative: dvdCloseVobFile() - Native function completed" }
    }
    
    private external fun dvdCloseVobFileNative(vobHandle: Long)

    /**
     * Gets audio tracks for a given title.
     */
    fun dvdGetAudioTracks(handle: Long, titleNumber: Int): List<DvdAudioTrackNative> {
        vLog { "DvdNative: dvdGetAudioTracks() called - handle: $handle, titleNumber: $titleNumber" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdGetAudioTracks() - DVD JNI library not available")
            return emptyList()
        }
        val tracks = dvdGetAudioTracksNative(handle, titleNumber) ?: return emptyList()
        vLog { "DvdNative: dvdGetAudioTracks() - Received ${tracks.size} audio tracks" }
        return tracks.toList()
    }

    private external fun dvdGetAudioTracksNative(handle: Long, titleNumber: Int): Array<DvdAudioTrackNative>?

    /**
     * Gets subtitle tracks for a given title.
     */
    fun dvdGetSubtitleTracks(handle: Long, titleNumber: Int): List<DvdSubtitleTrackNative> {
        vLog { "DvdNative: dvdGetSubtitleTracks() called - handle: $handle, titleNumber: $titleNumber" }
        if (!ensureLibraryLoaded()) {
            Timber.e("DvdNative: dvdGetSubtitleTracks() - DVD JNI library not available")
            return emptyList()
        }
        val tracks = dvdGetSubtitleTracksNative(handle, titleNumber) ?: return emptyList()
        vLog { "DvdNative: dvdGetSubtitleTracks() - Received ${tracks.size} subtitle tracks" }
        return tracks.toList()
    }

    private external fun dvdGetSubtitleTracksNative(handle: Long, titleNumber: Int): Array<DvdSubtitleTrackNative>?

    /**
     * Gets the DVD name/title from the VMG.
     * 
     * Reads the DVD name from the Video Manager Information (VMG).
     * May return null if the name is not available or not set.
     * 
     * @param handle DVD handle (must be > 0)
     * @return DVD name/title, or null if not available
     */
    fun dvdGetName(handle: Long): String? {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot get DVD name")
            return null
        }
        return dvdGetNameNative(handle)
    }
    
    private external fun dvdGetNameNative(handle: Long): String?

}

/**
 * Safe wrapper for DVD native handles that implements [AutoCloseable].
 * 
 * This wrapper ensures that [DvdNative.dvdClose] is always called, even on errors.
 * Use the [use] function to automatically close the handle:
 * 
 * ```
 * DvdHandle.open(path)?.use { handle ->
 *     val titleCount = DvdNative.dvdGetTitleCount(handle.handle)
 *     // ... other operations
 * }
 * ```
 * 
 * @property handle The native DVD handle (valid if not -1)
 * @property isValid True if the handle is valid (not -1), false otherwise
 * @throws IllegalStateException if operations are attempted after [close] is called
 */
class DvdHandle private constructor(val handle: Long) : AutoCloseable {
    
    private var isClosed = false
    
    /**
     * True if the handle is valid (not -1), false otherwise.
     */
    val isValid: Boolean
        get() = handle != -1L && !isClosed
    
    /**
     * Opens a DVD and returns a safe handle wrapper.
     * 
     * @param path Mount point or path to the DVD device
     * @return A [DvdHandle] if successful, null if dvdOpen returned -1
     */
    companion object {
        fun open(path: String): DvdHandle? {
            val handle = DvdNative.dvdOpen(path)
            return if (handle == -1L) {
                Timber.e("Failed to open DVD at path: $path")
                null
            } else {
                DvdHandle(handle)
            }
        }
    }
    
    /**
     * Closes the DVD handle.
     * 
     * **Behavior:**
     * - This method is idempotent: calling it multiple times is safe
     * - Handles <= 0 are treated as invalid and [dvdClose] silently returns (no-op)
     * - After calling this, [isValid] will return false and operations will fail
     * 
     * **Thread Safety:** This class is not thread-safe. Do not invoke any methods
     * concurrently from multiple threads without external synchronization.
     */
    override fun close() {
        if (!isClosed && handle != -1L) {
            try {
                DvdNative.dvdClose(handle)
                Timber.d("DVD handle closed: $handle")
            } catch (e: Exception) {
                Timber.e(e, "Error closing DVD handle: $handle")
                throw e
            } finally {
                // Set isClosed in finally block to ensure idempotency
                // This prevents dangerous retries even if dvdClose throws
                isClosed = true
            }
        }
    }

}

/**
 * Native DVD title information structure.
 */
data class DvdTitleNative(
    val number: Int,
    val chapterCount: Int,
    val duration: Long // milliseconds
)

data class DvdAudioTrackNative(
    val streamId: Int,
    val language: String?,
    val codec: String,
    val channels: Int,
    val sampleRate: Int
)

data class DvdSubtitleTrackNative(
    val streamId: Int,
    val language: String?,
    val type: String
)

/**
 * Native DVD chapter information structure.
 */
data class DvdChapterNative(
    val number: Int,
    val startTime: Long, // milliseconds
    val duration: Long // milliseconds
)

/**
 * Video stream information from DVD.
 */
data class VideoStreamNative(
    val codec: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Double
)

/**
 * VOB offset information for a single cell.
 * 
 * @property firstSector First sector number (relative to VOB start)
 * @property lastSector Last sector number (relative to VOB start)
 */
data class VobOffsetInfo(
    val firstSector: Long,
    val lastSector: Long
)
