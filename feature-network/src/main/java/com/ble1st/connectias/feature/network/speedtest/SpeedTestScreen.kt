package com.ble1st.connectias.feature.network.speedtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestPhase
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestResult
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestServer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Network Speed Test screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    viewModel: SpeedTestViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()

    var showHistory by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Speed Test") },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Server selection
            ServerSelector(
                servers = uiState.servers,
                selectedServer = uiState.selectedServer,
                enabled = !uiState.isRunning,
                onServerSelected = { viewModel.selectServer(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Connection type indicator
            Text(
                text = "Connection: ${uiState.connectionType}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Speed gauge
            SpeedGauge(
                phase = uiState.phase,
                downloadSpeed = uiState.currentDownloadSpeed,
                uploadSpeed = uiState.currentUploadSpeed,
                downloadProgress = uiState.downloadProgress,
                uploadProgress = uiState.uploadProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Results or status
            when {
                uiState.lastResult != null -> {
                    ResultSummary(result = uiState.lastResult!!)
                }
                uiState.isRunning -> {
                    StatusText(phase = uiState.phase)
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start/Stop button
            TestButton(
                isRunning = uiState.isRunning,
                canStart = uiState.canStart,
                onStart = { viewModel.startTest() },
                onStop = { viewModel.stopTest() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // History bottom sheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSelector(
    servers: List<SpeedTestServer>,
    selectedServer: SpeedTestServer?,
    enabled: Boolean,
    onServerSelected: (SpeedTestServer) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedServer?.name ?: "Select Server",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Test Server") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(server.name)
                            server.location?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onServerSelected(server)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SpeedGauge(
    phase: SpeedTestPhase,
    downloadSpeed: Double,
    uploadSpeed: Double,
    downloadProgress: Float,
    uploadProgress: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val animatedDownloadProgress by animateFloatAsState(
        targetValue = downloadProgress,
        animationSpec = tween(300),
        label = "downloadProgress"
    )
    val animatedUploadProgress by animateFloatAsState(
        targetValue = uploadProgress,
        animationSpec = tween(300),
        label = "uploadProgress"
    )

    val displaySpeed = when (phase) {
        SpeedTestPhase.DOWNLOADING -> downloadSpeed
        SpeedTestPhase.UPLOADING -> uploadSpeed
        SpeedTestPhase.COMPLETED -> maxOf(downloadSpeed, uploadSpeed)
        else -> 0.0
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 24.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Background arc
            drawArc(
                color = surfaceVariant,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Download progress arc (outer)
            if (phase == SpeedTestPhase.DOWNLOADING || phase == SpeedTestPhase.COMPLETED) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.5f), primaryColor)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedDownloadProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Upload progress arc (inner)
            val innerRadius = radius - strokeWidth - 8.dp.toPx()
            if (phase == SpeedTestPhase.UPLOADING || phase == SpeedTestPhase.COMPLETED) {
                drawArc(
                    color = surfaceVariant,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = strokeWidth * 0.6f, cap = StrokeCap.Round)
                )

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(secondaryColor.copy(alpha = 0.5f), secondaryColor)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedUploadProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = strokeWidth * 0.6f, cap = StrokeCap.Round)
                )
            }
        }

        // Center content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatSpeed(displaySpeed),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (phase) {
                    SpeedTestPhase.DOWNLOADING -> "Download"
                    SpeedTestPhase.UPLOADING -> "Upload"
                    SpeedTestPhase.MEASURING_LATENCY -> "Latency"
                    SpeedTestPhase.CONNECTING -> "Connecting..."
                    else -> "Mbps"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultSummary(result: SpeedTestResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ResultItem(
            icon = Icons.Default.ArrowDownward,
            label = "Download",
            value = result.downloadSpeedFormatted,
            color = MaterialTheme.colorScheme.primary
        )
        ResultItem(
            icon = Icons.Default.ArrowUpward,
            label = "Upload",
            value = result.uploadSpeedFormatted,
            color = MaterialTheme.colorScheme.secondary
        )
        ResultItem(
            icon = Icons.Default.Timer,
            label = "Ping",
            value = "${result.latency} ms",
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun ResultItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusText(phase: SpeedTestPhase) {
    val text = when (phase) {
        SpeedTestPhase.CONNECTING -> "Connecting to server..."
        SpeedTestPhase.MEASURING_LATENCY -> "Measuring latency..."
        SpeedTestPhase.DOWNLOADING -> "Testing download speed..."
        SpeedTestPhase.UPLOADING -> "Testing upload speed..."
        else -> ""
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TestButton(
    isRunning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isRunning) {
        OutlinedButton(
            onClick = onStop,
            modifier = modifier.height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop Test")
        }
    } else {
        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = modifier.height(56.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Test")
        }
    }
}

@Composable
private fun HistorySheet(
    history: List<SpeedTestResult>,
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
                text = "Test History",
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
                text = "No tests yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { result ->
                    HistoryItem(result = result)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(result: SpeedTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${result.downloadSpeedFormatted} ↓ / ${result.uploadSpeedFormatted} ↑",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${result.server.name} • ${result.latency}ms • ${result.connectionType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = formatTimestamp(result.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSpeed(speed: Double): String {
    return when {
        speed >= 1000 -> String.format("%.1f", speed / 1000)
        speed >= 100 -> String.format("%.0f", speed)
        speed >= 10 -> String.format("%.1f", speed)
        else -> String.format("%.2f", speed)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
