@file:OptIn(ExperimentalMaterial3Api::class)

package com.ble1st.connectias.feature.network.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.feature.network.model.DeviceType
import com.ble1st.connectias.feature.network.model.HostInfo
import com.ble1st.connectias.feature.network.model.PortPresets
import com.ble1st.connectias.feature.network.model.PortResult
import com.ble1st.connectias.feature.network.model.PortRangePreset
import com.ble1st.connectias.feature.network.model.SecurityType
import com.ble1st.connectias.feature.network.model.SslReport
import com.ble1st.connectias.feature.network.model.TraceHop
import com.ble1st.connectias.feature.network.model.WifiNetwork
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NetworkToolsRoute(
    viewModel: NetworkToolsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val wifiPermissions = rememberMultiplePermissionsState(wifiPermissions())

    LaunchedEffect(wifiPermissions.allPermissionsGranted) {
        viewModel.onWifiPermission(wifiPermissions.allPermissionsGranted)
    }

    LaunchedEffect(state.activeTab, wifiPermissions.allPermissionsGranted) {
        if (state.activeTab == NetworkToolsTab.WIFI && wifiPermissions.allPermissionsGranted) {
            viewModel.refreshWifi()
        }
    }

    NetworkToolsScreen(
        state = state,
        wifiPermissionsState = wifiPermissions,
        onRequestWifiPermissions = { wifiPermissions.launchMultiplePermissionRequest() },
        onRefreshWifi = { viewModel.refreshWifi() },
        onTabSelected = viewModel::setActiveTab,
        onStartNetworkScan = { viewModel.startNetworkScan() },
        onPortTargetChanged = viewModel::updatePortTarget,
        onPortPresetSelected = viewModel::selectPortPreset,
        onCustomPortRangeChanged = viewModel::updateCustomPortRange,
        onStartPortScan = { viewModel.startPortScan() },
        onTracerouteTargetChanged = viewModel::updateTracerouteTarget,
        onStartTraceroute = { viewModel.startTraceroute() },
        onSslTargetChanged = viewModel::updateSslTarget,
        onStartSslScan = { viewModel.startSslScan() },
        onClearSsl = { viewModel.clearSslReport() }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NetworkToolsScreen(
    state: NetworkToolsState,
    wifiPermissionsState: MultiplePermissionsState,
    onRequestWifiPermissions: () -> Unit,
    onRefreshWifi: () -> Unit,
    onTabSelected: (NetworkToolsTab) -> Unit,
    onStartNetworkScan: () -> Unit,
    onPortTargetChanged: (String) -> Unit,
    onPortPresetSelected: (PortRangePreset?) -> Unit,
    onCustomPortRangeChanged: (Int, Int) -> Unit,
    onStartPortScan: () -> Unit,
    onTracerouteTargetChanged: (String) -> Unit,
    onStartTraceroute: () -> Unit,
    onSslTargetChanged: (String) -> Unit,
    onStartSslScan: () -> Unit,
    onClearSsl: () -> Unit
) {
    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Network Toolkit",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    AssistChip(
                        onClick = onRefreshWifi,
                        label = { Text("Refresh") },
                        leadingIcon = {
                            Icon(Icons.Default.WifiTethering, contentDescription = null)
                        }
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabSelector(
                active = state.activeTab,
                onTabSelected = onTabSelected
            )

            when (state.activeTab) {
                NetworkToolsTab.WIFI -> WifiTab(
                    state = state.wifiState,
                    permissionsState = wifiPermissionsState,
                    onRequestPermissions = onRequestWifiPermissions,
                    onRefresh = onRefreshWifi
                )
                NetworkToolsTab.LAN -> LanTab(
                    state = state.lanState,
                    onScan = onStartNetworkScan
                )
                NetworkToolsTab.PORTS -> PortTab(
                    state = state.portState,
                    onTargetChanged = onPortTargetChanged,
                    onPresetSelected = onPortPresetSelected,
                    onCustomRangeChanged = onCustomPortRangeChanged,
                    onStartScan = onStartPortScan
                )
                NetworkToolsTab.TRACEROUTE -> TracerouteTab(
                    state = state.tracerouteState,
                    onTargetChanged = onTracerouteTargetChanged,
                    onStart = onStartTraceroute
                )
                NetworkToolsTab.SSL -> SslTab(
                    state = state.sslState,
                    onTargetChanged = onSslTargetChanged,
                    onStartScan = onStartSslScan,
                    onClear = onClearSsl
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSelector(
    active: NetworkToolsTab,
    onTabSelected: (NetworkToolsTab) -> Unit
) {
    val items = listOf(
        Triple(NetworkToolsTab.WIFI, Icons.Default.Wifi, "WLAN"),
        Triple(NetworkToolsTab.LAN, Icons.Default.Devices, "Netz"),
        Triple(NetworkToolsTab.PORTS, Icons.Default.NetworkCheck, "Ports"),
        Triple(NetworkToolsTab.TRACEROUTE, Icons.Default.Route, "Traceroute"),
        Triple(NetworkToolsTab.SSL, Icons.Default.Lock, "SSL")
    )
    SingleChoiceSegmentedButtonRow {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                selected = active == item.first,
                onClick = { onTabSelected(item.first) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                icon = {
                    Icon(item.second, contentDescription = null)
                },
                label = {
                    Text(item.third)
                }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun WifiTab(
    state: WifiUiState,
    permissionsState: MultiplePermissionsState,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit
) {
    if (!state.permissionGranted || !permissionsState.allPermissionsGranted) {
        PermissionCard(
            onRequestPermissions = onRequestPermissions,
            showRationale = permissionsState.shouldShowRationale
        )
        return
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Text(
                    text = "WLAN Scanner",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.WifiTethering, contentDescription = "Scan")
                }
            }

            if (state.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.height(32.dp)
                    )
                    Text("Scanning...")
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.networks.isEmpty() && !state.isLoading && state.error == null) {
                Text(
                    text = "Keine WLANs gefunden. Bitte erneut scannen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.networks) { network ->
                        WifiCard(network = network)
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiCard(network: WifiNetwork) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (network.ssid.isNullOrBlank()) "<Hidden SSID>" else network.ssid,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "BSSID: ${network.bssid ?: "n/a"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${rssiToQuality(network.rssi)}%") },
                    leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                AssistChip(
                    onClick = {},
                    label = { Text(securityLabel(network.security)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${network.frequency} MHz") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                )
            }
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Channel: ${network.channel ?: "-"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "RSSI: ${network.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "Capabilities: ${network.capabilities}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanTab(
    state: LanScanUiState,
    onScan: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Devices, contentDescription = null)
                Text("Netz-Scanner", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onScan,
                    label = { Text("Scan") },
                    leadingIcon = { Icon(Icons.Default.NetworkCheck, contentDescription = null) }
                )
            }

            state.environment?.let { env ->
                Text(
                    text = "CIDR: ${env.cidr} (${env.interfaceName ?: "n/a"})",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Gateway: ${env.gateway ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Text(
                text = "Netzwerk wird beim Scan automatisch erkannt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.isScanning) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.hosts.isEmpty() && !state.isScanning && state.error == null) {
                Text(
                    text = "Noch keine Hosts gefunden. Starte einen Scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.hosts) { host ->
                        HostCard(host)
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCard(host: HostInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = host.hostname ?: host.ip,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = host.ip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(host.deviceType.toLabel()) },
                    leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) }
                )
                host.pingMs?.let { ping ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${ping}ms") },
                        leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null) }
                    )
                }
                host.mac?.let { mac ->
                    AssistChip(
                        onClick = {},
                        label = { Text(mac) },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                }
            }
            if (!host.isReachable) {
                Text(
                    text = "Host nicht erreichbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun DeviceType.toLabel(): String = when (this) {
    DeviceType.ROUTER -> "Router"
    DeviceType.COMPUTER -> "Computer"
    DeviceType.PHONE -> "Phone"
    DeviceType.PRINTER -> "Printer"
    DeviceType.IOT -> "IoT"
    DeviceType.SERVER -> "Server"
    DeviceType.UNKNOWN -> "Unknown"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortTab(
    state: PortScanUiState,
    onTargetChanged: (String) -> Unit,
    onPresetSelected: (PortRangePreset?) -> Unit,
    onCustomRangeChanged: (Int, Int) -> Unit,
    onStartScan: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null)
                Text("Port Scanner", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onStartScan,
                    label = { Text("Scan") },
                    leadingIcon = { Icon(Icons.Default.Power, contentDescription = null) }
                )
            }

            OutlinedTextField(
                value = state.target,
                onValueChange = onTargetChanged,
                label = { Text("Domain oder IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Portbereich", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow {
                PortPresets.presets.forEachIndexed { index, preset ->
                    SegmentedButton(
                        selected = state.selectedPreset == preset,
                        onClick = { onPresetSelected(preset) },
                        shape = SegmentedButtonDefaults.itemShape(index, PortPresets.presets.size),
                        icon = { Icon(Icons.Default.Power, contentDescription = null) },
                        label = { Text(preset.label) }
                    )
                }
            }

            val isCustom = state.selectedPreset?.start == 0
            if (isCustom) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.customStart.toString(),
                        onValueChange = { value ->
                            val sanitized = value.filter { it.isDigit() }
                            val start = sanitized.toIntOrNull() ?: 0
                            onCustomRangeChanged(start, state.customEnd)
                        },
                        label = { Text("Start") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.customEnd.toString(),
                        onValueChange = { value ->
                            val sanitized = value.filter { it.isDigit() }
                            val end = sanitized.toIntOrNull() ?: 0
                            onCustomRangeChanged(state.customStart, end)
                        },
                        label = { Text("Ende") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            if (state.isScanning) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.results.isEmpty() && !state.isScanning && state.error == null) {
                Text(
                    text = "Keine offenen Ports gefunden oder noch nicht gescannt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.results) { result ->
                        PortResultCard(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortResultCard(result: PortResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Port ${result.port}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(result.service ?: "Unbekannt") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
                result.banner?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(it.take(30)) },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TracerouteTab(
    state: TracerouteUiState,
    onTargetChanged: (String) -> Unit,
    onStart: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Route, contentDescription = null)
                Text("Traceroute", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onStart,
                    label = { Text("Start") },
                    leadingIcon = { Icon(Icons.Default.Power, contentDescription = null) }
                )
            }

            OutlinedTextField(
                value = state.target,
                onValueChange = onTargetChanged,
                label = { Text("Domain oder IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (state.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.hops.isEmpty() && !state.isRunning && state.error == null) {
                Text(
                    text = "Noch keine Hops. Starte eine Route.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.hops) { hop ->
                        TraceHopCard(hop)
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceHopCard(hop: TraceHop) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Hop ${hop.hop}", style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(hop.status.name) },
                    leadingIcon = { Icon(Icons.Default.Route, contentDescription = null) }
                )
            }
            Text(
                text = hop.ip ?: "<unbekannt>",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            hop.host?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            hop.rttMs?.let {
                Text(
                    text = "RTT: ${it}ms",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            hop.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SslTab(
    state: SslScanUiState,
    onTargetChanged: (String) -> Unit,
    onStartScan: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Text("SSL Scanner", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onStartScan,
                    label = { Text("Scan") },
                    leadingIcon = { Icon(Icons.Default.Power, contentDescription = null) }
                )
                AssistChip(
                    onClick = onClear,
                    label = { Text("Reset") }
                )
            }

            OutlinedTextField(
                value = state.target,
                onValueChange = onTargetChanged,
                label = { Text("Domain (z.B. example.com)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (state.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            state.report?.let { report ->
                SslReportCard(report)
            }

            if (state.report == null && !state.isRunning && state.error == null) {
                Text(
                    text = "Gib eine Domain ein und starte den Scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SslReportCard(report: SslReport) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (report.isValidNow) "Zertifikat gültig" else "Zertifikat ungültig",
                style = MaterialTheme.typography.titleMedium,
                color = if (report.isValidNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text("Subject: ${report.subject}", style = MaterialTheme.typography.bodySmall)
            Text("Issuer: ${report.issuer}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Gültig: ${report.validFrom} - ${report.validTo}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Resttage: ${report.daysRemaining}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Key: ${report.keyAlgorithm} (${report.keySize} bit) / Sig: ${report.signatureAlgorithm}",
                style = MaterialTheme.typography.bodySmall
            )
            if (report.problems.isNotEmpty()) {
                Text("Probleme:", style = MaterialTheme.typography.titleSmall)
                report.problems.forEach { problem ->
                    Text(
                        "- $problem",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermissions: () -> Unit,
    showRationale: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WLAN-Berechtigung benötigt",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "Bitte erteile Standort/WLAN-Scan Berechtigungen, um Netzwerke zu sehen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onRequestPermissions,
                    label = { Text("Erteilen") },
                    leadingIcon = { Icon(Icons.Default.Power, contentDescription = null) }
                )
                if (showRationale) {
                    AssistChip(
                        onClick = onRequestPermissions,
                        label = { Text("Nochmal fragen") }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun wifiPermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun rssiToQuality(rssi: Int): Int {
    // WifiManager calculates 0..99, normalize to 0..100
    val value = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 100)
    return value.coerceIn(0, 100)
}

private fun securityLabel(type: SecurityType): String = when (type) {
    SecurityType.OPEN -> "Open"
    SecurityType.WEP -> "WEP"
    SecurityType.WPA -> "WPA"
    SecurityType.WPA2 -> "WPA2"
    SecurityType.WPA3 -> "WPA3"
    SecurityType.UNKNOWN -> "Unknown"
}
