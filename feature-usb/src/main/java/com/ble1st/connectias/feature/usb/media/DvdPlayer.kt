package com.ble1st.connectias.feature.usb.media

import android.content.Context
import android.net.Uri
import android.os.Looper
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
 * 
 * ExoPlayer operations are performed on the main thread as required by ExoPlayer.
 */
@Singleton
class DvdPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private var exoPlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    
    /**
     * Plays a video stream.
     * @throws IllegalArgumentException if stream URI is null or invalid
     */
    fun playStream(stream: VideoStream) {
        synchronized(lock) {
            Timber.d("Playing video stream: ${stream.codec}, ${stream.width}x${stream.height}")
            
            // Create ExoPlayer if needed
            if (exoPlayer == null) {
                // Ensure ExoPlayer creation happens on main thread
                check(Looper.myLooper() == Looper.getMainLooper()) {
                    "ExoPlayer must be created on the main thread"
                }
                
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
            
            // Parse and validate URI
            val parsedUri = Uri.parse(uri)
            val scheme = parsedUri.scheme?.lowercase()
            if (scheme.isNullOrBlank()) {
                Timber.e("Video stream URI has no scheme: $uri")
                throw IllegalArgumentException("Video stream URI must have a valid scheme (http, https, file, or content): $uri")
            }
            val validSchemes = setOf("http", "https", "file", "content")
            if (scheme !in validSchemes) {
                Timber.e("Video stream URI has invalid scheme '$scheme': $uri")
                throw IllegalArgumentException("Video stream URI scheme must be one of [${validSchemes.joinToString()}], but was '$scheme': $uri")
            }
            
            val mediaItem = MediaItem.fromUri(parsedUri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
        }
    }
    
    /**
     * Pauses playback.
     */
    fun pause() {
        synchronized(lock) {
            Timber.d("Pausing DVD playback")
            exoPlayer?.pause()
            Timber.d("DVD playback paused")
        }
    }
    
    /**
     * Resumes playback.
     */
    fun resume() {
        synchronized(lock) {
            Timber.d("Resuming DVD playback")
            exoPlayer?.play()
            Timber.d("DVD playback resumed")
        }
    }
    
    /**
     * Stops playback.
     */
    fun stop() {
        synchronized(lock) {
            Timber.d("Stopping DVD playback")
            exoPlayer?.stop()
            Timber.d("DVD playback stopped")
        }
    }
    
    /**
     * Seeks to a specific position.
     */
    fun seekTo(positionMs: Long) {
        synchronized(lock) {
            Timber.d("Seeking to position: ${positionMs}ms")
            exoPlayer?.seekTo(positionMs)
            Timber.d("Seek complete")
        }
    }
    fun getCurrentPosition(): Long {
        synchronized(lock) {
            return exoPlayer?.currentPosition ?: 0L
        }
    }
    
    /**
     * Gets the Player interface (not the concrete ExoPlayer implementation).
     * Returns null if player has not yet been created.
     * 
     * Note: This method returns the Player interface to limit external access to ExoPlayer-specific APIs.
     * For view binding, use attachPlayerToView() instead.
     */
    fun getPlayer(): Player? {
        synchronized(lock) {
            return exoPlayer
        }
    }
    
    /**
     * Attaches the player to a PlayerView for rendering.
     * This is the preferred method for view binding instead of getPlayer().
     */
    fun attachPlayerToView(playerView: androidx.media3.ui.PlayerView): Boolean {
        synchronized(lock) {
            if (exoPlayer == null) {
                Timber.w("Attempted to attach player to view before player was created")
                return false
            }
            playerView.player = exoPlayer
            Timber.d("Player attached to view")
            return true
        }
    }
    
    /**
     * Releases resources.
     * This method should be called when the player is no longer needed.
     * After release, the player can be recreated on next access via lazy initialization.
     */
    fun release() {
        synchronized(lock) {
            if (exoPlayer == null) {
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
            
            Timber.d("DvdPlayer resources released")
        }
    }
}
