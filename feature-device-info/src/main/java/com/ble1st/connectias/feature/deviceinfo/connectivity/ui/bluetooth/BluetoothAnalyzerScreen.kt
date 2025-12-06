package com.ble1st.connectias.feature.deviceinfo.connectivity.ui.bluetooth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.BeaconInfo
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.BeaconType
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.BluetoothDeviceInfo
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.BluetoothDeviceType
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.BondState
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.ScanMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bluetooth Analyzer screen for scanning and analyzing Bluetooth devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothAnalyzerScreen(
    viewModel: BluetoothAnalyzerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeviceDetails by remember { mutableStateOf(false) }
    val deviceDetailsSheetState = rememberModalBottomSheetState()

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearSnackbarMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Analyzer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isScanning) {
                        IconButton(onClick = { viewModel.stopScan() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Scan")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !uiState.isBluetoothAvailable -> {
                    BluetoothNotAvailableState()
                }
                !uiState.isBluetoothEnabled -> {
                    BluetoothDisabledState(onRefresh = { viewModel.refreshBluetoothState() })
                }
                else -> {
                    BluetoothScannerContent(
                        isScanning = isScanning,
                        scanMode = uiState.scanMode,
                        devices = if (uiState.showOnlyConnectable) {
                            devices.filter { it.isConnectable }
                        } else {
                            devices
                        },
                        pairedDevices = uiState.pairedDevices,
                        beacons = uiState.beacons,
                        connectedDevice = connectedDevice,
                        isConnecting = uiState.isConnecting,
                        connectingAddress = uiState.connectingDeviceAddress,
                        showOnlyConnectable = uiState.showOnlyConnectable,
                        onStartScan = { viewModel.startScan(it) },
                        onStopScan = { viewModel.stopScan() },
                        onScanModeChange = { viewModel.setScanMode(it) },
                        onToggleConnectable = { viewModel.toggleConnectableFilter() },
                        onDeviceClick = { device ->
                            viewModel.selectDevice(device)
                            showDeviceDetails = true
                        },
                        onConnect = { viewModel.connectToDevice(it) },
                        onDisconnect = { viewModel.disconnect() },
                        getEstimatedDistance = { rssi, txPower ->
                            viewModel.getEstimatedDistance(rssi, txPower)
                        }
                    )
                }
            }
        }

        // Device details sheet
        if (showDeviceDetails && uiState.selectedDevice != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showDeviceDetails = false
                    viewModel.selectDevice(null)
                },
                sheetState = deviceDetailsSheetState
            ) {
                DeviceDetailsSheet(
                    device = uiState.selectedDevice!!,
                    connectedDevice = connectedDevice,
                    isConnecting = uiState.isConnecting,
                    onConnect = { viewModel.connectToDevice(it) },
                    onDisconnect = { viewModel.disconnect() },
                    getEstimatedDistance = { rssi, txPower ->
                        viewModel.getEstimatedDistance(rssi, txPower)
                    }
                )
            }
        }
    }
}

@Composable
private fun BluetoothNotAvailableState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Bluetooth Not Available",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This device does not support Bluetooth",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BluetoothDisabledState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Bluetooth is Disabled",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Please enable Bluetooth in your device settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = onRefresh) {
            Text("Check Again")
        }
    }
}

@Composable
private fun BluetoothScannerContent(
    isScanning: Boolean,
    scanMode: ScanMode,
    devices: List<BluetoothDeviceInfo>,
    pairedDevices: List<BluetoothDeviceInfo>,
    beacons: List<BeaconInfo>,
    connectedDevice: BluetoothDeviceInfo?,
    isConnecting: Boolean,
    connectingAddress: String?,
    showOnlyConnectable: Boolean,
    onStartScan: (ScanMode) -> Unit,
    onStopScan: () -> Unit,
    onScanModeChange: (ScanMode) -> Unit,
    onToggleConnectable: () -> Unit,
    onDeviceClick: (BluetoothDeviceInfo) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    getEstimatedDistance: (Int, Int) -> Double
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scan controls
        item {
            ScanControlsCard(
                isScanning = isScanning,
                scanMode = scanMode,
                showOnlyConnectable = showOnlyConnectable,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onScanModeChange = onScanModeChange,
                onToggleConnectable = onToggleConnectable
            )
        }

        // Connected device
        if (connectedDevice != null) {
            item {
                ConnectedDeviceCard(
                    device = connectedDevice,
                    onDisconnect = onDisconnect
                )
            }
        }

        // Beacons section
        if (beacons.isNotEmpty()) {
            item {
                Text(
                    text = "Beacons (${beacons.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(beacons) { beacon ->
                        BeaconCard(
                            beacon = beacon,
                            distance = getEstimatedDistance(beacon.rssi, -59)
                        )
                    }
                }
            }
        }

        // Discovered devices
        if (devices.isNotEmpty() || isScanning) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Devices (${devices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            items(
                items = devices.sortedByDescending { it.rssi ?: Int.MIN_VALUE },
                key = { it.address }
            ) { device ->
                DeviceCard(
                    device = device,
                    isConnecting = isConnecting && connectingAddress == device.address,
                    distance = device.rssi?.let { rssi ->
                        getEstimatedDistance(rssi, device.txPower ?: -59)
                    },
                    onClick = { onDeviceClick(device) },
                    onConnect = { onConnect(device.address) }
                )
            }
        }

        // Paired devices
        if (pairedDevices.isNotEmpty() && !isScanning) {
            item {
                Text(
                    text = "Paired Devices (${pairedDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(pairedDevices, key = { "paired_${it.address}" }) { device ->
                PairedDeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) }
                )
            }
        }

        // Empty state
        if (devices.isEmpty() && !isScanning) {
            item {
                EmptyScanState(onStartScan = { onStartScan(scanMode) })
            }
        }
    }
}

@Composable
private fun ScanControlsCard(
    isScanning: Boolean,
    scanMode: ScanMode,
    showOnlyConnectable: Boolean,
    onStartScan: (ScanMode) -> Unit,
    onStopScan: () -> Unit,
    onScanModeChange: (ScanMode) -> Unit,
    onToggleConnectable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BLE Scanner",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isScanning) "Scanning..." else "Ready to scan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { if (isScanning) onStopScan() else onStartScan(scanMode) }
                ) {
                    Icon(
                        if (isScanning) Icons.Default.Stop else Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "Stop" else "Scan")
                }
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scan mode chips
            Text(
                text = "Scan Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanMode.entries.forEach { mode ->
                    FilterChip(
                        onClick = { onScanModeChange(mode) },
                        label = { 
                            Text(
                                when (mode) {
                                    ScanMode.LOW_POWER -> "Low Power"
                                    ScanMode.BALANCED -> "Balanced"
                                    ScanMode.LOW_LATENCY -> "Fast"
                                }
                            ) 
                        },
                        selected = scanMode == mode,
                        enabled = !isScanning
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilterChip(
                onClick = onToggleConnectable,
                label = { Text("Connectable only") },
                selected = showOnlyConnectable
            )
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: BluetoothDeviceInfo,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.BluetoothConnected,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${device.services.size} services discovered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = "Disconnect",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun BeaconCard(
    beacon: BeaconInfo,
    distance: Double
) {
    Card(
        modifier = Modifier.width(180.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Radar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = beacon.type.name.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (beacon.type) {
                BeaconType.IBEACON -> {
                    Text(
                        text = "Major: ${beacon.major}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Minor: ${beacon.minor}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                BeaconType.EDDYSTONE_UID -> {
                    Text(
                        text = "NS: ${beacon.namespace?.take(8)}...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${beacon.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "~${String.format("%.1f", distance)}m",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BluetoothDeviceInfo,
    isConnecting: Boolean,
    distance: Double?,
    onClick: () -> Unit,
    onConnect: () -> Unit
) {
    val signalStrength = device.rssi?.let { rssi ->
        when {
            rssi > -50 -> "Excellent"
            rssi > -70 -> "Good"
            rssi > -90 -> "Fair"
            else -> "Weak"
        }
    }

    val signalColor = device.rssi?.let { rssi ->
        when {
            rssi > -50 -> Color(0xFF4CAF50)
            rssi > -70 -> Color(0xFF8BC34A)
            rssi > -90 -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        }
    } ?: MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(signalColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = signalColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    device.rssi?.let { rssi ->
                        Text(
                            text = "$rssi dBm • $signalStrength",
                            style = MaterialTheme.typography.labelSmall,
                            color = signalColor
                        )
                    }
                    distance?.let { d ->
                        Text(
                            text = "~${String.format("%.1f", d)}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (device.isConnectable) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onConnect) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "Connect",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairedDeviceCard(
    device: BluetoothDeviceInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${device.deviceType} • Paired",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyScanState(onStartScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Filled.BluetoothSearching,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No devices found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start scanning to discover nearby Bluetooth devices",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onStartScan) {
            Text("Start Scan")
        }
    }
}

@Composable
private fun DeviceDetailsSheet(
    device: BluetoothDeviceInfo,
    connectedDevice: BluetoothDeviceInfo?,
    isConnecting: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    getEstimatedDistance: (Int, Int) -> Double
) {
    val isConnected = connectedDevice?.address == device.address

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Device info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DeviceInfoRow("Type", device.deviceType.name)
                DeviceInfoRow("Bond State", device.bondState.name)
                DeviceInfoRow("Connectable", if (device.isConnectable) "Yes" else "No")
                device.rssi?.let { rssi ->
                    DeviceInfoRow("Signal", "$rssi dBm")
                    val distance = getEstimatedDistance(rssi, device.txPower ?: -59)
                    DeviceInfoRow("Est. Distance", "${String.format("%.2f", distance)}m")
                }
                device.txPower?.let { tx ->
                    DeviceInfoRow("TX Power", "$tx dBm")
                }
            }
        }

        // Services (if connected)
        if (isConnected && device.services.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Services (${device.services.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            device.services.forEach { service ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = service.name ?: "Unknown Service",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = service.uuid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${service.characteristics.size} characteristics",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action button
        if (device.isConnectable) {
            Button(
                onClick = {
                    if (isConnected) onDisconnect() else onConnect(device.address)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isConnected) "Disconnect" else "Connect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

