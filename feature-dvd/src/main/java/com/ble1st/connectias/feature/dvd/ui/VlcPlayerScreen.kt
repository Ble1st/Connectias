package com.ble1st.connectias.feature.dvd.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ble1st.connectias.feature.dvd.R
import com.ble1st.connectias.feature.dvd.driver.UsbBlockDevice
import com.ble1st.connectias.feature.dvd.media.VlcDvdPlayer
import com.ble1st.connectias.feature.dvd.models.UsbDevice
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private enum class OrientationMode {
    AutoSensor,
    LockLandscape
}

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
    val configuration = LocalConfiguration.current
    val vlcPlayer = remember { VlcDvdPlayer(context) }
    val activity = remember(context) { context.findActivity() }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var lastInteractionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var orientationMode by remember { mutableStateOf(OrientationMode.AutoSensor) }
    val previousOrientation = remember { mutableStateOf<Int?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    fun registerInteraction() {
        lastInteractionTimestamp = System.currentTimeMillis()
        controlsVisible = true
    }
    
    // Manage lifecycle
    DisposableEffect(activity) {
        onDispose {
            vlcPlayer.release()
            activity?.requestedOrientation =
                previousOrientation.value ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Restore system bars when leaving
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
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

    // Apply orientation mode and remember previous orientation
    LaunchedEffect(activity, orientationMode) {
        val act = activity ?: return@LaunchedEffect
        if (previousOrientation.value == null) {
            previousOrientation.value = act.requestedOrientation
        }
        act.requestedOrientation = when (orientationMode) {
            OrientationMode.AutoSensor -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientationMode.LockLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Hide controls after inactivity
    LaunchedEffect(lastInteractionTimestamp, controlsVisible) {
        if (controlsVisible) {
            val snapshot = lastInteractionTimestamp
            // Auto-hide after 3 seconds without interaction
            kotlinx.coroutines.delay(3_000)
            if (controlsVisible && snapshot == lastInteractionTimestamp) {
                controlsVisible = false
            }
        }
    }

    // Auto-toggle fullscreen when entering landscape; allows manual toggle thereafter
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(isLandscape) {
        isFullscreen = isLandscape
    }

    // Apply fullscreen state to window
    LaunchedEffect(isFullscreen, activity) {
        val act = activity ?: return@LaunchedEffect
        val window = act.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
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
                .pointerInput(Unit) {
                    detectTapGestures {
                        registerInteraction()
                    }
                }
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
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                registerInteraction()
                                tryAwaitRelease()
                            })
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val orientationLabel =
                            if (orientationMode == OrientationMode.AutoSensor) {
                                stringResource(R.string.dvd_player_orientation_auto)
                            } else {
                                stringResource(R.string.dvd_player_orientation_lock)
                            }
                        // Orientation toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = orientationLabel,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = {
                                registerInteraction()
                                orientationMode = when (orientationMode) {
                                    OrientationMode.AutoSensor -> OrientationMode.LockLandscape
                                    OrientationMode.LockLandscape -> OrientationMode.AutoSensor
                                }
                            }) {
                                val icon = if (orientationMode == OrientationMode.AutoSensor) {
                                    Icons.Default.ScreenRotation
                                } else {
                                    Icons.Default.ScreenLockRotation
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(R.string.dvd_player_orientation_toggle),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Fullscreen toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val fullscreenLabel = if (isFullscreen) {
                                stringResource(R.string.dvd_player_fullscreen_on)
                            } else {
                                stringResource(R.string.dvd_player_fullscreen_off)
                            }
                            Text(
                                text = fullscreenLabel,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = {
                                registerInteraction()
                                isFullscreen = !isFullscreen
                            }) {
                                val icon = if (isFullscreen) {
                                    Icons.Default.FullscreenExit
                                } else {
                                    Icons.Default.Fullscreen
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(R.string.dvd_player_fullscreen_toggle),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Transport Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(onClick = {
                                registerInteraction()
                                vlcPlayer.seekBy(-10_000)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "Rewind 10s",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                registerInteraction()
                                if (isPlaying) {
                                    vlcPlayer.pause()
                                } else {
                                    vlcPlayer.resume()
                                }
                                isPlaying = vlcPlayer.isPlaying()
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                registerInteraction()
                                vlcPlayer.seekBy(10_000)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Forward 10s",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                registerInteraction()
                                vlcPlayer.stop()
                                onBack()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Progress + time
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = sliderPosition,
                                onValueChange = { value ->
                                    registerInteraction()
                                    sliderPosition = value
                                    isSeeking = true
                                },
                                onValueChangeFinished = {
                                    registerInteraction()
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
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    var depth = 0
    while (ctx is android.content.ContextWrapper && depth < 100) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
        depth++
    }
    return null
}
