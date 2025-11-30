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
    private val exoPlayerLock = Any()
    private var exoPlayer: ExoPlayer? = null
    
    /**
     * Initializes ExoPlayer if not already initialized.
     * This method can be called to reinitialize the player after release().
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
            
            // Create ExoPlayer if needed
            ensurePlayerInitialized()
            
            // TODO: Create MediaItem from track using drive information
            // TODO: Call exoPlayer.setMediaItem(), prepare(), and play()
            Timber.d("playTrack called but playback not yet implemented")
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio track")
        }
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
            Timber.d("Pausing audio playback")
            synchronized(exoPlayerLock) {
                exoPlayer?.pause()
            }
            Timber.d("Audio playback paused")
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
     * Releases resources.
     * After calling this, the player can be reinitialized on next use via ensurePlayerInitialized().
     * This should be called when the feature is no longer needed (e.g., from ViewModel.onCleared()
     * or Service.onDestroy()).
     */
    fun release() {
        try {
            Timber.d("Releasing AudioCdPlayer resources")
            synchronized(exoPlayerLock) {
                exoPlayer?.release()
                exoPlayer = null
            }
            Timber.d("AudioCdPlayer resources released")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing player")
        }
    }
}
