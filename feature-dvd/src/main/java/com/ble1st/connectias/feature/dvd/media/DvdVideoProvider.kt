package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.dvd.driver.scsi.ScsiDriver
import com.ble1st.connectias.feature.dvd.models.DvdInfo
import com.ble1st.connectias.feature.dvd.models.DvdTitle
import com.ble1st.connectias.feature.dvd.models.OpticalDrive
import com.ble1st.connectias.feature.dvd.models.VideoStream
import com.ble1st.connectias.feature.dvd.native.DvdNative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

import com.ble1st.connectias.feature.dvd.storage.OpticalDriveProvider

/**
 * Provider for Video DVD operations using Direct USB/SCSI access.
 */
@Singleton
class DvdVideoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opticalDriveProvider: OpticalDriveProvider
) {
    
    /**
     * Opens a Video DVD, reads its structure (Titles/Chapters), and closes it.
     * Returns DvdInfo containing the metadata.
     */
    suspend fun openDvd(drive: OpticalDrive): DvdInfo = withContext(Dispatchers.IO) {
        var handle: Long = -1
        
        // Acquire exclusive session via provider (locks mutex)
        val scsiDriver = opticalDriveProvider.openSession(drive) 
            ?: throw IllegalStateException("Failed to open driver session or device busy")
        
        try {
            Timber.d("Opening DVD: ${drive.device.product}")
            
            // Wait for drive to be ready before attempting DVD operations
            if (!scsiDriver.waitForReady(maxAttempts = 15, delayMs = 500)) {
                Timber.w("Drive not ready or no medium present")
                throw IllegalStateException("Drive not ready or no medium present")
            }
            
            // Ensure Native Lib
            if (!DvdNative.ensureLibraryLoaded()) throw IllegalStateException("Native library not loaded")
            
            // Open Stream
            handle = DvdNative.dvdOpenStream(scsiDriver)
            if (handle <= 0) throw IllegalStateException("Failed to open DVD stream")
            
            Timber.i("DVD opened successfully via SCSI, handle: $handle")
            
            // Read Structure - Only load title metadata (no chapters for performance)
            val titleCount = DvdNative.dvdGetTitleCount(handle)
            Timber.i("DVD contains $titleCount titles")
            
            val titles = mutableListOf<DvdTitle>()
            for (titleNumber in 1..titleCount) {
                val titleNative = DvdNative.dvdReadTitle(handle, titleNumber)
                if (titleNative != null) {
                    // Only store basic title info - chapters are loaded lazily when needed
                    titles.add(DvdTitle(
                        number = titleNative.number,
                        duration = titleNative.duration,
                        chapterCount = titleNative.chapterCount
                        // chapters will be empty (default) - loaded on demand
                    ))
                }
            }
            
            Timber.i("Loaded ${titles.size} title metadata entries (chapters deferred)")
            
            // Try to read DVD name
            val dvdName = try {
                DvdNative.dvdGetName(handle)?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Timber.w(e, "Failed to read DVD name")
                null
            }
            
            if (dvdName != null) {
                Timber.i("DVD name: $dvdName")
            } else {
                Timber.d("DVD name not available")
            }
            
            return@withContext DvdInfo(
                handle = -1, // Handle is invalid after return
                mountPoint = "", // Not used
                deviceId = drive.device.deviceId, // Use device ID from drive object
                titles = titles,
                name = dvdName
            )
            
        } finally {
            // Always close native handle and release session
            if (handle > 0) DvdNative.dvdClose(handle)
            opticalDriveProvider.closeSession()
        }
    }
    
    /**
     * Generates a Playback URI for a DVD title.
     */
    suspend fun playTitle(dvdInfo: DvdInfo, titleNumber: Int): VideoStream = withContext(Dispatchers.IO) {
        Timber.d("Preparing playback for Title $titleNumber (Device ID: ${dvdInfo.deviceId})")
        
        val title = dvdInfo.titles.find { it.number == titleNumber }
            ?: throw IllegalArgumentException("Title not found")
            
        // Note: We cannot easily probe codec/resolution without opening the DVD again.
        // But creating VideoStream requires these fields?
        // If VideoStream requires them, we might need to keep the DVD open or re-open briefly.
        // For now, we'll use dummy values or maybe we can cache them during openDvd if DvdTitle had them.
        // DvdTitle (Native) usually has aspect ratio / format info.
        
        // Let's assume standard DVD resolution for now to avoid re-opening overhead just for metadata
        // or update DvdTitle to include video attributes in a future refactor.
        
        val uri = "content://com.ble1st.connectias.provider/dvd/${dvdInfo.deviceId}/$titleNumber"
        
        VideoStream(
            codec = "mpeg2", // Standard DVD
            width = 720, // PAL/NTSC typical
            height = 480, 
            bitrate = 5000000L,
            frameRate = 30.0,
            uri = uri
        )
    }
    
    suspend fun closeDvd(dvdInfo: DvdInfo) {
        // No-op since we close in openDvd
    }
}