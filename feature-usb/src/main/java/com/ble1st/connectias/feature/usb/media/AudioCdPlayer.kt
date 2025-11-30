package com.ble1st.connectias.feature.usb.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.ble1st.connectias.feature.usb.models.AudioTrack
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Player for Audio CD tracks.
 * 
 * This class is a @Singleton for dependency injection, but the ExoPlayer instance
 * can be released and reinitialized as needed. The singleton instance persists
 * across the application lifecycle, but internal player resources are managed
 * independently. Use releasePlayer() to free ExoPlayer resources when under memory
 * pressure, or let the singleton manage the player lifecycle automatically.
 */
@Singleton
class AudioCdPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val exoPlayerLock = Any()
    private var exoPlayer: ExoPlayer? = null
    
    /**
     * Initializes ExoPlayer if not already initialized.
     * This method can be called to reinitialize the player after releasePlayer().
     */
    private fun ensurePlayerInitialized() {
        synchronized(exoPlayerLock) {
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context).build()
                Timber.d("ExoPlayer created")
            }
        }
    }
    
    /**
     * Plays an audio track from an Audio CD.
     */
    fun playTrack(drive: OpticalDrive, track: AudioTrack) {
        try {
            Timber.d("Playing Audio CD track ${track.number}: ${track.title}")
            
            // Find the audio file for this track (file I/O outside synchronized block)
            val trackFile = findTrackFile(drive.mountPoint, track.number)
            if (trackFile == null || !trackFile.exists()) {
                Timber.e("Track file not found for track ${track.number} in ${drive.mountPoint}")
                return
            }
            
            // Create MediaItem from file URI (outside synchronized block)
            val mediaItem = MediaItem.fromUri(Uri.fromFile(trackFile))
            Timber.d("Created MediaItem from file: ${trackFile.absolutePath}")
            
            // Create ExoPlayer if needed
            ensurePlayerInitialized()
            
            // Synchronized block only for player operations
            synchronized(exoPlayerLock) {
                val player = exoPlayer ?: run {
                    Timber.e("ExoPlayer is null after initialization")
                    return
                }
                
                try {
                    // Stop any current playback and clear previous items
                    if (player.isPlaying) {
                        Timber.d("Stopping current playback before starting new track")
                        player.stop()
                    }
                    player.clearMediaItems()
                    
                    // Set media item and prepare
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    
                    // Start playback
                    player.play()
                    Timber.i("Started playback of track ${track.number}: ${track.title}")
                } catch (e: Exception) {
                    Timber.e(e, "Error preparing or starting playback for track ${track.number}")
                    // Reset player state on error
                    try {
                        player.stop()
                        player.clearMediaItems()
                    } catch (resetError: Exception) {
                        Timber.e(resetError, "Error resetting player after playback failure")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio track")
        }
    }
    
    /**
     * Finds the audio file for a given track number.
     * Searches for common audio file patterns in the mount point directory.
     */
    private fun findTrackFile(mountPoint: String, trackNumber: Int): File? {
        val rootDir = File(mountPoint)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            Timber.w("Mount point does not exist: $mountPoint")
            return null
        }
        
        val files = rootDir.listFiles() ?: return null
        val audioExtensions = setOf("wav", "mp3", "flac", "ogg", "m4a", "aac")
        
        // Try common patterns: track01.wav, track1.wav, 01.wav, 1.wav, etc.
        val trackNumberStr = String.format("%02d", trackNumber)
        val trackNumberShort = trackNumber.toString()
        
        val patterns = listOf(
            "track$trackNumberStr",
            "track$trackNumberShort",
            trackNumberStr,
            trackNumberShort
        )
        
        // First, try exact pattern matches
        for (pattern in patterns) {
            for (ext in audioExtensions) {
                val fileName = "$pattern.$ext"
                val file = File(rootDir, fileName)
                if (file.exists() && file.isFile) {
                    Timber.d("Found track file: ${file.absolutePath}")
                    return file
                }
            }
        }
        
        // If no exact match, search for files containing track number in name
        val matchingFiles = files.filter { file ->
            if (!file.isFile) return@filter false
            val name = file.name.lowercase()
            val ext = file.extension.lowercase()
            ext in audioExtensions && (
                name.contains("track$trackNumberStr") ||
                name.contains("track$trackNumberShort") ||
                name.startsWith("$trackNumberStr.") ||
                name.startsWith("$trackNumberShort.")
            )
        }
        
        if (matchingFiles.isNotEmpty()) {
            val file = matchingFiles.first()
            Timber.d("Found track file by pattern: ${file.absolutePath}")
            return file
        }
        
        // Last resort: if files are numbered sequentially, use index
        val audioFiles = files.filter { file ->
            file.isFile && file.extension.lowercase() in audioExtensions
        }.sortedBy { it.name }
        
        if (audioFiles.size >= trackNumber) {
            val file = audioFiles[trackNumber - 1] // 0-indexed
            Timber.d("Found track file by index: ${file.absolutePath}")
            return file
        }
        
        Timber.w("No audio file found for track $trackNumber in $mountPoint")
        return null
    }
    
    /**
     * Checks if audio is currently playing.
     */
    fun isPlaying(): Boolean {
        return synchronized(exoPlayerLock) {
            exoPlayer?.isPlaying ?: false
        }
    }
    
    /**
     * Pauses playback without resetting the player state.
     */
    fun pause() {
        try {
            synchronized(exoPlayerLock) {
                exoPlayer?.let {
                    Timber.d("Pausing audio playback")
                    it.pause()
                    Timber.d("Audio playback paused")
                } ?: Timber.d("No active player to pause")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error pausing playback")
        }
    }
    
    /**
     * Stops playback.
     * Only stops if currently playing to avoid unnecessary resets.
     */
    fun stop() {
        try {
            synchronized(exoPlayerLock) {
                if (exoPlayer?.isPlaying == true) {
                    Timber.d("Stopping audio playback")
                    exoPlayer?.stop()
                    Timber.d("Audio playback stopped")
                } else {
                    Timber.d("Audio playback already stopped, skipping stop()")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping playback")
        }
    }
    
    /**
     * Releases ExoPlayer resources when under memory pressure.
     * The player will be automatically reinitialized on next use.
     * 
     * This method is public for application-level components that need to manage
     * resource pressure. It is intended for use by application lifecycle managers
     * or memory pressure handlers, but can be called by any component that needs
     * to free player resources.
     */
    fun releasePlayer() {
        try {
            Timber.d("Releasing ExoPlayer resources")
            synchronized(exoPlayerLock) {
                exoPlayer?.release()
                exoPlayer = null
            }
            Timber.d("ExoPlayer resources released")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing player")
        }
    }
    
}
