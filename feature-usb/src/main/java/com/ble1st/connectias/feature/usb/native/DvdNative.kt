package com.ble1st.connectias.feature.usb.native

import com.ble1st.connectias.feature.usb.BuildConfig
import timber.log.Timber

/**
 * Native interface for DVD operations via libdvdread/libdvdnav/libdvdcss.
 */
object DvdNative {
    
    private var cssDecryptionEnabled = false
    
    init {
        try {
            Timber.d("Loading DVD native libraries...")
            System.loadLibrary("dvdread")
            Timber.d("libdvdread loaded successfully")
            System.loadLibrary("dvdnav")
            Timber.d("libdvdnav loaded successfully")
            
            if (BuildConfig.ENABLE_DVD_CSS) {
                try {
                    Timber.d("Loading libdvdcss (CSS decryption)...")
                    System.loadLibrary("dvdcss")
                    Timber.d("libdvdcss loaded successfully")
                    cssDecryptionEnabled = true
                    Timber.i("CSS decryption enabled")
                } catch (e: UnsatisfiedLinkError) {
                    Timber.w(e, "libdvdcss not available (CSS decryption disabled)")
                    cssDecryptionEnabled = false
                }
            } else {
                Timber.d("libdvdcss not loaded (CSS decryption disabled via build config)")
            }
            
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
     * @return Handle to the opened DVD, or -1 on error
     */
    external fun dvdOpen(path: String): Long
    
    /**
     * Closes a DVD.
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
    @Suppress("UNUSED_PARAMETER")
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
 * Native DVD title structure.
 */
data class DvdTitleNative(
    val number: Int,
    val chapterCount: Int,
    val duration: Long // milliseconds
)

/**
 * Native DVD chapter structure.
 */
data class DvdChapterNative(
    val number: Int,
    val startTime: Long, // milliseconds
    val duration: Long // milliseconds
)

/**
 * Native video stream structure.
 */
data class VideoStreamNative(
    val codec: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Double
)
