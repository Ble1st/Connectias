package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.R
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
        modifier = modifier,
        onDismissRequest = {
            Timber.d("USB permission dialog dismissed")
            onDenied()
        },
        title = {
            Text(stringResource(R.string.usb_permission_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.usb_permission_message))
                Text(
                    text = device.product ?: stringResource(R.string.usb_unknown_device),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.usb_vendor_product_format, device.vendorId, device.productId),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Timber.i("User granted USB permission")
                onGranted()
            }) {
                Text(stringResource(R.string.usb_permission_grant))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                Timber.d("User denied USB permission")
                onDenied()
            }) {
                Text(stringResource(R.string.usb_permission_deny))
            }
        }
    )
}
