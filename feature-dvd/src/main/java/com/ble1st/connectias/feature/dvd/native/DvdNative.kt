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
        if (libraryLoaded) {
            return libraryAvailable
        }
        
        synchronized(libraryLoadLock) {
            if (libraryLoaded) {
                return libraryAvailable
            }
            
            try {
                Timber.d("Loading DVD JNI native library...")
                System.loadLibrary("dvd_jni")
                libraryAvailable = true
                // dvdread/dvdnav are statically linked into dvd_jni
                dvdReadAvailable = true 
                Timber.d("DVD JNI native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                libraryAvailable = false
                dvdReadAvailable = false
                Timber.e(e, "Failed to load DVD JNI native library - DVD functionality will be unavailable")
                libraryLoaded = true
                return false
            }
            
            // CSS decryption is now statically linked into libdvdread/libdvd_jni
            // No need to load libdvdcss separately - it's embedded in the native library
            cssDecryptionEnabled = BuildConfig.ENABLE_DVD_CSS
            if (cssDecryptionEnabled) {
                Timber.i("CSS decryption enabled (statically linked via libdvdcss)")
            } else {
                Timber.d("CSS decryption disabled via build config")
            }
            
            libraryLoaded = true
            return true
        }
    }
    
    /**
     * Checks if the native JNI library is available.
     * 
     * @return true if dvd_jni is loaded and available, false otherwise
     */
    fun isLibraryAvailable(): Boolean {
        ensureLibraryLoaded()
        return libraryAvailable
    }
    
    /**
     * Checks if libdvdread is available for full DVD functionality.
     * 
     * @return true if libdvdread is loaded and available, false otherwise
     */
    fun isDvdReadAvailable(): Boolean {
        ensureLibraryLoaded()
        return dvdReadAvailable
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
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot open DVD")
            return -1L
        }
        return dvdOpenNative(path)
    }
    
    private external fun dvdOpenNative(path: String): Long

    /**
     * Opens a DVD using a file descriptor.
     * 
     * This is the preferred method on Android, as it allows using the UsbDeviceConnection
     * file descriptor, bypassing permission issues with direct device path access.
     * 
     * @param fd The file descriptor (from UsbDeviceConnection.fileDescriptor)
     * @return A valid handle (!= -1L) on success, or -1L on failure
     */
    fun dvdOpenFd(fd: Int): Long {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot open DVD via FD")
            return -1L
        }
        return dvdOpenFdNative(fd)
    }

    private external fun dvdOpenFdNative(fd: Int): Long

    /**
     * Opens a DVD using a custom UsbBlockDevice stream.
     *
     * @param driver The UsbBlockDevice instance (e.g. ScsiDriver)
     * @return A valid handle (!= -1L) on success, or -1L on failure
     */
    fun dvdOpenStream(driver: UsbBlockDevice): Long {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot open DVD via Stream")
            return -1L
        }
        return dvdOpenStreamNative(driver)
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
        if (!ensureLibraryLoaded()) {
            Timber.w("DVD JNI library not available - cannot close DVD handle")
            return
        }
        dvdCloseNative(handle)
    }
    
    private external fun dvdCloseNative(handle: Long)
    
    /**
     * Gets the number of titles on the DVD.
     */
    fun dvdGetTitleCount(handle: Long): Int {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot get title count")
            return -1
        }
        return dvdGetTitleCountNative(handle)
    }
    
    private external fun dvdGetTitleCountNative(handle: Long): Int
    
    /**
     * Reads title information.
     */
    fun dvdReadTitle(handle: Long, titleNumber: Int): DvdTitleNative? {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot read title")
            return null
        }
        return dvdReadTitleNative(handle, titleNumber)
    }
    
    private external fun dvdReadTitleNative(handle: Long, titleNumber: Int): DvdTitleNative?
    
    /**
     * Reads chapter information.
     */
    fun dvdReadChapter(handle: Long, titleNumber: Int, chapterNumber: Int): DvdChapterNative? {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot read chapter")
            return null
        }
        return dvdReadChapterNative(handle, titleNumber, chapterNumber)
    }
    
    private external fun dvdReadChapterNative(handle: Long, titleNumber: Int, chapterNumber: Int): DvdChapterNative?
    
    /**
     * Decrypts CSS-protected sector (only available if libdvdcss is loaded).
     */
    fun dvdDecryptCss(handle: Long, titleNumber: Int): ByteArray? {
        if (!cssDecryptionEnabled) {
            Timber.w("CSS decryption requested but library not available")
            return null
        }
        return dvdDecryptCssNative(handle, titleNumber)
    }
    
    @Suppress("unused")
    private external fun dvdDecryptCssNative(handle: Long, titleNumber: Int): ByteArray?
    
    /**
     * Extracts video stream from DVD title/chapter.
     */
    fun dvdExtractVideoStream(handle: Long, titleNumber: Int, chapterNumber: Int): VideoStreamNative? {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot extract video stream")
            return null
        }
        return dvdExtractVideoStreamNative(handle, titleNumber, chapterNumber)
    }
    
    private external fun dvdExtractVideoStreamNative(handle: Long, titleNumber: Int, chapterNumber: Int): VideoStreamNative?
    
    /**
     * Checks if CSS decryption is available.
     */
    fun isCssDecryptionAvailable(): Boolean {
        ensureLibraryLoaded()
        return cssDecryptionEnabled
    }
    
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
    
    /**
     * Ejects an optical drive device.
     * 
     * Attempts to eject the device using ioctl CDROMEJECT command.
     * This requires appropriate permissions (typically root or special device access).
     * 
     * @param devicePath Device path (e.g., /dev/sg0, /dev/sr0)
     * @return true if eject command was sent successfully, false otherwise
     */
    fun ejectDevice(devicePath: String): Boolean {
        if (!ensureLibraryLoaded()) {
            Timber.e("DVD JNI library not available - cannot eject device")
            return false
        }
        return ejectDeviceNative(devicePath)
    }
    
    private external fun ejectDeviceNative(devicePath: String): Boolean
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
    
    /**
     * Throws [IllegalStateException] if the handle is closed or invalid.
     */
    private fun checkValid() {
        if (isClosed) {
            throw IllegalStateException("DVD handle has been closed")
        }
        if (handle == -1L) {
            throw IllegalStateException("DVD handle is invalid (-1)")
        }
    }
    
    /**
     * Gets the number of titles on the DVD.
     * @throws IllegalStateException if handle is closed or invalid
     */
    fun getTitleCount(): Int {
        checkValid()
        return DvdNative.dvdGetTitleCount(handle)
    }
    
    /**
     * Reads title information.
     * @throws IllegalStateException if handle is closed or invalid
     */
    fun readTitle(titleNumber: Int): DvdTitleNative? {
        checkValid()
        return DvdNative.dvdReadTitle(handle, titleNumber)
    }
    
    /**
     * Reads chapter information.
     * @throws IllegalStateException if handle is closed or invalid
     */
    fun readChapter(titleNumber: Int, chapterNumber: Int): DvdChapterNative? {
        checkValid()
        return DvdNative.dvdReadChapter(handle, titleNumber, chapterNumber)
    }
    
    /**
     * Decrypts CSS-protected sector (only available if libdvdcss is loaded).
     * @throws IllegalStateException if handle is closed or invalid
     */
    fun decryptCss(titleNumber: Int): ByteArray? {
        checkValid()
        return DvdNative.dvdDecryptCss(handle, titleNumber)
    }
    
    /**
     * Extracts video stream from DVD title/chapter.
     * @throws IllegalStateException if handle is closed or invalid
     */
    fun extractVideoStream(titleNumber: Int, chapterNumber: Int): VideoStreamNative? {
        checkValid()
        return DvdNative.dvdExtractVideoStream(handle, titleNumber, chapterNumber)
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
