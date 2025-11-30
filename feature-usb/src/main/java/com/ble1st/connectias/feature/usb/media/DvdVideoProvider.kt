package com.ble1st.connectias.feature.usb.media

import android.net.Uri
import com.ble1st.connectias.feature.usb.models.DvdChapter
import com.ble1st.connectias.feature.usb.models.DvdInfo
import com.ble1st.connectias.feature.usb.models.DvdTitle
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import com.ble1st.connectias.feature.usb.models.VideoStream
import com.ble1st.connectias.feature.usb.native.DvdNative
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import com.ble1st.connectias.feature.usb.storage.OpticalDriveProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for Video DVD operations.
 */
@Singleton
class DvdVideoProvider @Inject constructor(
    private val opticalDriveProvider: OpticalDriveProvider,
    private val dvdSettings: DvdSettings,
    private val dvdHandleRegistry: DvdHandleRegistry
) {
    
    /**
     * Opens a Video DVD.
     */
    suspend fun openDvd(drive: OpticalDrive): DvdInfo = withContext(Dispatchers.IO) {
        var handle: Long? = null
        try {
            Timber.d("Opening DVD at mount point: ${drive.mountPoint}")
            
            handle = DvdNative.dvdOpen(drive.mountPoint)
            Timber.d("DVD opened successfully, handle: $handle")
            
            val titleCount = DvdNative.dvdGetTitleCount(handle)
            Timber.i("DVD contains $titleCount titles")
            
            val titles = mutableListOf<DvdTitle>()
            for (titleNumber in 1..titleCount) {
                Timber.d("Reading title $titleNumber...")
                val titleNative = DvdNative.dvdReadTitle(handle, titleNumber)
                if (titleNative != null) {
                    // Read chapters for this title
                    val chapters = mutableListOf<DvdChapter>()
                    for (chapterNumber in 1..titleNative.chapterCount) {
                        val chapterNative = DvdNative.dvdReadChapter(handle, titleNumber, chapterNumber)
                        if (chapterNative != null) {
                            chapters.add(
                                DvdChapter(
                                    number = chapterNative.number,
                                    titleNumber = titleNumber,
                                    startTime = chapterNative.startTime,
                                    duration = chapterNative.duration
                                )
                            )
                        } else {
                            Timber.w("Failed to read chapter $chapterNumber for title $titleNumber (handle: $handle)")
                        }
                    }
                    
                    val title = DvdTitle(
                        number = titleNative.number,
                        duration = titleNative.duration,
                        chapters = chapters.toList() // Create immutable list
                    )
                    titles.add(title)
                    Timber.d("Title $titleNumber: ${title.chapterCount} chapters, duration=${title.duration}ms")
                } else {
                    Timber.w("Failed to read title $titleNumber (handle: $handle)")
                }
            }
            
            Timber.i("Successfully opened DVD with ${titles.size} titles")
            
            val dvdInfo = DvdInfo(
                handle = handle,
                mountPoint = drive.mountPoint,
                titles = titles
            )
            
            // Register DVD handle in registry for ContentProvider access
            dvdHandleRegistry.registerDvd(dvdInfo)
            Timber.d("DVD registered in handle registry")
            
            dvdInfo
        } catch (e: Exception) {
            Timber.e(e, "Failed to open DVD at ${drive.mountPoint}")
            handle?.let { 
                try {
                    DvdNative.dvdClose(it)
                } catch (closeException: Exception) {
                    Timber.e(closeException, "Failed to close DVD handle during error cleanup")
                }
            }
            throw e
        }
    }
    
    /**
     * Plays a DVD title.
     */
    suspend fun playTitle(dvdInfo: DvdInfo, titleNumber: Int): VideoStream = withContext(Dispatchers.IO) {
        Timber.d("Starting playback of title $titleNumber")
        
        val title = dvdInfo.titles.find { it.number == titleNumber }
            ?: run {
                Timber.e("Title $titleNumber not found in DVD")
                throw IllegalArgumentException("Title not found")
            }
        
        Timber.d("Title found: ${title.chapters.size} chapters")
        
        // CSS-Decryption falls aktiviert
        performCssDecryption(dvdInfo, titleNumber)
        
        // Validate chapters before accessing
        if (title.chapters.isEmpty()) {
            Timber.e("Title $titleNumber has no chapters - cannot play")
            throw IllegalStateException("Title has no chapters")
        }
        
        // FFmpeg für Video-Decodierung verwenden
        Timber.d("Extracting video stream with FFmpeg...")
        val videoStreamNative = DvdNative.dvdExtractVideoStream(
            dvdInfo.handle,
            titleNumber,
            title.chapters.first().number
        )
        
        if (videoStreamNative == null) {
            Timber.e("Failed to extract video stream")
            throw IllegalStateException("Video stream extraction failed")
        }
        
        Timber.i("Video stream extracted successfully: ${videoStreamNative.codec}, ${videoStreamNative.width}x${videoStreamNative.height}")
        
        // Generate URI for video playback
        // Using content:// URI scheme for local file access
        val uri = generateVideoUri(dvdInfo, titleNumber, title.chapters.first().number)
        
        VideoStream(
            codec = videoStreamNative.codec,
            width = videoStreamNative.width,
            height = videoStreamNative.height,
            bitrate = videoStreamNative.bitrate,
            frameRate = videoStreamNative.frameRate,
            uri = uri
        )
    }
    
    /**
     * Performs CSS decryption for a DVD title if enabled.
     */
    private suspend fun performCssDecryption(dvdInfo: DvdInfo, titleNumber: Int) {
        val cssEnabled = dvdSettings.isCssDecryptionEnabled()
        Timber.d("CSS decryption enabled: $cssEnabled")
        
        if (cssEnabled && DvdNative.isCssDecryptionAvailable()) {
            Timber.i("CSS protection detected, starting decryption...")
            try {
                val decrypted = DvdNative.dvdDecryptCss(dvdInfo.handle, titleNumber)
                if (decrypted != null) {
                    Timber.i("CSS decryption successful")
                } else {
                    Timber.w("CSS decryption returned null - may not be protected")
                }
            } catch (e: Exception) {
                Timber.w(e, "CSS decryption failed, attempting playback anyway")
            }
        } else if (!cssEnabled) {
            Timber.d("CSS decryption disabled - skipping decryption")
        } else {
            Timber.w("CSS decryption requested but library not available")
        }
    }
    
    /**
     * Generates a URI for video playback.
     * Creates a content:// URI pointing to the DVD video stream.
     */
    private fun generateVideoUri(dvdInfo: DvdInfo, titleNumber: Int, chapterNumber: Int): String {
        // Generate URI using content:// scheme for local file access
        // Format: content://com.ble1st.connectias.dvd/video/{mountPoint}/{titleNumber}/{chapterNumber}
        // This URI will be handled by a ContentProvider that streams the DVD video data
        val uri = DvdVideoContentProvider.buildUri(dvdInfo.mountPoint, titleNumber, chapterNumber)
        return uri.toString()
    }
    
    /**
     * Closes a DVD.
     */
    suspend fun closeDvd(dvdInfo: DvdInfo) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Closing DVD, handle: ${dvdInfo.handle}")
            
            // Unregister DVD handle from registry before closing
            dvdHandleRegistry.unregisterDvd(dvdInfo)
            Timber.d("DVD unregistered from handle registry")
            
            DvdNative.dvdClose(dvdInfo.handle)
            Timber.d("DVD closed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error closing DVD")
        }
    }
}
