package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.network.R
import com.ble1st.connectias.feature.network.models.FlowStats
import com.ble1st.connectias.feature.network.models.TopTalker
import java.util.Locale

@Composable
fun FlowAnalyzerScreen(
    state: FlowAnalyzerState,
    onAnalyzeFlows: () -> Unit,
    onResetState: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = getThemedString(stringResource(R.string.network_flow_analyzer_title)),
            style = MaterialTheme.typography.headlineMedium
        )

        when (state) {
            is FlowAnalyzerState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is FlowAnalyzerState.Success -> {
                FlowStatsContent(
                    stats = state.stats,
                    onRefresh = onAnalyzeFlows
                )
            }
            is FlowAnalyzerState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = getThemedString(stringResource(R.string.error_prefix, state.message)),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = onResetState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getThemedString(stringResource(R.string.reset)))
                        }
                    }
                }
            }
            is FlowAnalyzerState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = getThemedString(stringResource(R.string.click_to_start_flow_analysis)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = getThemedString(stringResource(R.string.for_best_results_use_dashboard)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onAnalyzeFlows,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getThemedString(stringResource(R.string.analyze_flows)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowStatsContent(
    stats: FlowStats,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(getThemedString(stringResource(R.string.refresh_analysis)))
            }
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Flow Statistics",
                        style = MaterialTheme.typography.titleMedium
                    )
                    InfoRow("Total Flows", stats.totalFlows.toString())
                    InfoRow("Total Bytes", formatBytes(stats.totalBytes))
                }
            }
        }

        item {
            Text(
                text = "Top Talkers",
                style = MaterialTheme.typography.titleLarge
            )
        }

        items(stats.topTalkers) { talker ->
            TopTalkerCard(talker)
        }

        item {
            Text(
                text = "Protocol Distribution",
                style = MaterialTheme.typography.titleLarge
            )
        }

        items(stats.protocolDistribution.toList()) { (protocol, bytes) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = protocol,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatBytes(bytes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TopTalkerCard(talker: TopTalker) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = talker.hostname ?: talker.ipAddress,
                style = MaterialTheme.typography.titleMedium
            )
            InfoRow("IP Address", talker.ipAddress)
            InfoRow("Bytes Transferred", formatBytes(talker.bytesTransferred))
            InfoRow("Flow Count", talker.flowCount.toString())
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
