package com.ble1st.connectias.feature.network.analysis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VlanAnalyzerScreen(
    state: VlanAnalyzerState,
    onAnalyzeVlans: (List<String>) -> Unit,
    onResetState: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "VLAN Analyzer",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "VLANs are inferred from subnet segmentation since Android doesn't provide direct VLAN tag access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (state) {
            is VlanAnalyzerState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is VlanAnalyzerState.Success -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Detected VLANs (${state.vlans.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(state.vlans) { vlan ->
                        VlanInfoCard(vlan)
                    }
                }
            }
            is VlanAnalyzerState.Error -> {
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
                        TextButton(
                            onClick = onResetState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
            is VlanAnalyzerState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "VLAN analysis requires IP addresses from network discovery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use the Network Dashboard to discover devices first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Note: Passing emptyList() triggers internal device discovery
                        // The analyzer will attempt to discover devices automatically
                        Button(
                            onClick = { onAnalyzeVlans(emptyList()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start VLAN Analysis (discovering)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VlanInfoCard(vlanInfo: com.ble1st.connectias.feature.network.analysis.models.VlanInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "VLAN ${vlanInfo.vlanId ?: "Unknown"} - ${vlanInfo.subnetInfo.cidr}",
                style = MaterialTheme.typography.titleMedium
            )

            InfoRow("Network Address", vlanInfo.subnetInfo.networkAddress)
            InfoRow("Device Count", vlanInfo.deviceCount.toString())
            InfoRow("Usable Hosts", vlanInfo.subnetInfo.usableHosts.toString())

            if (vlanInfo.devices.isNotEmpty()) {
                Text(
                    text = "Devices:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                vlanInfo.devices.take(5).forEach { device ->
                    Text(
                        text = "  • $device",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (vlanInfo.devices.size > 5) {
                    Text(
                        text = "  ... and ${vlanInfo.devices.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
