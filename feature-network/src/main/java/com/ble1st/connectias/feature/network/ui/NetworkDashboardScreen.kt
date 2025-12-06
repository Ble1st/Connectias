package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.LocalAppStrings
import com.ble1st.connectias.feature.network.models.NetworkAnalysis
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.WifiNetwork

@Composable
fun NetworkDashboardScreen(
    state: NetworkState,
    onRefreshWifi: () -> Unit,
    onRefreshLan: () -> Unit,
    onRefreshAnalysis: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is NetworkState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is NetworkState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${LocalAppStrings.current.alertError}: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = {
                        onRefreshWifi()
                        onRefreshLan()
                        onRefreshAnalysis()
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text(LocalAppStrings.current.retryAll)
                    }
                }
            }
            is NetworkState.NetworkDataHolder -> {
                NetworkDashboardContent(
                    state = state,
                    onRefreshWifi = onRefreshWifi,
                    onRefreshLan = onRefreshLan,
                    onRefreshAnalysis = onRefreshAnalysis
                )
            }
        }
    }
}

@Composable
private fun NetworkDashboardContent(
    state: NetworkState.NetworkDataHolder,
    onRefreshWifi: () -> Unit,
    onRefreshLan: () -> Unit,
    onRefreshAnalysis: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = LocalAppStrings.current.navNetworkDashboard,
                style = MaterialTheme.typography.headlineMedium
            )
        }

        if (state is NetworkState.PartialSuccess) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = LocalAppStrings.current.networkAnalysisTitle,
                onRefresh = onRefreshAnalysis
            )
            NetworkAnalysisCard(state.analysis)
        }

        item {
            SectionHeader(
                title = LocalAppStrings.current.wifiNetworksTitle,
                onRefresh = onRefreshWifi
            )
            if (state.wifiNetworks.isEmpty()) {
                EmptyStateText(LocalAppStrings.current.noWifiNetworks)
            }
        }
        items(state.wifiNetworks) { network ->
            WifiNetworkItem(network)
        }

        item {
            SectionHeader(
                title = LocalAppStrings.current.lanDevicesTitle,
                onRefresh = onRefreshLan
            )
            if (state.devices.isEmpty()) {
                EmptyStateText(LocalAppStrings.current.noLanDevices)
            }
        }
        items(state.devices) { device ->
            NetworkDeviceItem(device)
        }
        
        item {
             Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh $title")
        }
    }
}

@Composable
private fun NetworkAnalysisCard(analysis: NetworkAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(
                LocalAppStrings.current.connectionType,
                analysis.connectionType.name
            )
            InfoRow(
                LocalAppStrings.current.connected,
                if (analysis.isConnected) LocalAppStrings.current.yes else LocalAppStrings.current.no
            )
            InfoRow(
                LocalAppStrings.current.gateway,
                analysis.gateway ?: LocalAppStrings.current.notAvailable
            )
            InfoRow(
                LocalAppStrings.current.dnsServers,
                if (analysis.dnsServers.isEmpty()) LocalAppStrings.current.notAvailable else analysis.dnsServers.joinToString(", ")
            )
        }
    }
}

@Composable
private fun WifiNetworkItem(network: WifiNetwork) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = network.ssid, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${network.signalStrength} dBm • ${network.encryptionType.displayName}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NetworkDeviceItem(device: NetworkDevice) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = device.hostname, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${device.ipAddress} • ${device.deviceType.name}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EmptyStateText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}