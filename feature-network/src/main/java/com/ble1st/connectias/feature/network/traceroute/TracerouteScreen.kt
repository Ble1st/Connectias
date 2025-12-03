package com.ble1st.connectias.feature.network.traceroute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Traceroute screen for network path analysis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    viewModel: TracerouteViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    var targetHost by remember { mutableStateOf("") }
    var isTracing by remember { mutableStateOf(false) }
    var hops by remember { mutableStateOf<List<TracerouteHop>>(emptyList()) }
    var traceJob by remember { mutableStateOf<Job?>(null) }

    val tracerouteProvider = remember { TracerouteProvider() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traceroute") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero section
            item {
                TracerouteHeroSection()
            }

            // Input section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = targetHost,
                            onValueChange = { targetHost = it },
                            label = { Text("Target Host") },
                            placeholder = { Text("e.g., google.com or 8.8.8.8") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isTracing
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    if (isTracing) {
                                        traceJob?.cancel()
                                        isTracing = false
                                    } else {
                                        hops = emptyList()
                                        isTracing = true
                                        traceJob = scope.launch {
                                            tracerouteProvider.runTraceroute(targetHost).collect { hop ->
                                                hops = hops + hop
                                                if (hop.status == HopStatus.DESTINATION_REACHED) {
                                                    isTracing = false
                                                }
                                            }
                                            isTracing = false
                                        }
                                    }
                                },
                                enabled = targetHost.isNotBlank() || isTracing
                            ) {
                                Icon(
                                    if (isTracing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isTracing) "Stop" else "Trace")
                            }
                        }
                    }
                }
            }

            // Progress indicator
            if (isTracing) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            // Results
            if (hops.isNotEmpty()) {
                item {
                    Text(
                        text = "Route to $targetHost",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(hops.filter { it.status != HopStatus.STARTED }) { hop ->
                    HopCard(hop = hop)
                }

                // Summary
                if (!isTracing && hops.any { it.status == HopStatus.DESTINATION_REACHED }) {
                    item {
                        TraceSummaryCard(hops = hops)
                    }
                }
            }
        }
    }
}

@Composable
private fun TracerouteHeroSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF3B82F6).copy(alpha = 0.2f),
                            Color(0xFF06B6D4).copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF06B6D4))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Traceroute",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trace the network path to any host",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HopCard(hop: TracerouteHop) {
    val statusColor = when (hop.status) {
        HopStatus.HOP_REACHED -> Color(0xFF10B981)
        HopStatus.DESTINATION_REACHED -> Color(0xFF3B82F6)
        HopStatus.TIMEOUT -> Color(0xFFF59E0B)
        HopStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hop number
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hop.hop.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Hop details
            Column(modifier = Modifier.weight(1f)) {
                when (hop.status) {
                    HopStatus.TIMEOUT -> {
                        Text(
                            text = "* * *",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Request timed out",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HopStatus.HOP_REACHED, HopStatus.DESTINATION_REACHED -> {
                        Text(
                            text = hop.hostname ?: hop.ipAddress ?: "Unknown",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        hop.ipAddress?.let { ip ->
                            if (hop.hostname != null && hop.hostname != ip) {
                                Text(
                                    text = ip,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = hop.status.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Latency
            hop.latency?.let { latency ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = statusColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${latency}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Status icon
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                when (hop.status) {
                    HopStatus.DESTINATION_REACHED -> Icons.Default.Check
                    HopStatus.ERROR -> Icons.Default.Close
                    else -> Icons.Default.Route
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = statusColor
            )
        }
    }
}

@Composable
private fun TraceSummaryCard(hops: List<TracerouteHop>) {
    val reachedHops = hops.filter { 
        it.status == HopStatus.HOP_REACHED || it.status == HopStatus.DESTINATION_REACHED 
    }
    val avgLatency = reachedHops.mapNotNull { it.latency }.average()
    val timeouts = hops.count { it.status == HopStatus.TIMEOUT }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "Total Hops",
                    value = hops.maxOfOrNull { it.hop }?.toString() ?: "0"
                )
                SummaryItem(
                    label = "Avg Latency",
                    value = if (avgLatency.isNaN()) "N/A" else "${avgLatency.toInt()}ms"
                )
                SummaryItem(
                    label = "Timeouts",
                    value = timeouts.toString()
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

