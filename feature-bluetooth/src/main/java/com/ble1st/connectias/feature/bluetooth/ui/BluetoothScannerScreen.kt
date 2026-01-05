package com.ble1st.connectias.feature.bluetooth.ui
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ble1st.connectias.feature.bluetooth.ui.model.BluetoothUiState
import com.ble1st.connectias.feature.bluetooth.ui.model.PermissionStatus
import com.ble1st.connectias.feature.bluetooth.ui.model.UiDevice
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothScannerRoute(
    viewModel: BluetoothScannerViewModel = hiltViewModel()
) {
    val permissionsState = rememberMultiplePermissionsState(permissions = bluetoothPermissions())
    val state by viewModel.state.collectAsState()

    LaunchedEffect(permissionsState.allPermissionsGranted, permissionsState.shouldShowRationale) {
        val status = when {
            permissionsState.allPermissionsGranted -> PermissionStatus.Granted
            permissionsState.shouldShowRationale -> PermissionStatus.Rationale
            else -> PermissionStatus.DeniedPermanently
        }
        viewModel.onPermissionStatus(status)
    }

    BluetoothScannerScreen(
        state = state,
        permissionsState = permissionsState,
        onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
        onToggleScan = {
            if (state.isScanning) viewModel.stopScanning() else viewModel.startScanning()
        },
        onSignalSelected = { viewModel.selectDevice(it) },
        onDismissRadar = { viewModel.dismissRadar() }
    )
}

private fun bluetoothPermissions(): List<String> {
    return emptyList()
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScannerScreen(
    state: BluetoothUiState,
    permissionsState: MultiplePermissionsState,
    onRequestPermissions: () -> Unit,
    onToggleScan: () -> Unit,
    onSignalSelected: (UiDevice) -> Unit,
    onDismissRadar: () -> Unit
) {
    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bluetooth Scanner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.BluetoothSearching,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (state.isScanning) {
                        AssistChip(
                            onClick = onToggleScan,
                            label = { Text("Stop") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    contentDescription = null
                                )
                            }
                        )
                    } else {
                        AssistChip(
                            onClick = onToggleScan,
                            label = { Text("Scan") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    contentDescription = null
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionCard(
                permissionsState = permissionsState,
                state = state,
                onRequestPermissions = onRequestPermissions
            )

            AnimatedVisibility(visible = state.devices.isEmpty()) {
                EmptyState(isPermissionGranted = state.permissionStatus == PermissionStatus.Granted)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.devices) { device ->
                    DeviceCard(
                        device = device,
                        onSignalSelected = onSignalSelected
                    )
                }
            }
        }
    }

    state.selectedDevice?.let { device ->
        RadarDialog(
            device = device,
            onDismiss = onDismissRadar
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionCard(
    permissionsState: MultiplePermissionsState,
    state: BluetoothUiState,
    onRequestPermissions: () -> Unit
) {
    val needsPermission =
        state.permissionStatus != PermissionStatus.Granted || !permissionsState.allPermissionsGranted
    if (!needsPermission) return

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
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Bluetooth-Berechtigungen erforderlich",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "Wir benötigen Scan- und ggf. Standort-Rechte, um nahegelegene Geräte anzeigen zu können.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onRequestPermissions) {
                    Text("Berechtigungen erteilen")
                }
                if (permissionsState.shouldShowRationale || state.permissionStatus == PermissionStatus.Rationale) {
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
private fun EmptyState(isPermissionGranted: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isPermissionGranted) "Keine Geräte gefunden" else "Warte auf Berechtigungen",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isPermissionGranted) {
                    "Stelle sicher, dass Bluetooth aktiviert und Geräte sichtbar sind."
                } else {
                    "Bitte erteile die erforderlichen Berechtigungen, um Geräte zu sehen."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: UiDevice,
    onSignalSelected: (UiDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = device.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = { onSignalSelected(device) },
                label = { Text("RSSI: ${device.rssi} dBm") },
                leadingIcon = {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun RadarDialog(
    device: UiDevice,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        icon = {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column {
                Text(text = device.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadarView(fill = device.fillLevel)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Annäherung: ${(device.fillLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        dismissButton = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Schließen")
            }
        }
    )
}

@Composable
private fun RadarView(fill: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radarPulse")
    val pulse = transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(220.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val stroke = 12.dp.toPx()
            val radius = size.minDimension / 2 - stroke
            val sweep = 360 * fill
            drawCircle(
                color = surfaceVariant,
                radius = radius,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.9f),
                        primaryColor.copy(alpha = 0.6f),
                        primaryColor
                    )
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke * pulse.value, cap = StrokeCap.Round)
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.12f),
                radius = radius * pulse.value
            )
        }
    }
}
