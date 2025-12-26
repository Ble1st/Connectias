package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.dvd.models.DvdInfo
import com.ble1st.connectias.feature.dvd.models.DvdTitle
import com.ble1st.connectias.feature.dvd.models.DvdAudioTrack
import com.ble1st.connectias.feature.dvd.models.DvdSubtitleTrack
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
    private companion object {
        private const val LOG_VERBOSE = false
    }
    private inline fun vLog(msg: () -> String) { if (LOG_VERBOSE) Timber.d(msg()) }
    
    /**
     * Opens a Video DVD, reads its structure (Titles/Chapters), and closes it.
     * Returns DvdInfo containing the metadata.
     */
    suspend fun openDvd(drive: OpticalDrive): DvdInfo = withContext(Dispatchers.IO) {
        Timber.d("DvdVideoProvider: openDvd() called")
        Timber.d("DvdVideoProvider: openDvd() - drive: ${drive.device.product}, vendorId: ${drive.device.vendorId}, productId: ${drive.device.productId}")
        var handle: Long = -1
        
        vLog { "DvdVideoProvider: openDvd() - Acquiring exclusive session via provider" }
        // Acquire exclusive session via provider (locks mutex)
        val scsiDriver = opticalDriveProvider.openSession(drive)
        if (scsiDriver == null) {
            Timber.e("DvdVideoProvider: openDvd() - Failed to open driver session or device busy")
            throw IllegalStateException("Failed to open driver session or device busy")
        }
        vLog { "DvdVideoProvider: openDvd() - Session acquired, scsiDriver: $scsiDriver" }
        
        try {
            vLog { "DvdVideoProvider: openDvd() - Opening DVD: ${drive.device.product}" }
            
            vLog { "DvdVideoProvider: openDvd() - Waiting for drive to be ready (max 15 attempts, 500ms delay)" }
            // Wait for drive to be ready before attempting DVD operations
            val ready = scsiDriver.waitForReady(maxAttempts = 15, delayMs = 500)
            vLog { "DvdVideoProvider: openDvd() - waitForReady() returned: $ready" }
            if (!ready) {
                Timber.w("DvdVideoProvider: openDvd() - Drive not ready or no medium present")
                throw IllegalStateException("Drive not ready or no medium present")
            }
            vLog { "DvdVideoProvider: openDvd() - Drive is ready" }
            
            vLog { "DvdVideoProvider: openDvd() - Ensuring native library is loaded" }
            // Ensure Native Lib
            val libLoaded = DvdNative.ensureLibraryLoaded()
            vLog { "DvdVideoProvider: openDvd() - ensureLibraryLoaded() returned: $libLoaded" }
            if (!libLoaded) {
                Timber.e("DvdVideoProvider: openDvd() - Native library not loaded")
                throw IllegalStateException("Native library not loaded")
            }
            vLog { "DvdVideoProvider: openDvd() - Native library loaded" }
            
            vLog { "DvdVideoProvider: openDvd() - Opening DVD stream via DvdNative" }
            // Open Stream
            handle = DvdNative.dvdOpenStream(scsiDriver)
            vLog { "DvdVideoProvider: openDvd() - dvdOpenStream() returned handle: $handle" }
            if (handle <= 0) {
                Timber.e("DvdVideoProvider: openDvd() - Failed to open DVD stream, handle: $handle")
                throw IllegalStateException("Failed to open DVD stream")
            }
            
            Timber.i("DvdVideoProvider: openDvd() - DVD opened successfully via SCSI, handle: $handle")
            
            vLog { "DvdVideoProvider: openDvd() - Reading DVD structure (titles only, chapters deferred)" }
            // Read Structure - Only load title metadata (no chapters for performance)
            val titleCount = DvdNative.dvdGetTitleCount(handle)
            Timber.i("DvdVideoProvider: openDvd() - DVD contains $titleCount titles")
            
            val titles = mutableListOf<DvdTitle>()
            vLog { "DvdVideoProvider: openDvd() - Iterating through titles (1 to $titleCount)" }
            for (titleNumber in 1..titleCount) {
                vLog { "DvdVideoProvider: openDvd() - Reading title $titleNumber" }
                val titleNative = DvdNative.dvdReadTitle(handle, titleNumber)
                if (titleNative != null) {
                    vLog { "DvdVideoProvider: openDvd() - Title $titleNumber: chapters=${titleNative.chapterCount}, duration=${titleNative.duration}ms" }
                    val audioTracks = DvdNative.dvdGetAudioTracks(handle, titleNumber).map { it.toModel() }
                    val subtitleTracks = DvdNative.dvdGetSubtitleTracks(handle, titleNumber).map { it.toModel() }
                    // Only store basic title info - chapters are loaded lazily when needed
                    titles.add(DvdTitle(
                        number = titleNative.number,
                        duration = titleNative.duration,
                        chapterCount = titleNative.chapterCount,
                        audioTracks = audioTracks,
                        subtitleTracks = subtitleTracks
                        // chapters will be empty (default) - loaded on demand
                    ))
                    vLog { "DvdVideoProvider: openDvd() - Title $titleNumber added to list" }
                } else {
                    Timber.w("DvdVideoProvider: openDvd() - Title $titleNumber returned null, skipping")
                }
            }
            
            Timber.i("DvdVideoProvider: openDvd() - Loaded ${titles.size} title metadata entries (chapters deferred)")
            
            vLog { "DvdVideoProvider: openDvd() - Attempting to read DVD name" }
            // Try to read DVD name
            val dvdName = try {
                val name = DvdNative.dvdGetName(handle)
                vLog { "DvdVideoProvider: openDvd() - dvdGetName() returned: $name" }
                name?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Timber.w(e, "DvdVideoProvider: openDvd() - Failed to read DVD name")
                null
            }
            
            if (dvdName != null) {
                Timber.i("DvdVideoProvider: openDvd() - DVD name: $dvdName")
            } else {
                vLog { "DvdVideoProvider: openDvd() - DVD name not available" }
            }
            
            vLog { "DvdVideoProvider: openDvd() - Getting deviceId from Android UsbDevice" }
            // Get deviceId from Android UsbDevice
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            vLog { "DvdVideoProvider: openDvd() - UsbManager obtained" }
            val androidDevice = usbManager.deviceList.values.find { 
                it.vendorId == drive.device.vendorId && it.productId == drive.device.productId 
            }
            vLog { "DvdVideoProvider: openDvd() - Android device found: ${androidDevice != null}" }
            val deviceId = androidDevice?.deviceId ?: -1
            vLog { "DvdVideoProvider: openDvd() - Device ID: $deviceId" }
            
            vLog { "DvdVideoProvider: openDvd() - Creating DvdInfo object" }
            val dvdInfo = DvdInfo(
                handle = -1, // Handle is invalid after return
                mountPoint = "", // Not used
                deviceId = deviceId,
                titles = titles,
                name = dvdName
            )
            vLog { "DvdVideoProvider: openDvd() - DvdInfo created with ${titles.size} titles" }
            Timber.i("DvdVideoProvider: openDvd() - Successfully opened DVD")
            return@withContext dvdInfo
            
        } finally {
            vLog { "DvdVideoProvider: openDvd() - Finally block: cleaning up" }
            // Always close native handle and release session
            if (handle > 0) {
                vLog { "DvdVideoProvider: openDvd() - Closing DVD handle: $handle" }
                DvdNative.dvdClose(handle)
                vLog { "DvdVideoProvider: openDvd() - DVD handle closed" }
            } else {
                vLog { "DvdVideoProvider: openDvd() - No handle to close (handle: $handle)" }
            }
            vLog { "DvdVideoProvider: openDvd() - Closing session" }
            opticalDriveProvider.closeSession()
            vLog { "DvdVideoProvider: openDvd() - Session closed" }
        }
    }
    
    /**
     * Generates a Playback URI for a DVD title.
     */
    suspend fun playTitle(
        dvdInfo: DvdInfo,
        titleNumber: Int,
        audioStreamId: Int? = null,
        subtitleStreamId: Int? = null
    ): VideoStream = withContext(Dispatchers.IO) {
        Timber.d("DvdVideoProvider: playTitle() called")
        Timber.d("DvdVideoProvider: playTitle() - titleNumber: $titleNumber, deviceId: ${dvdInfo.deviceId}")
        Timber.d("DvdVideoProvider: playTitle() - Available titles: ${dvdInfo.titles.size}")
        dvdInfo.titles.forEachIndexed { index, title ->
            Timber.d("DvdVideoProvider: playTitle() -   Title[$index]: number=${title.number}, chapters=${title.chapterCount}, duration=${title.duration}ms")
        }
        
        Timber.d("DvdVideoProvider: playTitle() - Searching for title $titleNumber")
        val title = dvdInfo.titles.find { it.number == titleNumber }
        if (title == null) {
            Timber.e("DvdVideoProvider: playTitle() - Title $titleNumber not found in ${dvdInfo.titles.size} available titles")
            throw IllegalArgumentException("Title not found")
        }
        Timber.d("DvdVideoProvider: playTitle() - Title found: number=${title.number}, chapters=${title.chapterCount}, duration=${title.duration}ms")
            
        // Note: We cannot easily probe codec/resolution without opening the DVD again.
        // But creating VideoStream requires these fields?
        // If VideoStream requires them, we might need to keep the DVD open or re-open briefly.
        // For now, we'll use dummy values or maybe we can cache them during openDvd if DvdTitle had them.
        // DvdTitle (Native) usually has aspect ratio / format info.
        
        // Let's assume standard DVD resolution for now to avoid re-opening overhead just for metadata
        // or update DvdTitle to include video attributes in a future refactor.
        
        val uri = "content://com.ble1st.connectias.provider/dvd/${dvdInfo.deviceId}/$titleNumber"
        Timber.d("DvdVideoProvider: playTitle() - Generated URI: $uri")
        
        Timber.d("DvdVideoProvider: playTitle() - Creating VideoStream object")
        val selectedAudio = title.audioTracks.firstOrNull { it.streamId == audioStreamId }
        val selectedSubtitle = title.subtitleTracks.firstOrNull { it.streamId == subtitleStreamId }

        val videoStream = VideoStream(
            codec = "mpeg2", // Standard DVD
            width = 720, // PAL/NTSC typical
            height = 480, 
            bitrate = 5000000L,
            frameRate = 30.0,
            uri = uri,
            audioStreamId = audioStreamId,
            subtitleStreamId = subtitleStreamId,
            audioLanguage = selectedAudio?.language,
            subtitleLanguage = selectedSubtitle?.language
        )
        Timber.d("DvdVideoProvider: playTitle() - VideoStream created: codec=${videoStream.codec}, resolution=${videoStream.width}x${videoStream.height}, uri=${videoStream.uri}")
        Timber.d("DvdVideoProvider: playTitle() - Returning VideoStream")
        return@withContext videoStream
    }

}

private fun com.ble1st.connectias.feature.dvd.native.DvdAudioTrackNative.toModel(): DvdAudioTrack {
    return DvdAudioTrack(
        streamId = streamId,
        language = language,
        codec = codec,
        channels = channels,
        sampleRate = sampleRate
    )
}

private fun com.ble1st.connectias.feature.dvd.native.DvdSubtitleTrackNative.toModel(): DvdSubtitleTrack {
    return DvdSubtitleTrack(
        streamId = streamId,
        language = language,
        type = type
    )
}