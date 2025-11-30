package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.models.UsbDevice
import timber.log.Timber

@Composable
fun UsbPermissionDialog(
    device: UsbDevice,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = {
            Timber.d("USB permission dialog dismissed")
            onDenied()
        },
        title = {
            Text("USB Permission Required")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("The app needs permission to access this USB device:")
                Text(
                    text = device.product ?: "Unknown Device",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Vendor: 0x%04X, Product: 0x%04X".format(device.vendorId, device.productId),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Timber.i("User granted USB permission")
                onGranted()
            }) {
                Text("Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                Timber.d("User denied USB permission")
                onDenied()
            }) {
                Text("Deny")
            }
        }
    )
}
