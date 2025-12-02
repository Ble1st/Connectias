package com.ble1st.connectias.feature.network.wol

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.feature.network.wol.models.WolDevice
import com.ble1st.connectias.feature.network.wol.models.WolHistoryEntry
import com.ble1st.connectias.feature.network.wol.models.WolResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wake-on-LAN management screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WolScreen(
    viewModel: WolViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val history by viewModel.history.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showHistory by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState()

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
                viewModel.clearError()
            }
        }
    }

    // Show success snackbar
    LaunchedEffect(uiState.lastResult) {
        uiState.lastResult?.let { result ->
            val message = when (result) {
                is WolResult.Success -> "Magic packet sent to ${result.device.name}"
                is WolResult.DeviceAwake -> "${result.device.name} is now online (${result.responseTime}ms)"
                is WolResult.DeviceNotResponding -> "${result.device.name} did not respond"
                else -> null
            }
            message?.let {
                scope.launch {
                    snackbarHostState.showSnackbar(it)
                    viewModel.clearResult()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wake-on-LAN") },
                actions = {
                    IconButton(onClick = { viewModel.refreshDeviceStatuses() }) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { viewModel.showQuickWakeDialog() }) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Quick Wake")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDeviceDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Add Device")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (devices.isEmpty()) {
                EmptyDevicesState(
                    onAddDevice = { viewModel.showAddDeviceDialog() },
                    onQuickWake = { viewModel.showQuickWakeDialog() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            isWaking = uiState.wakingDeviceId == device.id,
                            onWake = { viewModel.wakeDevice(device) },
                            onEdit = { viewModel.setEditDevice(device) },
                            onDelete = { viewModel.removeDevice(device.id) }
                        )
                    }
                }
            }
        }

        // Add/Edit Device Dialog
        if (uiState.showAddDeviceDialog || uiState.editingDevice != null) {
            AddEditDeviceDialog(
                device = uiState.editingDevice,
                onDismiss = { viewModel.hideAddDeviceDialog() },
                onSave = { device ->
                    if (uiState.editingDevice != null) {
                        viewModel.updateDevice(device)
                    } else {
                        viewModel.addDevice(device)
                    }
                    viewModel.hideAddDeviceDialog()
                }
            )
        }

        // Quick Wake Dialog
        if (uiState.showQuickWakeDialog) {
            QuickWakeDialog(
                onDismiss = { viewModel.hideQuickWakeDialog() },
                onWake = { mac, broadcast, port ->
                    viewModel.quickWake(mac, broadcast, port)
                    viewModel.hideQuickWakeDialog()
                }
            )
        }

        // History Bottom Sheet
        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = historySheetState
            ) {
                HistorySheet(
                    history = history,
                    onClear = { viewModel.clearHistory() }
                )
            }
        }
    }
}

@Composable
private fun EmptyDevicesState(
    onAddDevice: () -> Unit,
    onQuickWake: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No devices configured",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add devices to wake them remotely via Wake-on-LAN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onAddDevice) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Device")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onQuickWake) {
            Icon(Icons.Default.FlashOn, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Quick Wake")
        }
    }
}

@Composable
private fun DeviceCard(
    device: WolDevice,
    isWaking: Boolean,
    onWake: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (device.isOnline) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Computer,
                    contentDescription = null,
                    tint = if (device.isOnline) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = if (device.isOnline) "Online" else "Offline",
                        modifier = Modifier.size(8.dp),
                        tint = if (device.isOnline) Color(0xFF4CAF50) else Color(0xFFBDBDBD)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                device.ipAddress?.let { ip ->
                    Text(
                        text = ip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                IconButton(
                    onClick = onWake,
                    enabled = !isWaking
                ) {
                    if (isWaking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Power,
                            contentDescription = "Wake",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditDeviceDialog(
    device: WolDevice?,
    onDismiss: () -> Unit,
    onSave: (WolDevice) -> Unit
) {
    var name by remember(device) { mutableStateOf(device?.name ?: "") }
    var macAddress by remember(device) { mutableStateOf(device?.macAddress ?: "") }
    var ipAddress by remember(device) { mutableStateOf(device?.ipAddress ?: "") }
    var broadcastAddress by remember(device) { mutableStateOf(device?.broadcastAddress ?: "255.255.255.255") }
    var port by remember(device) { mutableStateOf(device?.port?.toString() ?: "9") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var macError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (device != null) "Edit Device" else "Add Device") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = null
                    },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = macAddress,
                    onValueChange = { 
                        macAddress = it.uppercase()
                        macError = null
                    },
                    label = { Text("MAC Address") },
                    placeholder = { Text("XX:XX:XX:XX:XX:XX") },
                    isError = macError != null,
                    supportingText = macError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP Address (optional)") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = broadcastAddress,
                        onValueChange = { broadcastAddress = it },
                        label = { Text("Broadcast") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validation
                    if (name.isBlank()) {
                        nameError = "Name is required"
                        return@Button
                    }
                    
                    val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
                    if (!macAddress.matches(macPattern)) {
                        macError = "Invalid MAC address format"
                        return@Button
                    }

                    val newDevice = WolDevice(
                        id = device?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        macAddress = macAddress.trim(),
                        ipAddress = ipAddress.trim().ifBlank { null },
                        broadcastAddress = broadcastAddress.trim(),
                        port = port.toIntOrNull() ?: 9
                    )
                    onSave(newDevice)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QuickWakeDialog(
    onDismiss: () -> Unit,
    onWake: (String, String, Int) -> Unit
) {
    var macAddress by remember { mutableStateOf("") }
    var broadcastAddress by remember { mutableStateOf("255.255.255.255") }
    var port by remember { mutableStateOf("9") }
    var macError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Wake") },
        text = {
            Column {
                Text(
                    text = "Send a magic packet to wake a device immediately",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = macAddress,
                    onValueChange = { 
                        macAddress = it.uppercase()
                        macError = null
                    },
                    label = { Text("MAC Address") },
                    placeholder = { Text("XX:XX:XX:XX:XX:XX") },
                    isError = macError != null,
                    supportingText = macError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = broadcastAddress,
                        onValueChange = { broadcastAddress = it },
                        label = { Text("Broadcast") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
                    if (!macAddress.matches(macPattern)) {
                        macError = "Invalid MAC address format"
                        return@Button
                    }
                    onWake(macAddress, broadcastAddress, port.toIntOrNull() ?: 9)
                }
            ) {
                Icon(Icons.Default.Power, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wake")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun HistorySheet(
    history: List<WolHistoryEntry>,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Wake History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (history.isNotEmpty()) {
                OutlinedButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Text(
                text = "No wake attempts yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { entry ->
                    HistoryItem(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: WolHistoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.success) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (entry.success) Icons.Default.Power else Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = if (entry.success) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = buildString {
                        append(if (entry.success) "Success" else "Failed")
                        if (entry.deviceResponded) {
                            append(" • Device responded")
                            entry.responseTime?.let { append(" (${it}ms)") }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
