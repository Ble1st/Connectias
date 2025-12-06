package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.network.R
import com.ble1st.connectias.feature.network.monitor.NetworkTraffic
import java.util.Locale

@Composable
fun NetworkMonitorScreen(
    traffic: NetworkTraffic,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = getThemedString(stringResource(R.string.network_monitor_title)),
            style = MaterialTheme.typography.headlineMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(getThemedString(stringResource(R.string.connection_type)), style = MaterialTheme.typography.labelMedium)
                Text(
                    traffic.connectionType.name,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrafficCard(
                modifier = Modifier.weight(1f),
                title = getThemedString(stringResource(R.string.download_rate)),
                value = "${formatBytesPerSecond(traffic.rxRate)}/s",
                total = formatBytes(traffic.rxBytes)
            )
            TrafficCard(
                modifier = Modifier.weight(1f),
                title = getThemedString(stringResource(R.string.upload_rate)),
                value = "${formatBytesPerSecond(traffic.txRate)}/s",
                total = formatBytes(traffic.txBytes)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(getThemedString(stringResource(R.string.total_data_transferred)), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    formatBytes(traffic.rxBytes + traffic.txBytes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(getThemedString(stringResource(R.string.refresh_metrics)))
        }
    }
}

@Composable
private fun TrafficCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    total: String
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(getThemedString(stringResource(R.string.total, total)), style = MaterialTheme.typography.bodySmall)
        }
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
        bytesPerSecond >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytesPerSecond / 1_000_000_000.0)
        bytesPerSecond >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB", bytesPerSecond / 1_000_000.0)
        bytesPerSecond >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB", bytesPerSecond / 1_000.0)
        else -> String.format(Locale.getDefault(), "%.0f B", bytesPerSecond)
    }
}
