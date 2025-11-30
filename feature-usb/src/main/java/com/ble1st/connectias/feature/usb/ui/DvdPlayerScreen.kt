package com.ble1st.connectias.feature.usb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    
    val player = dvdPlayer.getPlayer()
    
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
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Video Player Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // TODO: Add ExoPlayer SurfaceView/TextureView
            Text(
                text = "Video Player\n${videoStream.width}x${videoStream.height}\n${videoStream.codec}",
                style = MaterialTheme.typography.bodyLarge
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
                                text = "CSS-Decryption aktiviert",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        Timber.d("Previous chapter clicked")
                        // TODO: Implement previous chapter
                    }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
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
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    
                    IconButton(onClick = {
                        Timber.d("Next chapter clicked")
                        // TODO: Implement next chapter
                    }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                    }
                    
                    IconButton(onClick = {
                        Timber.d("Stop clicked")
                        dvdPlayer.stop()
                        onBack()
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
            }
        }
    }
}
