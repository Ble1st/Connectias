package com.ble1st.connectias.feature.network.analysis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun MacAnalyzerScreen(
    state: MacAnalyzerState,
    onAnalyzeMacAddress: (String) -> Unit
) {
    var macAddressInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            Text(
                text = "MAC Address Analyzer",
                style = MaterialTheme.typography.headlineMedium
            )

            val canAnalyze = macAddressInput.trim().isNotEmpty()

            OutlinedTextField(
                value = macAddressInput,
                onValueChange = { macAddressInput = it },
                label = { Text("MAC Address") },
                placeholder = { Text("00:11:22:33:44:55") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (canAnalyze) {
                            onAnalyzeMacAddress(macAddressInput.trim())
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { 
                            if (canAnalyze) {
                                onAnalyzeMacAddress(macAddressInput.trim())
                            }
                        },
                        enabled = canAnalyze
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Analyze")
                    }
                }
            )

            Button(
                onClick = { onAnalyzeMacAddress(macAddressInput.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canAnalyze
            ) {
                Text("Analyze MAC Address")
            }

            when (state) {
                is MacAnalyzerState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MacAnalyzerState.Success -> {
                    MacAddressInfoCard(state.macInfo)
                }
                is MacAnalyzerState.Error -> {
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
                is MacAnalyzerState.Idle -> {
                    // Show placeholder or instructions
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Enter a MAC address to analyze",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Supported formats:\n• 00:11:22:33:44:55\n• 00-11-22-33-44-55\n• 001122334455",
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
private fun MacAddressInfoCard(macInfo: com.ble1st.connectias.feature.network.analysis.models.MacAddressInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "MAC Address Information",
                style = MaterialTheme.typography.titleLarge
            )

            InfoRow("MAC Address", macInfo.formattedAddress)
            InfoRow("Valid Format", if (macInfo.isValid) "Yes" else "No")
            InfoRow(
                "Manufacturer",
                macInfo.manufacturer ?: "Unknown"
            )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
