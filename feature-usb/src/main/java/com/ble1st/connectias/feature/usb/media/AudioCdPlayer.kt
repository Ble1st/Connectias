package com.ble1st.connectias.feature.usb.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.ble1st.connectias.feature.usb.models.AudioTrack
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Player for Audio CD tracks.
 */
@Singleton
class AudioCdPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    
    /**
     * Plays an audio track from an Audio CD.
     */
    fun playTrack(drive: OpticalDrive, track: AudioTrack) {
        try {
            Timber.d("Playing Audio CD track ${track.number}: ${track.title}")
            
            // Create ExoPlayer if needed
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context).build()
                Timber.d("ExoPlayer created")
            }
            
            // TODO: Create MediaItem from track
            // For now, this is a placeholder
            Timber.i("Audio track playback started: ${track.title}")
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio track")
        }
    }
    
    /**
     * Stops playback.
     */
    fun stop() {
        try {
            Timber.d("Stopping audio playback")
            exoPlayer?.stop()
            Timber.d("Audio playback stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping playback")
        }
    }
    
    /**
     * Releases resources.
     */
    fun release() {
        try {
            Timber.d("Releasing AudioCdPlayer resources")
            exoPlayer?.release()
            exoPlayer = null
            Timber.d("AudioCdPlayer resources released")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing player")
        }
    }
}
