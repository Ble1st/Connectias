package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.network.scanner.DnsLookupProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLookupScreen(
    state: DnsState,
    onLookup: (String, DnsLookupProvider.RecordType, String?) -> Unit,
    onReverseLookup: (String, String?) -> Unit,
    onTestDns: (String) -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var dnsServer by remember { mutableStateOf("") }
    var selectedRecordType by remember { mutableStateOf(DnsLookupProvider.RecordType.A) }
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "DNS Diagnostics",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Domain / IP") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = dnsServer,
                        onValueChange = { dnsServer = it },
                        label = { Text("DNS Server (Optional)") },
                        placeholder = { Text("e.g. 8.8.8.8") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = selectedRecordType.name,
                            onValueChange = {},
                            label = { Text("Record Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DnsLookupProvider.RecordType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        selectedRecordType = type
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onLookup(domain, selectedRecordType, dnsServer.takeIf { it.isNotBlank() }) },
                            modifier = Modifier.weight(1f),
                            enabled = state !is DnsState.Loading && domain.isNotBlank()
                        ) {
                            Text("Lookup")
                        }
                        Button(
                            onClick = { onReverseLookup(domain, dnsServer.takeIf { it.isNotBlank() }) },
                            modifier = Modifier.weight(1f),
                            enabled = state !is DnsState.Loading && domain.isNotBlank()
                        ) {
                            Text("Reverse")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { onTestDns(dnsServer) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state !is DnsState.Loading && dnsServer.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Test DNS Server")
                    }
                }
            }
        }
        
        if (state is DnsState.Loading) {
             item {
                 Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                     CircularProgressIndicator()
                 }
             }
        } else if (state is DnsState.Success) {
             item {
                 Text("Results", style = MaterialTheme.typography.titleLarge)
             }
             
             items(state.results) { result ->
                 Card(
                     modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                 ) {
                     Text(
                         text = result,
                         modifier = Modifier.padding(16.dp),
                         style = MaterialTheme.typography.bodyMedium
                     )
                 }
             }
        } else if (state is DnsState.Error) {
             item {
                 Card(
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Row(
                         modifier = Modifier.padding(16.dp),
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                         Spacer(modifier = Modifier.width(16.dp))
                         Text(
                             text = state.message,
                             color = MaterialTheme.colorScheme.onErrorContainer
                         )
                     }
                 }
             }
        }
    }
}
