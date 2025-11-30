package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.spacedBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.R
import com.ble1st.connectias.feature.usb.models.UsbDevice

/**
 * Action dialog that appears after USB permission is granted.
 * Shows available actions based on device type.
 */
@Composable
fun UsbDeviceActionDialog(
    device: UsbDevice,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
    onOpenDvdCd: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.usb_device_connected))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Device info
                Text(
                    text = device.product ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = device.manufacturer ?: "Unknown Manufacturer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Divider()
                
                // Available actions
                Text(
                    text = stringResource(R.string.available_actions),
                    style = MaterialTheme.typography.labelLarge
                )
                
                // Always show "View Details"
                ActionButton(
                    icon = Icons.Default.Info,
                    text = stringResource(R.string.view_device_details),
                    onClick = {
                        onViewDetails()
                        onDismiss()
                    }
                )
                
                // Show DVD/CD option for mass storage devices
                if (device.isMassStorage) {
                    ActionButton(
                        icon = Icons.Default.PlayArrow,
                        text = stringResource(R.string.open_dvd_drive),
                        onClick = {
                            onOpenDvdCd()
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "",
                modifier = Modifier.size(20.dp)
            )
            Text(text)
        }
    }
}
