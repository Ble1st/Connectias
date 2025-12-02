package com.ble1st.connectias.feature.deviceinfo.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.deviceinfo.provider.DeviceInfo
import java.util.Locale

@Composable
fun DeviceInfoScreen(
    state: DeviceInfoState,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is DeviceInfoState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is DeviceInfoState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Retry")
                    }
                }
            }
            is DeviceInfoState.Success -> {
                DeviceInfoContent(
                    info = state.info,
                    onRefresh = onRefresh
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoContent(
    info: DeviceInfo,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Device Information",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
             Text("Refresh Info")
        }

        // OS Info
        InfoCard(title = "Operating System") {
            InfoRow("Version", info.osInfo.version)
            InfoRow("SDK", info.osInfo.sdkVersion.toString())
            InfoRow("Manufacturer", info.osInfo.manufacturer)
            InfoRow("Model", info.osInfo.model)
        }

        // CPU Info
        InfoCard(title = "CPU") {
            InfoRow("Cores", info.cpuInfo.cores.toString())
            InfoRow("Architecture", info.cpuInfo.architecture)
            InfoRow("Frequency", "${info.cpuInfo.frequency} MHz")
        }

        // RAM Info
        InfoCard(title = "RAM") {
            InfoRow("Used", Formatter.formatShortFileSize(context, info.ramInfo.used))
            InfoRow("Total", Formatter.formatShortFileSize(context, info.ramInfo.total))
            LinearProgressIndicator(
                progress = { (info.ramInfo.percentageUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                text = "${String.format(Locale.getDefault(), "%.1f", info.ramInfo.percentageUsed)}% Used",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }

        // Storage Info
        InfoCard(title = "Storage") {
            InfoRow("Used", Formatter.formatShortFileSize(context, info.storageInfo.used))
            InfoRow("Total", Formatter.formatShortFileSize(context, info.storageInfo.total))
            LinearProgressIndicator(
                progress = { (info.storageInfo.percentageUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
             Text(
                text = "${String.format(Locale.getDefault(), "%.1f", info.storageInfo.percentageUsed)}% Used",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }

        // Network Info
        InfoCard(title = "Network Identity") {
            InfoRow("IP Address", info.networkInfo.ipAddress ?: "Not available")
            InfoRow("Android ID", info.networkInfo.androidId ?: "Not available")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToolButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
