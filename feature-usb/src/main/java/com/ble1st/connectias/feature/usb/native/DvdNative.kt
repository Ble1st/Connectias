package com.ble1st.connectias.feature.usb.native

import com.ble1st.connectias.feature.usb.BuildConfig
import timber.log.Timber

/**
 * Native interface for DVD operations via libdvdread/libdvdnav/libdvdcss.
 */
object DvdNative {
    
    private val cssDecryptionEnabled: Boolean
    
    init {
        val cssEnabled = if (BuildConfig.ENABLE_DVD_CSS) {
            try {
                Timber.d("Loading libdvdcss (CSS decryption)...")
                System.loadLibrary("dvdcss")
                Timber.d("libdvdcss loaded successfully")
                Timber.i("CSS decryption enabled")
                true
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "libdvdcss not available (CSS decryption disabled)")
                false
            }
        } else {
            Timber.d("libdvdcss not loaded (CSS decryption disabled via build config)")
            false
        }
        
        cssDecryptionEnabled = cssEnabled
        
        try {
            Timber.d("Loading DVD native libraries...")
            System.loadLibrary("dvdread")
            Timber.d("libdvdread loaded successfully")
            System.loadLibrary("dvdnav")
            Timber.d("libdvdnav loaded successfully")
            System.loadLibrary("ffmpeg")
            Timber.d("FFmpeg loaded successfully")
            Timber.i("DVD native libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load DVD native libraries")
            throw IllegalStateException("DVD native libraries not available", e)
        }
    }
    
    /**
     * Opens a DVD at the specified path.
     * 
     * @param path Mount point or path to the DVD device
     * @return A valid handle (>= 0) on success, or -1L on failure
     * 
     * **Lifecycle Requirements:**
     * - Callers must call [dvdClose] for every valid handle returned by [dvdOpen] (i.e., when the return value is not -1L)
     * - Do NOT call [dvdClose] if [dvdOpen] returns -1L (invalid handle)
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
    external fun dvdOpen(path: String): Long
    
    /**
     * Closes a DVD handle.
     * 
     * **Important:** This must be called for every valid handle returned by [dvdOpen] (i.e., when [dvdOpen] returned a value != -1L).
     * Do NOT call this method if [dvdOpen] returned -1L.
     * 
     * This method is safe to call with invalid handles (<= 0) - it will silently return without error.
     * However, calling it multiple times with the same handle may cause undefined behavior.
     * 
     * @param handle The handle to close (should be a valid handle returned by [dvdOpen], or <= 0 for no-op)
     */
    external fun dvdClose(handle: Long)
    
    /**
     * Gets the number of titles on the DVD.
     */
    external fun dvdGetTitleCount(handle: Long): Int
    
    /**
     * Reads title information.
     */
    external fun dvdReadTitle(handle: Long, titleNumber: Int): DvdTitleNative?
    
    /**
     * Reads chapter information.
     */
    external fun dvdReadChapter(handle: Long, titleNumber: Int, chapterNumber: Int): DvdChapterNative?
    
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
    external fun dvdExtractVideoStream(handle: Long, titleNumber: Int, chapterNumber: Int): VideoStreamNative?
    
    /**
     * Checks if CSS decryption is available.
     */
    fun isCssDecryptionAvailable(): Boolean = cssDecryptionEnabled
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
     * This method is idempotent - calling it multiple times is safe.
     * After calling this, [isValid] will return false and operations will fail.
     * 
     * **Thread Safety:** This class is not thread-safe. Do not call [close] concurrently
     * from multiple threads without external synchronization.
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
data class VideoStreamNative(
    val codec: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Double
)
