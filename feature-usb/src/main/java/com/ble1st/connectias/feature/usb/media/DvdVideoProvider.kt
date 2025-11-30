package com.ble1st.connectias.feature.usb.media

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
    private val dvdSettings: DvdSettings
) {
    
    /**
     * Opens a Video DVD.
     */
    suspend fun openDvd(drive: OpticalDrive): DvdInfo = withContext(Dispatchers.IO) {
        try {
            Timber.d("Opening DVD at mount point: ${drive.mountPoint}")
            
            val handle = DvdNative.dvdOpen(drive.mountPoint)
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
                        }
                    }
                    
                    val title = DvdTitle(
                        number = titleNative.number,
                        chapterCount = titleNative.chapterCount,
                        duration = titleNative.duration,
                        chapters = chapters
                    )
                    titles.add(title)
                    Timber.d("Title $titleNumber: ${title.chapterCount} chapters, duration=${title.duration}ms")
                }
            }
            
            Timber.i("Successfully opened DVD with ${titles.size} titles")
            
            DvdInfo(
                handle = handle,
                mountPoint = drive.mountPoint,
                titles = titles
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to open DVD at ${drive.mountPoint}")
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
        
        VideoStream(
            codec = videoStreamNative.codec,
            width = videoStreamNative.width,
            height = videoStreamNative.height,
            bitrate = videoStreamNative.bitrate,
            frameRate = videoStreamNative.frameRate,
            uri = null // TODO: Generate URI for video stream
        )
    }
    
    /**
     * Closes a DVD.
     */
    suspend fun closeDvd(dvdInfo: DvdInfo) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Closing DVD, handle: ${dvdInfo.handle}")
            DvdNative.dvdClose(dvdInfo.handle)
            Timber.d("DVD closed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error closing DVD")
        }
    }
}
