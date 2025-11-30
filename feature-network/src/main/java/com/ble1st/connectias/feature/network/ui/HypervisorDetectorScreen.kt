package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.network.models.HypervisorInfo
import com.ble1st.connectias.feature.network.models.VmInfo

@Composable
fun HypervisorDetectorScreen(
    state: HypervisorDetectorState,
    onDetectHypervisors: () -> Unit,
    onResetState: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Hypervisor/VM Detector",
            style = MaterialTheme.typography.headlineMedium
        )

        when (state) {
            is HypervisorDetectorState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is HypervisorDetectorState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.hypervisors.isNotEmpty()) {
                        item {
                            Text(
                                text = "Detected Hypervisors (${state.hypervisors.size})",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        items(state.hypervisors) { hypervisor ->
                            HypervisorInfoCard(hypervisor)
                        }
                    }

                    if (state.containers.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detected Containers (${state.containers.size})",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        items(state.containers) { container ->
                            ContainerCard(container)
                        }
                    }

                    if (state.hypervisors.isEmpty() && state.containers.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No hypervisors or VMs detected.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is HypervisorDetectorState.Error -> {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onResetState,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset")
                            }
                            Button(
                                onClick = onDetectHypervisors,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            is HypervisorDetectorState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hypervisor detection requires discovered devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use the Network Dashboard to discover devices first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onDetectHypervisors,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Detect Hypervisors")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HypervisorInfoCard(hypervisor: HypervisorInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = hypervisor.type.name,
                style = MaterialTheme.typography.titleMedium
            )
            InfoRow("VMs Detected", hypervisor.detectedVms.size.toString())
            
            if (hypervisor.detectedVms.isNotEmpty()) {
                Text(
                    text = "Virtual Machines:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                hypervisor.detectedVms.forEach { vm ->
                    VmInfoRow(vm)
                }
            }
        }
    }
}

@Composable
private fun VmInfoRow(vm: VmInfo) {
    Column(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "  • ${vm.hostname} (${vm.ipAddress})",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ContainerCard(container: com.ble1st.connectias.feature.network.models.NetworkDevice) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = container.hostname,
                style = MaterialTheme.typography.titleMedium
            )
            InfoRow("IP Address", container.ipAddress)
            container.macAddress?.let { InfoRow("MAC Address", it) }
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
