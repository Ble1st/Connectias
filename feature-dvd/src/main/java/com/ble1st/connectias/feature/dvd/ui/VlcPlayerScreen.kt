package com.ble1st.connectias.feature.dvd.ui

import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ble1st.connectias.feature.dvd.R
import com.ble1st.connectias.feature.dvd.media.VlcDvdPlayer
import com.ble1st.connectias.feature.dvd.models.UsbDevice
import com.ble1st.connectias.feature.dvd.driver.UsbBlockDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun VlcPlayerScreen(
    usbDevice: UsbDevice,
    devicePath: String,
    driver: UsbBlockDevice?,
    audioStreamId: Int? = null,
    subtitleStreamId: Int? = null,
    audioLanguage: String? = null,
    subtitleLanguage: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vlcPlayer = remember { VlcDvdPlayer(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    
    // Manage lifecycle
    DisposableEffect(Unit) {
        onDispose {
            vlcPlayer.release()
        }
    }

    // Auto-start on background thread to avoid blocking UI
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                vlcPlayer.playDvd(
                    usbDevice = usbDevice,
                    devicePath = devicePath,
                    driver = driver,
                    titleNumber = null,
                    audioStreamId = audioStreamId,
                    subtitleStreamId = subtitleStreamId,
                    audioLanguage = audioLanguage,
                    subtitleLanguage = subtitleLanguage
                )
                isPlaying = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to start DVD playback")
            }
        }
    }

    // Poll playback position and duration
    LaunchedEffect(isSeeking) {
        while (true) {
            val length = vlcPlayer.getLengthMs()
            val time = vlcPlayer.getTimeMs()
            durationMs = length
            if (!isSeeking && length > 0) {
                sliderPosition = (time.toFloat() / length.toFloat()).coerceIn(0f, 1f)
            }
            positionMs = time
            kotlinx.coroutines.delay(500)
        }
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Video Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        vlcPlayer.attachView(this)
                    }
                },
                update = { surfaceView ->
                    // Updates if needed
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Minimal Controls
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = {
                        vlcPlayer.seekBy(-10_000)
                    }) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s"
                        )
                    }
                    IconButton(onClick = {
                        if (isPlaying) {
                            vlcPlayer.pause()
                        } else {
                            vlcPlayer.resume()
                        }
                        isPlaying = vlcPlayer.isPlaying()
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    IconButton(onClick = {
                        vlcPlayer.seekBy(10_000)
                    }) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s"
                        )
                    }
                    IconButton(onClick = { vlcPlayer.stop(); onBack() }) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress + time
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { value ->
                            sliderPosition = value
                            isSeeking = true
                        },
                        onValueChangeFinished = {
                            val target = (durationMs * sliderPosition).toLong()
                            vlcPlayer.seekTo(target)
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(positionMs),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = formatTime(durationMs),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
