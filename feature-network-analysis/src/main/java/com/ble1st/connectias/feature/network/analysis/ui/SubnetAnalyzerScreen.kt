package com.ble1st.connectias.feature.network.analysis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SubnetAnalyzerScreen(
    state: SubnetAnalyzerState,
    onAnalyzeCidr: (String) -> Unit,
    onResetState: () -> Unit = {}
) {
    var cidrInput by remember { mutableStateOf("") }
    val isValidInput = cidrInput.trim().isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Subnet Analyzer",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = cidrInput,
            onValueChange = { cidrInput = it },
            label = { Text("CIDR Notation") },
            placeholder = { Text("192.168.1.0/24") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (isValidInput) {
                        onAnalyzeCidr(cidrInput.trim())
                    }
                }
            ),
            trailingIcon = {
                IconButton(
                    onClick = { 
                        if (isValidInput) {
                            onAnalyzeCidr(cidrInput.trim())
                        }
                    },
                    enabled = isValidInput
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Analyze")
                }
            }
        )

        Button(
            onClick = { 
                if (isValidInput) {
                    onAnalyzeCidr(cidrInput.trim())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isValidInput
        ) {
            Text("Analyze Subnet")
        }

        when (state) {
            is SubnetAnalyzerState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SubnetAnalyzerState.Success -> {
                SubnetInfoCard(state.subnetInfo)
            }
            is SubnetAnalyzerState.DiscoveredSubnets -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Discovered Subnets (${state.subnets.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(state.subnets) { subnet ->
                        SubnetInfoCard(subnet)
                    }
                }
            }
            is SubnetAnalyzerState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is SubnetAnalyzerState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enter a CIDR notation to analyze",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Example: 192.168.1.0/24",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubnetInfoCard(subnetInfo: com.ble1st.connectias.feature.network.analysis.models.SubnetInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = subnetInfo.cidr,
                style = MaterialTheme.typography.titleMedium
            )

            InfoRow("Network Address", subnetInfo.networkAddress)
            InfoRow("Subnet Mask", subnetInfo.subnetMask)
            InfoRow("First Host", subnetInfo.firstHost)
            InfoRow("Last Host", subnetInfo.lastHost)
            InfoRow("Broadcast Address", subnetInfo.broadcastAddress)
            InfoRow("Total Hosts", subnetInfo.totalHosts.toString())
            InfoRow("Usable Hosts", subnetInfo.usableHosts.toString())
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
