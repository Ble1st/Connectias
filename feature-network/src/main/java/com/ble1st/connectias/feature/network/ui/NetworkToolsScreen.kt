@file:OptIn(ExperimentalMaterial3Api::class)

package com.ble1st.connectias.feature.network.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ble1st.connectias.feature.network.model.HostInfo
import com.ble1st.connectias.feature.network.model.PortRangePreset
import com.ble1st.connectias.feature.network.model.SecurityType
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
    val hasRequestedPermissions = remember { mutableStateOf(false) }

    // Request permissions automatically when WIFI tab is active and permissions are not granted
    LaunchedEffect(state.activeTab) {
        if (state.activeTab == NetworkToolsTab.WIFI && 
            !wifiPermissions.allPermissionsGranted && 
            !hasRequestedPermissions.value &&
            !wifiPermissions.shouldShowRationale) {
            // Automatically request permissions on first use
            wifiPermissions.launchMultiplePermissionRequest()
            hasRequestedPermissions.value = true
        }
    }

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
        onSslTargetChanged = viewModel::updateSslTarget,
        onStartSslScan = { viewModel.startSslScan() },
        onClearSsl = { viewModel.clearSslReport() },
        onStartSpeedTest = { viewModel.startSpeedTest() }
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
    onSslTargetChanged: (String) -> Unit,
    onStartSslScan: () -> Unit,
    onClearSsl: () -> Unit,
    onStartSpeedTest: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Network Toolkit",
                        style = MaterialTheme.typography.displaySmall
                    )
                },
                actions = {
                    IconButton(onClick = onRefreshWifi) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = state.activeTab.ordinal,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                NetworkToolsTab.entries.forEach { tab ->
                    Tab(
                        selected = state.activeTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(getTabLabel(tab)) },
                        icon = { Icon(getTabIcon(tab), contentDescription = null) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                AnimatedContent(
                    targetState = state.activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    }
                ) { targetTab ->
                    when (targetTab) {
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
                            onStartScan = onStartPortScan
                        )
                        NetworkToolsTab.SSL -> SslTab(
                            state = state.sslState,
                            onTargetChanged = onSslTargetChanged,
                            onStartScan = onStartSslScan,
                            onClear = onClearSsl
                        )
                        NetworkToolsTab.SPEED_TEST -> SpeedTestTab(
                            state = state.speedTestState,
                            onStartTest = onStartSpeedTest
                        )
                    }
                }
            }
        }
    }
}

private fun getTabLabel(tab: NetworkToolsTab): String = when (tab) {
    NetworkToolsTab.WIFI -> "WLAN"
    NetworkToolsTab.LAN -> "Scanner"
    NetworkToolsTab.PORTS -> "Ports"
    NetworkToolsTab.SSL -> "SSL"
    NetworkToolsTab.SPEED_TEST -> "Speed"
}

private fun getTabIcon(tab: NetworkToolsTab): ImageVector = when (tab) {
    NetworkToolsTab.WIFI -> Icons.Default.Wifi
    NetworkToolsTab.LAN -> Icons.Default.Devices
    NetworkToolsTab.PORTS -> Icons.Default.NetworkCheck
    NetworkToolsTab.SSL -> Icons.Default.Lock
    NetworkToolsTab.SPEED_TEST -> Icons.Default.Speed
}

@Composable
private fun SpeedTestTab(
    state: SpeedTestUiState,
    onStartTest: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(top = 32.dp)
    ) {
        // Speed Display Circle
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(240.dp),
                strokeWidth = 16.dp,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.1f", state.downloadSpeedMbps),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mbps",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (state.isRunning) {
            Text("Testing Download Speed...", style = MaterialTheme.typography.bodyLarge)
        } else if (state.isFinished) {
            Text("Test Completed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        } else if (state.error != null) {
            Text("Error: ${state.error}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartTest,
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)
        ) {
            Icon(Icons.Default.Speed, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isRunning) "Running..." else "Start Test")
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
            onRequestPermissions = onRequestPermissions
        )
        return
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (state.networks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No networks found", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onRefresh) { Text("Scan Again") }
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.networks) { network ->
                WifiCard(network = network)
            }
        }
    }
}

@Composable
private fun WifiCard(network: WifiNetwork) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (network.security != SecurityType.OPEN) Icons.Default.Lock else Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (network.ssid.isNullOrBlank()) "<Hidden SSID>" else network.ssid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = network.bssid ?: "Unknown BSSID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${rssiToQuality(network.rssi)}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipInfo(label = securityLabel(network.security))
                ChipInfo(label = "${network.frequency} MHz")
                ChipInfo(label = "CH ${network.channel ?: "?"}")
            }
        }
    }
}

@Composable
private fun ChipInfo(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun LanTab(
    state: LanScanUiState,
    onScan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = onScan
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Local Network")
            }
        }
        
        if (state.isScanning) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Text("Scanning network... ${(state.progress * 100).toInt()}%")
        }
        
        state.environment?.let { env ->
             Text("Network: ${env.cidr} via ${env.interfaceName}", style = MaterialTheme.typography.labelMedium)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.hosts) { host ->
                HostCard(host)
            }
        }
    }
}

@Composable
private fun HostCard(host: HostInfo) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (host.isReachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = if (host.isReachable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(host.hostname ?: host.ip, style = MaterialTheme.typography.titleMedium)
                if (host.hostname != null) {
                    Text(host.ip, style = MaterialTheme.typography.bodySmall)
                }
                host.mac?.let { 
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

// ... Port and SSL Tabs adapted similarly with cleaner UI ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortTab(
    state: PortScanUiState,
    onTargetChanged: (String) -> Unit,
    onStartScan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.target,
            onValueChange = onTargetChanged,
            label = { Text("Target IP / Host") },
            modifier = Modifier.fillMaxWidth()
        )
        
        FilledTonalButton(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
            Text("Start Port Scan")
        }

        if (state.isScanning) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        
        LazyColumn {
            items(state.results) { result ->
                Text("Port ${result.port}: OPEN (${result.service})", modifier = Modifier.padding(8.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.target,
            onValueChange = onTargetChanged,
            label = { Text("Domain (e.g., google.com)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onStartScan, modifier = Modifier.weight(1f)) {
                Text("Scan SSL")
            }
            if (state.report != null) {
                Button(onClick = onClear, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("Clear")
                }
            }
        }

        if (state.isRunning) CircularProgressIndicator()
        
        state.report?.let { report ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (report.isValidNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (report.isValidNow) "VALID CERTIFICATE" else "INVALID CERTIFICATE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Issued to: ${report.subject}")
                    Text("Issued by: ${report.issuer}")
                    Text("Expires: ${report.validTo}")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermissions: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Permissions Required", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Location/Wi-Fi permissions are needed to scan networks.", color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text("Grant Permissions")
            }
        }
    }
}

private fun wifiPermissions(): List<String> {
    return emptyList()
}

@Suppress("DEPRECATION")
private fun rssiToQuality(rssi: Int): Int {
    val value = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 100)
    return value.coerceIn(0, 100)
}

private fun securityLabel(type: SecurityType): String = type.name
