package com.ble1st.connectias.feature.dvd.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.dvd.R
import com.ble1st.connectias.feature.dvd.models.OpticalDrive

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
                text = stringResource(R.string.disc_information_title),
                style = MaterialTheme.typography.titleMedium
            )
            
            InfoRow(stringResource(R.string.disc_info_type), drive.type.displayName)
            InfoRow(stringResource(R.string.disc_info_file_system), drive.fileSystem.displayName)
            
            // Show mount point if available, otherwise show device path
            val accessPath = drive.mountPoint ?: drive.devicePath
            val accessLabel = if (drive.mountPoint != null) {
                stringResource(R.string.disc_info_mount_point)
            } else {
                "Device Path"
            }
            if (accessPath != null) {
                InfoRow(accessLabel, accessPath)
            }
            
            InfoRow(stringResource(R.string.disc_info_vendor_id), "0x%04X".format(drive.device.vendorId))
            InfoRow(stringResource(R.string.disc_info_product_id), "0x%04X".format(drive.device.productId))
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
