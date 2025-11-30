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
import com.ble1st.connectias.feature.network.models.NetworkAnalysis
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.WifiNetwork
import com.ble1st.connectias.feature.network.ui.components.DashboardCategory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun NetworkDashboardScreen(
    state: NetworkState,
    onRefreshWifi: () -> Unit,
    onRefreshLan: () -> Unit,
    onRefreshAnalysis: () -> Unit,
    onNavigateToPortScanner: () -> Unit,
    onNavigateToDnsLookup: () -> Unit,
    onNavigateToNetworkMonitor: () -> Unit,
    onNavigateToWifiAnalyzer: () -> Unit = {},
    // Analysis features
    onNavigateToMacAnalyzer: () -> Unit = {},
    onNavigateToSubnetAnalyzer: () -> Unit = {},
    onNavigateToVlanAnalyzer: () -> Unit = {},
    // Topology
    onNavigateToTopology: () -> Unit = {},
    // Monitoring features
    onNavigateToBandwidthMonitor: () -> Unit = {},
    onNavigateToFlowAnalyzer: () -> Unit = {},
    // Detection features
    onNavigateToDhcpLease: () -> Unit = {},
    onNavigateToHypervisorDetector: () -> Unit = {}
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
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = {
                        onRefreshWifi()
                        onRefreshLan()
                        onRefreshAnalysis()
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Retry All")
                    }
                }
            }
            is NetworkState.NetworkDataHolder -> {
                NetworkDashboardContent(
                    state = state,
                    onRefreshWifi = onRefreshWifi,
                    onRefreshLan = onRefreshLan,
                    onRefreshAnalysis = onRefreshAnalysis,
                    onNavigateToPortScanner = onNavigateToPortScanner,
                    onNavigateToDnsLookup = onNavigateToDnsLookup,
                    onNavigateToNetworkMonitor = onNavigateToNetworkMonitor,
                    onNavigateToWifiAnalyzer = onNavigateToWifiAnalyzer,
                    onNavigateToMacAnalyzer = onNavigateToMacAnalyzer,
                    onNavigateToSubnetAnalyzer = onNavigateToSubnetAnalyzer,
                    onNavigateToVlanAnalyzer = onNavigateToVlanAnalyzer,
                    onNavigateToTopology = onNavigateToTopology,
                    onNavigateToBandwidthMonitor = onNavigateToBandwidthMonitor,
                    onNavigateToFlowAnalyzer = onNavigateToFlowAnalyzer,
                    onNavigateToDhcpLease = onNavigateToDhcpLease,
                    onNavigateToHypervisorDetector = onNavigateToHypervisorDetector
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
    onRefreshAnalysis: () -> Unit,
    onNavigateToPortScanner: () -> Unit,
    onNavigateToDnsLookup: () -> Unit,
    onNavigateToNetworkMonitor: () -> Unit,
    onNavigateToWifiAnalyzer: () -> Unit,
    onNavigateToMacAnalyzer: () -> Unit,
    onNavigateToSubnetAnalyzer: () -> Unit,
    onNavigateToVlanAnalyzer: () -> Unit,
    onNavigateToTopology: () -> Unit,
    onNavigateToBandwidthMonitor: () -> Unit,
    onNavigateToFlowAnalyzer: () -> Unit,
    onNavigateToDhcpLease: () -> Unit,
    onNavigateToHypervisorDetector: () -> Unit
) {
    var analysisExpanded by remember { mutableStateOf(false) }
    var topologyExpanded by remember { mutableStateOf(false) }
    var monitoringExpanded by remember { mutableStateOf(false) }
    var toolsExpanded by remember { mutableStateOf(true) }
    var detectionExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Network Dashboard",
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
                title = "Network Analysis",
                onRefresh = onRefreshAnalysis
            )
            NetworkAnalysisCard(state.analysis)
        }

        item {
            SectionHeader(
                title = "Wi-Fi Networks",
                onRefresh = onRefreshWifi
            )
            if (state.wifiNetworks.isEmpty()) {
                EmptyStateText("No Wi-Fi networks found.")
            }
        }
        items(state.wifiNetworks) { network ->
            WifiNetworkItem(network)
        }

        item {
            SectionHeader(
                title = "LAN Devices",
                onRefresh = onRefreshLan
            )
            if (state.devices.isEmpty()) {
                EmptyStateText("No devices found on local network.")
            }
        }
        items(state.devices) { device ->
            NetworkDeviceItem(device)
        }

        // Analysis Category
        item {
            DashboardCategory(
                title = "Analysis",
                icon = Icons.Default.Info,
                isExpanded = analysisExpanded,
                onExpandedChange = { analysisExpanded = it }
            ) {
                ToolButton("MAC Address Analyzer", Icons.Default.Fingerprint, onNavigateToMacAnalyzer)
                ToolButton("Subnet Analyzer", Icons.Default.NetworkCheck, onNavigateToSubnetAnalyzer)
                ToolButton("VLAN Analyzer", Icons.Default.Layers, onNavigateToVlanAnalyzer)
            }
        }

        // Topology Category
        item {
            DashboardCategory(
                title = "Topology",
                icon = Icons.Default.Share,
                isExpanded = topologyExpanded,
                onExpandedChange = { topologyExpanded = it }
            ) {
                ToolButton("Network Topology", Icons.Default.Share, onNavigateToTopology)
            }
        }

        // Monitoring Category
        item {
            DashboardCategory(
                title = "Monitoring",
                icon = Icons.Default.Timeline,
                isExpanded = monitoringExpanded,
                onExpandedChange = { monitoringExpanded = it }
            ) {
                ToolButton("Network Monitor", Icons.Default.Timeline, onNavigateToNetworkMonitor)
                ToolButton("Bandwidth Monitor", Icons.Default.Timeline, onNavigateToBandwidthMonitor)
                ToolButton("Flow Analyzer", Icons.Default.Timeline, onNavigateToFlowAnalyzer)
            }
        }

        // Tools Category
        item {
            DashboardCategory(
                title = "Tools",
                icon = Icons.Default.Settings,
                isExpanded = toolsExpanded,
                onExpandedChange = { toolsExpanded = it }
            ) {
                ToolButton("Port Scanner", Icons.Default.Search, onNavigateToPortScanner)
                ToolButton("DNS Lookup", Icons.Default.Info, onNavigateToDnsLookup)
                ToolButton("Wi-Fi Analyzer", Icons.Default.SignalWifi4Bar, onNavigateToWifiAnalyzer)
            }
        }

        // Detection Category
        item {
            DashboardCategory(
                title = "Detection",
                icon = Icons.Default.Search,
                isExpanded = detectionExpanded,
                onExpandedChange = { detectionExpanded = it }
            ) {
                ToolButton("DHCP Lease Viewer", Icons.Default.Info, onNavigateToDhcpLease)
                ToolButton("Hypervisor Detector", Icons.Default.Info, onNavigateToHypervisorDetector)
            }
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
            InfoRow("Connection Type", analysis.connectionType.name)
            InfoRow("Connected", if (analysis.isConnected) "Yes" else "No")
            InfoRow("Gateway", analysis.gateway ?: "N/A")
            InfoRow("DNS Servers", if (analysis.dnsServers.isEmpty()) "N/A" else analysis.dnsServers.joinToString(", "))
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
