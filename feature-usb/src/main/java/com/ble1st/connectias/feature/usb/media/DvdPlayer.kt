package com.ble1st.connectias.feature.usb.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ble1st.connectias.feature.usb.models.VideoStream
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Player for Video DVD playback.
 * 
 * Note: This class is marked @Singleton for dependency injection, but ExoPlayer instances
 * should be managed by the consuming component (Activity/Fragment/ViewModel) lifecycle.
 * Consider refactoring to use a Factory pattern or @ActivityRetainedScoped for better lifecycle management.
 */
@Singleton
class DvdPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private var exoPlayer: ExoPlayer? = null
    private var isReleased = false
    private var playerListener: Player.Listener? = null
    
    /**
     * Plays a video stream.
     * @throws IllegalStateException if player has been released
     * @throws IllegalArgumentException if stream URI is null or invalid
     */
    fun playStream(stream: VideoStream) {
        synchronized(lock) {
            if (isReleased) {
                Timber.e("Attempted to play stream after player was released")
                throw IllegalStateException("Player has been released")
            }
            
            Timber.d("Playing video stream: ${stream.codec}, ${stream.width}x${stream.height}")
            
            // Create ExoPlayer if needed
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context).build()
                
                // Add Player.Listener to handle playback events
                playerListener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> Timber.d("Player state: BUFFERING")
                            Player.STATE_READY -> Timber.d("Player state: READY")
                            Player.STATE_ENDED -> Timber.d("Player state: ENDED")
                            Player.STATE_IDLE -> Timber.d("Player state: IDLE")
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Timber.e(error, "ExoPlayer error: ${error.errorCodeName}")
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Timber.d("Player isPlaying changed: $isPlaying")
                    }
                }
                exoPlayer?.addListener(playerListener!!)
                Timber.d("ExoPlayer created for DVD playback")
            }
            
            // Validate stream URI
            val uri = stream.uri
            if (uri.isNullOrBlank()) {
                Timber.e("Video stream URI is null or empty")
                throw IllegalArgumentException("Video stream URI cannot be null or empty")
            }
            
            // Create MediaItem from stream URI
            val mediaItem = MediaItem.fromUri(Uri.parse(uri))
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
            
            Timber.i("Video stream playback started: $uri")
        }
    }
    
    /**
     * Pauses playback.
     * @throws IllegalStateException if player has been released
     */
    fun pause() {
        synchronized(lock) {
            if (isReleased) {
                Timber.e("Attempted to pause after player was released")
                throw IllegalStateException("Player has been released")
            }
            Timber.d("Pausing DVD playback")
            exoPlayer?.pause()
            Timber.d("DVD playback paused")
        }
    }
    
    /**
     * Resumes playback.
     * @throws IllegalStateException if player has been released
     */
    fun resume() {
        synchronized(lock) {
            if (isReleased) {
                Timber.e("Attempted to resume after player was released")
                throw IllegalStateException("Player has been released")
            }
            Timber.d("Resuming DVD playback")
            exoPlayer?.play()
            Timber.d("DVD playback resumed")
        }
    }
    
    /**
     * Stops playback.
     * @throws IllegalStateException if player has been released
     */
    fun stop() {
        synchronized(lock) {
            if (isReleased) {
                Timber.e("Attempted to stop after player was released")
                throw IllegalStateException("Player has been released")
            }
            Timber.d("Stopping DVD playback")
            exoPlayer?.stop()
            Timber.d("DVD playback stopped")
        }
    }
    
    /**
     * Seeks to a specific position.
     * @throws IllegalStateException if player has been released
     */
    fun seekTo(positionMs: Long) {
        synchronized(lock) {
            if (isReleased) {
                Timber.e("Attempted to seek after player was released")
                throw IllegalStateException("Player has been released")
            }
            Timber.d("Seeking to position: ${positionMs}ms")
            exoPlayer?.seekTo(positionMs)
            Timber.d("Seek complete")
        }
    }
    
    /**
     * Gets current playback position.
     * @return Current position in milliseconds, or 0 if player is not available or released
     */
    fun getCurrentPosition(): Long {
        synchronized(lock) {
            return if (isReleased || exoPlayer == null) {
                0L
            } else {
                exoPlayer?.currentPosition ?: 0L
            }
        }
    }
    
    /**
     * Gets the Player interface (not the concrete ExoPlayer implementation).
     * Returns null if player has been released or not yet created.
     * 
     * Note: This method returns the Player interface to limit external access to ExoPlayer-specific APIs.
     * For view binding, use attachPlayerToView() instead.
     */
    fun getPlayer(): Player? {
        synchronized(lock) {
            return if (isReleased) {
                null
            } else {
                exoPlayer
            }
        }
    }
    
    /**
     * Attaches the player to a PlayerView for rendering.
     * This is the preferred method for view binding instead of getPlayer().
     * @throws IllegalStateException if player has been released
     */
    fun attachPlayerToView(playerView: androidx.media3.ui.PlayerView) {
        synchronized(lock) {
            if (isReleased) {
                Timber.e("Attempted to attach player to view after release")
                throw IllegalStateException("Player has been released")
            }
            playerView.player = exoPlayer
            Timber.d("Player attached to view")
        }
    }
    
    /**
     * Releases resources.
     * This method should be called when the player is no longer needed.
     * After release, all other methods will throw IllegalStateException.
     */
    fun release() {
        synchronized(lock) {
            if (isReleased) {
                Timber.w("Attempted to release already released player")
                return
            }
            
            Timber.d("Releasing DvdPlayer resources")
            
            // Remove listener to prevent leaks
            playerListener?.let { listener ->
                exoPlayer?.removeListener(listener)
            }
            playerListener = null
            
            exoPlayer?.release()
            exoPlayer = null
            isReleased = true
            
            Timber.d("DvdPlayer resources released")
        }
    }
}
