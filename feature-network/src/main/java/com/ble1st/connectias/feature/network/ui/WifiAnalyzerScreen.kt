package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.network.scanner.ChannelOverlap
import com.ble1st.connectias.feature.network.scanner.WifiChannelInfo

@Composable
fun WifiAnalyzerScreen(
    state: WifiAnalyzerState,
    onAnalyze: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state is WifiAnalyzerState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (state is WifiAnalyzerState.Error) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onAnalyze, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Retry")
                }
            }
        } else {
            WifiAnalyzerContent(
                state = state,
                onAnalyze = onAnalyze
            )
        }
    }
}

@Composable
private fun WifiAnalyzerContent(
    state: WifiAnalyzerState,
    onAnalyze: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Wi-Fi Analyzer",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Networks")
            }
        }

        if (state is WifiAnalyzerState.Success) {
            if (state.bestChannel != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Recommendation", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Best Channel: ${state.bestChannel}",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }
            }

            if (state.overlaps.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Channel Overlaps", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            state.overlaps.forEach { overlap ->
                                Text(
                                    "Channels ${overlap.channel1} & ${overlap.channel2}: ${overlap.severity.name}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Channel Details", style = MaterialTheme.typography.titleLarge)
            }

            items(state.channelInfos) { channelInfo ->
                WifiChannelItem(channelInfo)
            }
        } else if (state is WifiAnalyzerState.Idle) {
            item {
                 Text(
                    text = "Tap 'Analyze Networks' to start scanning for Wi-Fi channels and interference.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WifiChannelItem(info: WifiChannelInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Channel ${info.channel}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${info.frequency} MHz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${info.networkCount} networks")
                
                val signalColor = when {
                    info.avgSignalStrength >= -50 -> Color(0xFF4CAF50) // Green
                    info.avgSignalStrength >= -70 -> Color(0xFFFF9800) // Orange
                    else -> Color(0xFFF44336) // Red
                }
                
                Text(
                    text = "${info.avgSignalStrength} dBm",
                    color = signalColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
