package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.models.DiscType
import com.ble1st.connectias.feature.usb.models.OpticalDrive

@Composable
fun DiscInfoCard(
    drive: OpticalDrive,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Disc Information",
                style = MaterialTheme.typography.titleMedium
            )
            
            InfoRow("Type", drive.type.name)
            InfoRow("File System", drive.fileSystem.name)
            InfoRow("Mount Point", drive.mountPoint)
            InfoRow("Vendor ID", "0x%04X".format(drive.device.vendorId))
            InfoRow("Product ID", "0x%04X".format(drive.device.productId))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
