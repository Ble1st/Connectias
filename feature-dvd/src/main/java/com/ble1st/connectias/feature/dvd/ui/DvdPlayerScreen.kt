package com.ble1st.connectias.feature.dvd.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.ble1st.connectias.feature.dvd.R
import com.ble1st.connectias.feature.dvd.media.DvdPlayer
import com.ble1st.connectias.feature.dvd.models.VideoStream
import timber.log.Timber

@Composable
fun DvdPlayerScreen(
    videoStream: VideoStream,
    dvdPlayer: DvdPlayer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // PlayerView for ExoPlayer video rendering
    // Use a non-state reference to avoid storing Android View in Compose state
    val playerViewRef = remember { arrayOf<PlayerView?>(null) }
    
    // Start playback when the screen is displayed
    LaunchedEffect(videoStream) {
        Timber.d("DvdPlayerScreen: Starting playback for URI: ${videoStream.uri}")
        try {
            dvdPlayer.playStream(videoStream)
            isLoading = false
            Timber.d("DvdPlayerScreen: Playback started successfully")
        } catch (e: Exception) {
            Timber.e(e, "DvdPlayerScreen: Failed to start playback")
            playbackError = e.message ?: "Unknown playback error"
            isLoading = false
        }
    }
    
    // Get player after it's initialized
    val player = remember(isLoading) { 
        if (!isLoading) dvdPlayer.getPlayer() else null 
    }
    
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                Timber.d("Player isPlaying changed: $isPlayingValue")
                isPlaying = isPlayingValue
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.d("Player playback state changed: $playbackState")
                when (playbackState) {
                    Player.STATE_READY -> {
                        Timber.d("Player is ready")
                    }
                    Player.STATE_BUFFERING -> {
                        Timber.d("Player is buffering")
                    }
                    Player.STATE_ENDED -> {
                        Timber.d("Playback ended")
                    }
                    Player.STATE_IDLE -> {
                        Timber.d("Player is idle")
                    }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Timber.e(error, "Player error: ${error.errorCodeName}")
                playbackError = "Playback error: ${error.message}"
            }
        }
        player?.addListener(listener)
        
        onDispose {
            player?.removeListener(listener)
            // Detach player from view when composable is disposed
            playerViewRef[0]?.player = null
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Video Player Area with ExoPlayer PlayerView
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Show loading indicator while initializing
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Preparing playback...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            // Show error if playback failed
            else if (playbackError != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = playbackError ?: "Playback error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
            // Show player view when ready
            else {
                AndroidView(
                    factory = { ctx ->
                        val view = PlayerView(ctx)
                        view.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerViewRef[0] = view
                        view
                    },
                    update = { view ->
                        player?.let {
                            view.player = it
                            dvdPlayer.attachPlayerToView(view)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        // Control Bar
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Playback Controls
                val noPreviousChapterText = stringResource(R.string.dvd_player_no_previous_chapter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        Timber.d("Previous chapter clicked")
                        player?.let { p ->
                            if (p.hasPreviousMediaItem()) {
                                p.seekToPreviousMediaItem()
                            } else {
                                Timber.d(noPreviousChapterText)
                            }
                        } ?: run {
                            Timber.w("Player is null, cannot navigate to previous chapter")
                        }
                    }) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.dvd_player_previous)
                        )
                    }
                    
                    IconButton(onClick = {
                        Timber.d("Play/Pause clicked")
                        if (isPlaying) {
                            dvdPlayer.pause()
                        } else {
                            dvdPlayer.resume()
                        }
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.dvd_player_pause)
                            } else {
                                stringResource(R.string.dvd_player_play)
                            }
                        )
                    }
                    
                    val noNextChapterText = stringResource(R.string.dvd_player_no_next_chapter)
                    IconButton(onClick = {
                        Timber.d("Next chapter clicked")
                        player?.let { p ->
                            if (p.hasNextMediaItem()) {
                                p.seekToNextMediaItem()
                            } else {
                                Timber.d(noNextChapterText)
                            }
                        } ?: run {
                            Timber.w("Player is null, cannot navigate to next chapter")
                        }
                    }) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.dvd_player_next)
                        )
                    }
                    
                    IconButton(onClick = {
                        Timber.d("Stop clicked")
                        dvdPlayer.stop()
                        onBack()
                    }) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.dvd_player_stop)
                        )
                    }
                }
            }
        }
    }
}
