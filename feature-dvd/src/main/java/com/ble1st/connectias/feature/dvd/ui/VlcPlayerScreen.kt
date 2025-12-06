package com.ble1st.connectias.feature.dvd.ui

import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import timber.log.Timber

@Composable
fun VlcPlayerScreen(
    usbDevice: UsbDevice,
    devicePath: String,
    driver: UsbBlockDevice?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vlcPlayer = remember { VlcDvdPlayer(context) }
    
    // Manage lifecycle
    DisposableEffect(Unit) {
        onDispose {
            vlcPlayer.release()
        }
    }

    // Auto-start
    LaunchedEffect(Unit) {
        vlcPlayer.playDvd(usbDevice, devicePath, driver)
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
                // D-Pad for Menu Navigation
                Text(
                    text = "DVD Menu Control",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Up
                    IconButton(
                        onClick = { vlcPlayer.navigateUp() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Up")
                    }
                    // Down
                    IconButton(
                        onClick = { vlcPlayer.navigateDown() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "Down")
                    }
                    // Left
                    IconButton(
                        onClick = { vlcPlayer.navigateLeft() },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Left")
                    }
                    // Right
                    IconButton(
                        onClick = { vlcPlayer.navigateRight() },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, "Right")
                    }
                    // Enter (Center)
                    FilledIconButton(
                        onClick = { vlcPlayer.navigateEnter() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Circle, "Enter", modifier = Modifier.size(12.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { vlcPlayer.stop(); onBack() }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
