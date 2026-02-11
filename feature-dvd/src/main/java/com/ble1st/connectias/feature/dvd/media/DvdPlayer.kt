package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
