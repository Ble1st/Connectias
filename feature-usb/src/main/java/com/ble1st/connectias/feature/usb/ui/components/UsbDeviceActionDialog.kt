package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            Text("USB Device Connected")
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
                    text = "Available Actions:",
                    style = MaterialTheme.typography.labelLarge
                )
                
                // Always show "View Details"
                ActionButton(
                    icon = Icons.Default.Info,
                    text = "View Device Details",
                    onClick = {
                        onViewDetails()
                        onDismiss()
                    }
                )
                
                // Show DVD/CD option for mass storage devices
                if (device.isMassStorage) {
                    ActionButton(
                        icon = Icons.Default.PlayArrow,
                        text = "Open DVD/CD Drive",
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
                Text("Close")
            }
        }
    )
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(text)
        }
    }
}
