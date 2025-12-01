package com.ble1st.connectias.feature.usb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.permission.UsbPermissionManager
import com.ble1st.connectias.feature.usb.provider.UsbProvider
import com.ble1st.connectias.feature.usb.provider.UsbResult
import com.ble1st.connectias.feature.usb.ui.components.UsbDeviceActionDialog
import com.ble1st.connectias.feature.usb.ui.components.UsbDeviceList
import com.ble1st.connectias.feature.usb.ui.components.UsbPermissionDialog
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun UsbDashboardScreen(
    usbProvider: UsbProvider,
    permissionManager: UsbPermissionManager,
    deviceDetector: UsbDeviceDetector,
    onDeviceClick: (UsbDevice) -> Unit,
    onOpenDvdDrive: (UsbDevice) -> Unit = onDeviceClick,
    modifier: Modifier = Modifier,
    activity: android.app.Activity? = null
) {
    var devices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var permissionRequest by remember { mutableStateOf<UsbDevice?>(null) }
    var actionDialogDevice by remember { mutableStateOf<UsbDevice?>(null) }
    var previouslyGrantedDevices by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Try to get activity from context if not provided
    val activityInstance = remember(activity) {
        activity ?: (context as? android.app.Activity)
    }
    
    // Handle permission request flow - declare before use
    var permissionFlow by remember { mutableStateOf<kotlinx.coroutines.flow.Flow<Boolean>?>(null) }
    var currentPermissionDevice by remember { mutableStateOf<UsbDevice?>(null) }
    
    // Automatische Erkennung via StateFlow
    val detectedDevices by deviceDetector.detectedDevices.collectAsState()
    LaunchedEffect(detectedDevices) {
        Timber.d("Detected devices changed: ${detectedDevices.size} devices")
        // Merge detected devices with enumerated devices
        val allDevices = (devices + detectedDevices).distinctBy { "${it.vendorId}-${it.productId}" }
        // Compare using the same dedupe key to avoid mismatches from UsbDevice.equals() differences
        val allKeys = allDevices.map { "${it.vendorId}-${it.productId}" }.toSet()
        val currentKeys = devices.map { "${it.vendorId}-${it.productId}" }.toSet()
        if (allKeys != currentKeys) {
            Timber.d("Updating device list with ${allDevices.size} devices")
            devices = allDevices
        }
        
        // Check if permission was granted and refresh device info
        // Also automatically request permission for new devices without permission
        detectedDevices.forEach { device ->
            val deviceKey = Pair(device.vendorId, device.productId)
            if (permissionManager.hasPermission(device)) {
                // Permission granted, refresh device info to get serial number etc.
                deviceDetector.refreshDeviceInfo(device.vendorId, device.productId)
                
                // Show action dialog if permission was just granted (not previously shown)
                if (!previouslyGrantedDevices.contains(deviceKey)) {
                    Timber.d("Permission granted for new device, showing action dialog")
                    previouslyGrantedDevices = previouslyGrantedDevices + deviceKey
                    actionDialogDevice = device
                }
            } else {
                // No permission yet - automatically request permission for new devices
                // Only request if not already requested and activity is available
                if (!previouslyGrantedDevices.contains(deviceKey) && 
                    permissionRequest == null && 
                    activityInstance != null &&
                    currentPermissionDevice == null) {
                    Timber.d("New device detected without permission, automatically requesting permission")
                    permissionRequest = device
                    scope.launch {
                        try {
                            permissionFlow = permissionManager.requestPermission(device, activityInstance)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to request USB permission automatically")
                            permissionRequest = null
                        }
                    }
                }
            }
        }
    }
    
    // Initiale Enumeration
    LaunchedEffect(Unit) {
        Timber.d("Starting initial USB device enumeration...")
        isLoading = true
        try {
            when (val result = usbProvider.enumerateDevices()) {
                is UsbResult.Success -> {
                    devices = result.data
                    Timber.i("Initial enumeration complete: ${devices.size} devices")
                    
                    // Check if any devices already have permission and show action dialog
                    // Only show dialog for the first permitted device to avoid multiple dialogs
                    devices.forEach { device ->
                        val deviceKey = Pair(device.vendorId, device.productId)
                        if (actionDialogDevice == null && permissionManager.hasPermission(device) && !previouslyGrantedDevices.contains(deviceKey)) {
                            Timber.d("Device already has permission, showing action dialog")
                            previouslyGrantedDevices = previouslyGrantedDevices + deviceKey
                            actionDialogDevice = device
                        }
                    }
                }
                is UsbResult.Failure -> {
                    Timber.e(result.error, "Failed to enumerate USB devices")
                    devices = emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during initial enumeration")
            devices = emptyList()
        } finally {
            isLoading = false
        }
    }
    
    // Collect permission result from flow
    LaunchedEffect(permissionFlow) {
        permissionFlow?.let { flow ->
            currentPermissionDevice = permissionRequest
            flow.collect { granted ->
                currentPermissionDevice?.let { device ->
                    if (granted) {
                        Timber.i("USB permission granted for device via system dialog")
                        // Refresh device info to get full details (serial number, etc.)
                        deviceDetector.refreshDeviceInfo(device.vendorId, device.productId)
                        // Show action dialog after permission is granted
                        actionDialogDevice = device
                        // Device will be updated via detectedDevices StateFlow
                    } else {
                        Timber.d("USB permission denied by user")
                    }
                    currentPermissionDevice = null
                    permissionFlow = null
                    permissionRequest = null
                }
            }
        }
    }
    
    // Berechtigungsdialog nach Erkennung
    permissionRequest?.let { device ->
        UsbPermissionDialog(
            device = device,
            onGranted = {
                Timber.d("User clicked Grant, requesting system permission...")
                // Request actual Android system permission
                if (activityInstance != null) {
                    scope.launch {
                        try {
                            permissionFlow = permissionManager.requestPermission(device, activityInstance)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to request USB permission")
                            permissionRequest = null
                        }
                    }
                } else {
                    Timber.e("Cannot request permission: Activity not available")
                    permissionRequest = null
                }
            },
            onDenied = {
                Timber.d("USB permission request cancelled by user")
                permissionRequest = null
            }
        )
    }
    
    // Aktionsmenü nach Berechtigungserteilung
    actionDialogDevice?.let { device ->
        UsbDeviceActionDialog(
            device = device,
            onDismiss = {
                actionDialogDevice = null
            },
            onViewDetails = {
                Timber.d("User selected: View Device Details")
                onDeviceClick(device)
            },
            onOpenDvdCd = {
                Timber.d("User selected: Open DVD/CD Drive")
                // Navigate to DVD/CD detail screen
                onOpenDvdDrive(device)
            }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "USB Devices",
            style = MaterialTheme.typography.headlineMedium
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            UsbDeviceList(
                devices = devices,
                onDeviceClick = { device ->
                    Timber.d("Device clicked: ${device.product}")
                    if (!permissionManager.hasPermission(device)) {
                        permissionRequest = device
                    } else {
                        onDeviceClick(device)
                    }
                }
            )
        }
    }
}
