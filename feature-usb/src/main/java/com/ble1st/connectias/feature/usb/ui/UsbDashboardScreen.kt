package com.ble1st.connectias.feature.usb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    usbDevices: List<UsbDevice>,
    selectedDevice: UsbDevice?,
    onDeviceClick: (UsbDevice) -> Unit,
    onDismissDialog: () -> Unit,
    onViewDetails: (UsbDevice) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var devices by remember { mutableStateOf(usbDevices) }
    LaunchedEffect(usbDevices) {
        devices = usbDevices
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Expressive Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "USB Devices",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Searching for devices...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            UsbDeviceList(
                devices = devices,
                onDeviceClick = { device ->
                    Timber.d("Device clicked: ${device.product}")
                    onDeviceClick(device)
                }
            )
        }
    }

    selectedDevice?.let { device ->
        UsbDeviceActionDialog(
            device = device,
            onViewDetails = { onViewDetails(device) },
            onDismiss = onDismissDialog
        )
    }
}
