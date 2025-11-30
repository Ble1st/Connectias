package com.ble1st.connectias.feature.usb.ui

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
import com.ble1st.connectias.feature.usb.R
import com.ble1st.connectias.feature.usb.media.DvdPlayer
import com.ble1st.connectias.feature.usb.models.VideoStream
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import timber.log.Timber

@Composable
fun DvdPlayerScreen(
    videoStream: VideoStream,
    dvdPlayer: DvdPlayer,
    dvdSettings: DvdSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player = remember { dvdPlayer.getPlayer() }
    var isPlaying by remember { mutableStateOf(player?.isPlaying ?: false) }
    
    // PlayerView for ExoPlayer video rendering
    // Use a non-state reference to avoid storing Android View in Compose state
    val playerViewRef = remember { arrayOf<PlayerView?>(null) }
    
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                Timber.d("Player isPlaying changed: $isPlayingValue")
                isPlaying = isPlayingValue
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.d("Player playback state changed: $playbackState")
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
        
        // Control Bar
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // CSS-Decryption Status
                if (dvdSettings.isCssDecryptionEnabled()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.dvd_player_css_decryption_enabled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
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
