package com.ble1st.connectias.feature.usb.media

import com.ble1st.connectias.feature.usb.models.AudioTrack
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.SecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for Audio CD operations.
 */
@Singleton
class AudioCdProvider @Inject constructor() {
    
    companion object {
        /**
         * CD-quality audio constants: 44.1kHz, 16-bit, stereo
         * Bytes per second: 44,100 samples/sec * 16 bits/sample / 8 * 2 channels = 176,400 B/s
         * Bytes per minute: 176,400 * 60 = 10,584,000 B/min ≈ 10.58 MB/min
         */
        private const val CD_SAMPLE_RATE = 44_100
        private const val CD_BITS_PER_SAMPLE = 16
        private const val CD_CHANNELS = 2
        private const val CD_BYTES_PER_SECOND = (CD_SAMPLE_RATE * CD_BITS_PER_SAMPLE / 8 * CD_CHANNELS).toLong() // 176,400
        private const val CD_BYTES_PER_MINUTE = CD_BYTES_PER_SECOND * 60L // 10,584,000
        
        /**
         * CDDA (Compact Disc Digital Audio) sector size: 2352 bytes
         * This is the standard sector size for audio tracks on CDs
         */
        private const val CDDA_SECTOR_SIZE = 2352L
    }
    
    /**
     * Gets all tracks from an Audio CD.
     * Returns a Result that wraps either the list of tracks or an error.
     * 
     * @param drive The optical drive containing the Audio CD
     * @return AudioCdResult.Success with tracks, or AudioCdResult.Error on failure
     */
    suspend fun getTracks(drive: OpticalDrive): AudioCdResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Reading Audio CD tracks from: ${drive.mountPoint}")
            
            val rootDir = File(drive.mountPoint)
            if (!rootDir.exists() || !rootDir.isDirectory) {
                val errorMsg = "Mount point does not exist: ${drive.mountPoint}"
                Timber.w(errorMsg)
                return@withContext AudioCdResult.Error(errorMsg)
            }
            
            // Look for audio track files
            val files = rootDir.listFiles()
            val tracks = mutableListOf<AudioTrack>()
            
            val audioExtensions = setOf("wav", "mp3", "flac", "ogg", "m4a", "aac")
            val audioFiles = files?.filter { it.isFile && it.extension.lowercase() in audioExtensions } ?: emptyList()
            
            // Calculate cumulative sector positions for proper track indexing
            var currentSector = 0L
            
            audioFiles.forEachIndexed { index, file ->
                val trackNumber = index + 1
                val fileSize = file.length()
                val startSector = currentSector
                // Calculate sectors: ceiling division, but ensure at least 1 sector for empty files
                val sectorsInTrack = maxOf(1L, (fileSize + CDDA_SECTOR_SIZE - 1) / CDDA_SECTOR_SIZE)
                val endSector = startSector + sectorsInTrack
                
                val track = AudioTrack(
                    number = trackNumber,
                    title = extractTrackTitle(file.name),
                    duration = estimateTrackDuration(file),
                    startSector = startSector,
                    endSector = endSector
                )
                tracks.add(track)
                currentSector = endSector
                Timber.d("Track $trackNumber: ${track.title}, duration=${track.duration}ms, sectors=$startSector-$endSector")
            }
            
            // If no files found, try CDDA structure
            if (tracks.isEmpty()) {
                val cddaTracks = parseCddaStructure(rootDir)
                if (cddaTracks.isNotEmpty()) {
                    tracks.addAll(cddaTracks)
                }
            }
            
            Timber.i("Audio CD read: ${tracks.size} tracks found")
            AudioCdResult.Success(tracks)
        } catch (e: IOException) {
            val errorMsg = "I/O error reading Audio CD tracks: ${e.message}"
            Timber.e(e, errorMsg)
            AudioCdResult.Error.fromThrowable(e)
        } catch (e: SecurityException) {
            val errorMsg = "Security error accessing Audio CD: ${e.message}"
            Timber.e(e, errorMsg)
            AudioCdResult.Error.fromThrowable(e)
        } catch (e: Exception) {
            // Re-throw unexpected exceptions to avoid hiding critical errors
            val errorMsg = "Unexpected error reading Audio CD tracks: ${e.message}"
            Timber.e(e, errorMsg)
            throw e
        }
    }
    
    private fun extractTrackTitle(fileName: String): String? {
        // Try to extract title from filename
        val nameWithoutExt = fileName.substringBeforeLast(".")
        return if (nameWithoutExt.isNotEmpty()) nameWithoutExt else null
    }
    
    /**
     * Estimates track duration based on file size assuming CD-quality audio.
     * CD-quality: 44.1kHz, 16-bit, stereo ≈ 10.58 MB/min (176,400 bytes/sec)
     * 
     * @param file The audio file
     * @return Duration in milliseconds
     */
    private fun estimateTrackDuration(file: File): Long {
        val fileSizeBytes = file.length()
        // Duration in seconds = fileSizeBytes / bytesPerSecond
        // Convert to milliseconds: * 1000
        val durationMs = (fileSizeBytes * 1000L) / CD_BYTES_PER_SECOND
        return durationMs
    }
    
    private fun parseCddaStructure(rootDir: File): List<AudioTrack> {
        Timber.d("Parsing CDDA structure...")
        // TODO: Implement CDDA structure parsing
        return emptyList()
    }
}
