package com.ble1st.connectias.feature.dvd.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.dvd.media.AudioCdPlayer
import com.ble1st.connectias.feature.dvd.media.AudioCdProvider
import com.ble1st.connectias.feature.dvd.media.DvdVideoProvider
import com.ble1st.connectias.feature.dvd.models.AudioTrack
import com.ble1st.connectias.feature.dvd.models.DvdInfo
import com.ble1st.connectias.feature.dvd.models.OpticalDrive
import com.ble1st.connectias.feature.dvd.storage.OpticalDriveProvider
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Main entry point for the DVD/CD Player feature.
 * Handles device detection, permission requests, and displays disc content.
 */
@AndroidEntryPoint
@UnstableApi
class DvdCdDetailFragment : Fragment() {
    
    companion object {
        private const val ACTION_USB_PERMISSION = "com.ble1st.connectias.USB_PERMISSION"
    }
    
    @Inject lateinit var opticalDriveProvider: OpticalDriveProvider
    @Inject lateinit var dvdVideoProvider: DvdVideoProvider
    @Inject lateinit var audioCdProvider: AudioCdProvider
    @Inject lateinit var audioCdPlayer: AudioCdPlayer
    
    private var usbPermissionReceiver: BroadcastReceiver? = null
    
    // State to trigger recomposition/redetection
    private val refreshTrigger = mutableStateOf(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("DvdCdDetailFragment: onCreateView")
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val trigger by refreshTrigger
                    
                    // UI State
                    var drive by remember { mutableStateOf<OpticalDrive?>(null) }
                    var potentialDevice by remember { mutableStateOf<android.hardware.usb.UsbDevice?>(null) }
                    var hasPermission by remember { mutableStateOf(false) }
                    var isLoading by remember { mutableStateOf(true) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var autoRetryAttempts by remember { mutableStateOf(0) }
                    
                    // Load Data
                    LaunchedEffect(trigger) {
                        isLoading = true
                        errorMessage = null
                        
                        try {
                            // 1. Check for any connected Mass Storage devices
                            val devices = opticalDriveProvider.getConnectedUsbMassStorageDevices()
                            if (devices.isEmpty()) {
                                drive = null
                                potentialDevice = null
                                hasPermission = false
                                isLoading = false
                                return@LaunchedEffect
                            }
                            
                            val device = devices.first() // Take the first one for now
                            potentialDevice = device
                            
                            // 2. Check Permission
                            if (opticalDriveProvider.hasPermission(device)) {
                                hasPermission = true
                                // 3. If permission granted, Detect Optical Drive details
                                // Only detect if we don't have a drive loaded, or if it's a forced refresh (trigger > 0 implies user action or initial load)
                                // We check if drive is null to avoid re-detection on simple recompositions if trigger didn't change (but trigger is key)
                                
                                // Note: detectOpticalDrive now manages the session lock internally
                                val detectedDrive = opticalDriveProvider.detectOpticalDrive()
                                
                                if (detectedDrive != null) {
                                    drive = detectedDrive
                                } else {
                                    // Permission granted, but not identified as Optical Drive (or other error)
                                    errorMessage = "Device found but not recognized as an Optical Drive.\nPlease ensure a disc is inserted."
                                    drive = null
                                }
                            } else {
                                hasPermission = false
                                drive = null
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            Timber.d("DvdCdDetailFragment: Initialization cancelled (composition disposed)")
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Error during initialization")
                            errorMessage = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                    
                    // Auto-retry once on initialization errors to avoid manual "Retry"
                    LaunchedEffect(errorMessage, isLoading) {
                        if (!isLoading && errorMessage != null && autoRetryAttempts < 1) {
                            Timber.w("DvdCdDetailFragment: Auto-retrying after error: $errorMessage")
                            autoRetryAttempts++
                            delay(800)
                            refreshTrigger.value++
                        }
                    }
                    
                    // Main Content Switcher
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoading) {
                            LoadingScreen()
                        } else if (potentialDevice == null) {
                            NoDeviceScreen()
                        } else if (!hasPermission) {
                            PermissionScreen(
                                deviceName = potentialDevice?.productName ?: "USB Device",
                                onRequestPermission = {
                                    potentialDevice?.let { requestUsbPermission(it) }
                                }
                            )
                        } else if (errorMessage != null) {
                            ErrorScreen(message = errorMessage!!) {
                                refreshTrigger.value++
                            }
                        } else if (drive != null) {
                            // Show the detailed player/browser UI
                            DvdCdDetailScreen(
                                drive = drive!!,
                                opticalDriveProvider = opticalDriveProvider,
                                dvdVideoProvider = dvdVideoProvider,
                                audioCdProvider = audioCdProvider,
                                onDvdInfoLoaded = { /* Managed internally by screen */ },
                                onPlayTitle = { dvdInfo, titleNum, audioId, subId -> playTitle(drive!!, dvdInfo, titleNum, audioId, subId) },
                                onPlayTrack = { track -> playTrack(drive!!, track) },
                                onEject = { 
                                    // Refresh after eject
                                    refreshTrigger.value++
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun requestUsbPermission(device: android.hardware.usb.UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            requireContext(), 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
        )
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        usbManager.requestPermission(device, permissionIntent)
    }
    
    override fun onResume() {
        super.onResume()
        registerReceiver()
        // Trigger a refresh on resume to catch permission changes if they happened outside
        refreshTrigger.value++
    }
    
    override fun onPause() {
        super.onPause()
        unregisterReceiver()
    }
    
    private fun registerReceiver() {
        if (usbPermissionReceiver == null) {
            usbPermissionReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (ACTION_USB_PERMISSION == intent.action) {
                        synchronized(this) {
                            val device: android.hardware.usb.UsbDevice? =
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)

                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                device?.let {
                                    Timber.d("Permission granted for device ${device.deviceName}")
                                    refreshTrigger.value++
                                }
                            } else {
                                Timber.d("Permission denied for device ${device?.deviceName}")
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(
                requireContext(),
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }
    
    private fun unregisterReceiver() {
        usbPermissionReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Ignore if not registered
            }
        }
        usbPermissionReceiver = null
    }

    private fun playTitle(drive: OpticalDrive, dvdInfo: DvdInfo, titleNumber: Int, audioStreamId: Int?, subtitleStreamId: Int?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Create VideoStream
                val videoStream = dvdVideoProvider.playTitle(dvdInfo, titleNumber, audioStreamId, subtitleStreamId)
                
                // Open persistent session
                withContext(Dispatchers.IO) {
                    opticalDriveProvider.openSession(drive)
                }
                
                // Navigate
                // Note: Using getIdentifier to avoid circular dependency with app module.
                // The navigation ID is defined in app module's nav_graph.xml, so we cannot
                // directly reference R.id.nav_dvd_player from this feature module.
                @SuppressLint("DiscouragedApi")
                val navId = resources.getIdentifier("nav_dvd_player", "id", requireContext().packageName)
                if (navId != 0) {
                    val args = Bundle().apply {
                        putParcelable(DvdPlayerFragment.ARG_VIDEO_STREAM, videoStream)
                        putParcelable(DvdPlayerFragment.ARG_USB_DEVICE, drive.device)
                    }
                    findNavController().navigate(navId, args)
                } else {
                    Snackbar.make(requireView(), "Player screen not found", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing title")
                Snackbar.make(requireView(), "Playback failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun playTrack(drive: OpticalDrive, track: AudioTrack) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                audioCdPlayer.playTrack(drive, track)
            } catch (e: Exception) {
                Timber.e(e, "Error playing track")
                withContext(Dispatchers.Main) {
                    Snackbar.make(requireView(), "Playback failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoDeviceScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Usb,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Optical Drive Detected",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please connect an external DVD/CD drive via USB.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionScreen(deviceName: String, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Usb,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Connectias needs permission to access the connected device:\n$deviceName",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
