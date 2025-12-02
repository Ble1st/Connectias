package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.ble1st.connectias.feature.dvd.models.VideoStream
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
    @OptIn(UnstableApi::class)
    fun playStream(stream: VideoStream) {
        Timber.d("=== DvdPlayer: playStream CALLED ===")
        Timber.d("DvdPlayer: VideoStream details:")
        Timber.d("  - codec: ${stream.codec}")
        Timber.d("  - width: ${stream.width}")
        Timber.d("  - height: ${stream.height}")
        Timber.d("  - bitrate: ${stream.bitrate}")
        Timber.d("  - frameRate: ${stream.frameRate}")
        Timber.d("  - uri: ${stream.uri}")
        
        synchronized(lock) {
            Timber.d("DvdPlayer: Acquired lock, current thread: ${Thread.currentThread().name}")
            Timber.d("DvdPlayer: Is main thread: ${Looper.myLooper() == Looper.getMainLooper()}")
            
            // Create ExoPlayer if needed
            if (exoPlayer == null) {
                Timber.d("DvdPlayer: ExoPlayer is null, creating new instance...")
                
                // Ensure ExoPlayer creation happens on main thread
                check(Looper.myLooper() == Looper.getMainLooper()) {
                    "ExoPlayer must be created on the main thread"
                }
                
                Timber.d("DvdPlayer: Building ExoPlayer with PS (Program Stream) extractor...")
                
                // Create extractors factory that prioritizes PsExtractor for MPEG-2 PS (DVD VOB)
                val extractorsFactory = DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true)
                    .setConstantBitrateSeekingAlwaysEnabled(true)
                
                // Create data source factory
                val dataSourceFactory = DefaultDataSource.Factory(context)
                
                // Create media source factory with our extractor configuration
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                
                // Configure LoadControl for streaming from ContentProvider
                // Use larger buffers and longer wait times to allow ExoPlayer to parse the stream
                // For MPEG-PS streams, ExoPlayer needs more data to properly parse the format
                val loadControl: LoadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        50000,  // minBufferMs: Minimum buffer before playback starts (50 seconds)
                        120000, // maxBufferMs: Maximum buffer size (120 seconds)
                        10000,  // bufferForPlaybackMs: Buffer required before starting playback (10 seconds)
                        10000   // bufferForPlaybackAfterRebufferMs: Buffer required after rebuffering (10 seconds)
                    )
                    .setBackBuffer(30000, true) // Keep 30 seconds of back buffer
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
                
                Timber.d("DvdPlayer: LoadControl configured with large buffers for streaming")
                
                exoPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setLoadControl(loadControl)
                    .build()
                    
                Timber.d("DvdPlayer: ExoPlayer built successfully with PS extractor support")
                
                // Add Player.Listener to handle playback events
                playerListener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateName = when (playbackState) {
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            Player.STATE_IDLE -> "IDLE"
                            else -> "UNKNOWN($playbackState)"
                        }
                        Timber.d("=== DvdPlayer: Playback state changed to: $stateName ===")
                        
                        exoPlayer?.let { player ->
                            Timber.d("DvdPlayer: Buffered position: ${player.bufferedPosition}ms")
                            Timber.d("DvdPlayer: Total buffered duration: ${player.bufferedPosition - player.currentPosition}ms")
                            
                            // Log additional info when playback ends
                            if (playbackState == Player.STATE_ENDED) {
                                Timber.d("DvdPlayer: Duration: ${player.duration}ms")
                                Timber.d("DvdPlayer: Current position: ${player.currentPosition}ms")
                                Timber.d("DvdPlayer: Is playing: ${player.isPlaying}")
                                Timber.d("DvdPlayer: Play when ready: ${player.playWhenReady}")
                                Timber.d("DvdPlayer: Timeline window count: ${player.currentTimeline.windowCount}")
                                
                                // Check if we have tracks but playback ended prematurely
                                val tracks = player.currentTracks
                                if (tracks.groups.isNotEmpty()) {
                                    Timber.w("DvdPlayer: Playback ended but tracks were detected - this may indicate a streaming issue")
                                }
                            }
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Timber.e(error, "=== DvdPlayer: PLAYER ERROR: ${error.errorCodeName} ===")
                        Timber.e("DvdPlayer: Error code: ${error.errorCode}")
                        Timber.e("DvdPlayer: Error message: ${error.message}")
                        Timber.e("DvdPlayer: Error cause: ${error.cause}")
                        error.cause?.let { cause ->
                            Timber.e(cause, "DvdPlayer: Root cause stacktrace:")
                        }
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Timber.d("DvdPlayer: isPlaying changed to: $isPlaying")
                    }
                    
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        Timber.d("=== DvdPlayer: Tracks changed ===")
                        Timber.d("DvdPlayer: Number of track groups: ${tracks.groups.size}")
                        tracks.groups.forEachIndexed { index, group ->
                            Timber.d("DvdPlayer: Track group $index: ${group.type}, ${group.length} tracks, selected=${group.isSelected}")
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                Timber.d("DvdPlayer:   Track $i: ${format.sampleMimeType}, ${format.width}x${format.height}")
                            }
                        }
                        if (tracks.groups.isEmpty()) {
                            Timber.w("DvdPlayer: NO TRACKS FOUND - ExoPlayer could not detect any media tracks!")
                        }
                    }
                }
                exoPlayer?.addListener(playerListener!!)
                Timber.d("DvdPlayer: ExoPlayer listener attached")
            } else {
                Timber.d("DvdPlayer: ExoPlayer already exists, reusing")
            }
            
            // Validate stream URI
            val uri = stream.uri
            if (uri.isNullOrBlank()) {
                Timber.e("DvdPlayer: Video stream URI is null or empty!")
                throw IllegalArgumentException("Video stream URI cannot be null or empty")
            }
            
            // Parse and validate URI
            Timber.d("DvdPlayer: Parsing URI: $uri")
            val parsedUri = Uri.parse(uri)
            val scheme = parsedUri.scheme?.lowercase()
            Timber.d("DvdPlayer: URI scheme: $scheme")
            Timber.d("DvdPlayer: URI authority: ${parsedUri.authority}")
            Timber.d("DvdPlayer: URI path: ${parsedUri.path}")
            
            if (scheme.isNullOrBlank()) {
                Timber.e("DvdPlayer: Video stream URI has no scheme: $uri")
                throw IllegalArgumentException("Video stream URI must have a valid scheme (http, https, file, or content): $uri")
            }
            val validSchemes = setOf("http", "https", "file", "content")
            if (scheme !in validSchemes) {
                Timber.e("DvdPlayer: Video stream URI has invalid scheme '$scheme': $uri")
                throw IllegalArgumentException("Video stream URI scheme must be one of [${validSchemes.joinToString()}], but was '$scheme': $uri")
            }
            
            Timber.d("DvdPlayer: Creating MediaItem from URI with MPEG-PS mime type...")
            
            // DVD VOB files are MPEG-2 Program Stream (PS)
            // We must explicitly set the MIME type so ExoPlayer knows how to parse the stream
            val mediaItem = MediaItem.Builder()
                .setUri(parsedUri)
                .setMimeType(MimeTypes.VIDEO_PS) // MPEG-2 Program Stream (video/mp2p)
                .build()
            
            Timber.d("DvdPlayer: MediaItem created with MIME type: ${MimeTypes.VIDEO_PS}")
            Timber.d("DvdPlayer: MediaItem: $mediaItem")
            
            Timber.d("DvdPlayer: Setting media item on ExoPlayer...")
            exoPlayer?.setMediaItem(mediaItem)
            
            Timber.d("DvdPlayer: Preparing ExoPlayer...")
            exoPlayer?.prepare()
            
            Timber.d("DvdPlayer: Starting playback...")
            exoPlayer?.play()
            
            Timber.d("=== DvdPlayer: playStream COMPLETED ===")
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
