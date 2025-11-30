package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.network.monitor.InterfaceStats
import com.ble1st.connectias.feature.network.monitor.DeviceBandwidthStats
import com.ble1st.connectias.feature.network.monitor.TrafficPattern
import java.util.Locale

@Composable
fun BandwidthMonitorScreen(
    interfaceStats: List<InterfaceStats>,
    deviceStats: List<DeviceBandwidthStats>,
    trafficPattern: TrafficPattern?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bandwidth Monitor",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        // Traffic Pattern Summary
        trafficPattern?.let { pattern ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Traffic Pattern Analysis",
                        style = MaterialTheme.typography.titleMedium
                    )
                    InfoRow("Average Download", formatBytesPerSecond(pattern.averageRxRate))
                    InfoRow("Average Upload", formatBytesPerSecond(pattern.averageTxRate))
                    InfoRow("Peak Download", formatBytesPerSecond(pattern.peakRxRate))
                    InfoRow("Peak Upload", formatBytesPerSecond(pattern.peakTxRate))
                    InfoRow("Total Data", formatBytes(pattern.totalBytes))
                }
            }
        }

        // Combined list for interface and device stats
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interface Stats
            if (interfaceStats.isNotEmpty()) {
                item {
                    Text(
                        text = "Network Interfaces",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(interfaceStats) { stats ->
                    InterfaceStatsCard(stats)
                }
            }

            // Device Stats
            if (deviceStats.isNotEmpty()) {
                item {
                    Text(
                        text = "Device Bandwidth",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(deviceStats) { stats ->
                    DeviceStatsCard(stats)
                }
            }
        }
    }
}

@Composable
private fun InterfaceStatsCard(stats: InterfaceStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stats.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            InfoRow("Interface", stats.interfaceName)
            InfoRow("Status", if (stats.isActive) "Active" else "Inactive")
            InfoRow("Download Rate", formatBytesPerSecond(stats.rxRate))
            InfoRow("Upload Rate", formatBytesPerSecond(stats.txRate))
        }
    }
}

@Composable
private fun DeviceStatsCard(stats: DeviceBandwidthStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stats.deviceName,
                style = MaterialTheme.typography.titleMedium
            )
            InfoRow("IP Address", stats.deviceId)
            InfoRow("Download Rate", formatBytesPerSecond(stats.estimatedRxRate))
            InfoRow("Upload Rate", formatBytesPerSecond(stats.estimatedTxRate))
            InfoRow("Total Download", formatBytes(stats.estimatedRxBytes))
            InfoRow("Total Upload", formatBytes(stats.estimatedTxBytes))
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

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}

private fun formatBytesPerSecond(bytesPerSecond: Double): String {
    return when {
        bytesPerSecond >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB/s", bytesPerSecond / 1_000_000_000.0)
        bytesPerSecond >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB/s", bytesPerSecond / 1_000_000.0)
        bytesPerSecond >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB/s", bytesPerSecond / 1_000.0)
        else -> String.format(Locale.getDefault(), "%.0f B/s", bytesPerSecond)
    }
}
