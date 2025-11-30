package com.ble1st.connectias.feature.network.topology.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.network.topology.models.NetworkTopology
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
            text = "Network Topology",
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
                        Text("Refresh")
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
                                text = "Topology Statistics",
                                style = MaterialTheme.typography.titleMedium
                            )
                            InfoRow("Nodes", state.topology.nodes.size.toString())
                            InfoRow("Edges", state.topology.edges.size.toString())
                            Text(
                                text = "Tap a node to see details",
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
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = onBuildTopology,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Retry")
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
                            text = "Network topology requires discovered devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use the Network Dashboard to discover devices first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onBuildTopology,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Build Topology")
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
