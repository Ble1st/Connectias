package com.ble1st.connectias.feature.network.topology.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.LocalAppStrings
import com.ble1st.connectias.feature.network.topology.models.TopologyNode
import com.ble1st.connectias.feature.network.topology.ui.components.TopologyGraph

@Composable
fun TopologyScreen(
    state: TopologyState,
    onBuildTopology: () -> Unit,
    onResetState: () -> Unit
) {
    var selectedNode by remember { mutableStateOf<TopologyNode?>(null) }
    
    LaunchedEffect(state) {
        selectedNode = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = LocalAppStrings.current.networkTopologyTitle,
            style = MaterialTheme.typography.headlineMedium
        )

        when (state) {
            is TopologyState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is TopologyState.Success -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onBuildTopology) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(LocalAppStrings.current.actionRefresh)
                    }
                    Button(
                        onClick = onResetState,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(LocalAppStrings.current.actionReset)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TopologyGraph(
                            topology = state.topology,
                            modifier = Modifier.fillMaxSize(),
                            onNodeSelected = { selectedNode = it }
                        )
                    }
                }

                // Node details
                selectedNode?.let { node ->
                    NodeDetailsCard(node)
                } ?: run {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = LocalAppStrings.current.topologyStatsTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            InfoRow(LocalAppStrings.current.statsNodes, state.topology.nodes.size.toString())
                            InfoRow(LocalAppStrings.current.statsEdges, state.topology.edges.size.toString())
                            Text(
                                text = LocalAppStrings.current.topologyHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            is TopologyState.Error -> {
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
                            text = "${LocalAppStrings.current.alertError}: ${state.message}",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = onBuildTopology,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(LocalAppStrings.current.actionRetry)
                        }
                    }
                }
            }
            is TopologyState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = LocalAppStrings.current.topologyEmptyStateTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = LocalAppStrings.current.topologyEmptyStateDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onBuildTopology,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(LocalAppStrings.current.actionBuildTopology)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeDetailsCard(node: TopologyNode) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.titleMedium
            )
            InfoRow("Type", node.type.name)
            node.ipAddress?.let { InfoRow("IP Address", it) }
            node.macAddress?.let { InfoRow("MAC Address", it) }
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