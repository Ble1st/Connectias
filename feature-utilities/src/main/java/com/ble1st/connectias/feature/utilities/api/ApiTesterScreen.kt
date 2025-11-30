package com.ble1st.connectias.feature.utilities.api

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTesterScreen(
    state: ApiState,
    onSend: (String, ApiTesterProvider.HttpMethod, Map<String, String>, String?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(ApiTesterProvider.HttpMethod.GET) }
    var headersText by remember { mutableStateOf("") }
    var bodyText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "API Tester",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.weight(0.3f)
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.menuAnchor(),
                                readOnly = true,
                                value = selectedMethod.name,
                                onValueChange = {},
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                ApiTesterProvider.HttpMethod.values().forEach { method ->
                                    DropdownMenuItem(
                                        text = { Text(method.name) },
                                        onClick = {
                                            selectedMethod = method
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("URL") },
                            placeholder = { Text("https://api.example.com") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = headersText,
                        onValueChange = { headersText = it },
                        label = { Text("Headers (Key: Value)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        label = { Text("Request Body (JSON)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val headers = parseHeaders(headersText)
                            onSend(url, selectedMethod, headers, bodyText.takeIf { it.isNotBlank() })
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state !is ApiState.Loading && url.isNotBlank()
                    ) {
                        if (state is ApiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Send Request")
                        }
                    }
                }
            }
        }

        if (state is ApiState.Success) {
            val response = state.response
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Response", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val statusColor = if (response.statusCode in 200..299) Color(0xFF4CAF50) else if (response.statusCode in 400..499) Color(0xFFFF9800) else Color.Red
                        
                        Text(
                            "${response.statusCode} ${response.statusMessage}",
                            style = MaterialTheme.typography.titleLarge,
                            color = statusColor
                        )
                        Text("Duration: ${response.duration}ms", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Headers:", style = MaterialTheme.typography.labelMedium)
                        Text(formatHeaders(response.headers), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                        
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Body:", style = MaterialTheme.typography.labelMedium)
                        Text(response.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (state is ApiState.Error) {
            item {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun parseHeaders(headersText: String): Map<String, String> {
    if (headersText.isBlank()) return emptyMap()
    
    return headersText.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains(":") }
        .associate {
            val parts = it.split(":", limit = 2)
            parts[0].trim() to parts[1].trim()
        }
}

private fun formatHeaders(headers: Map<String, List<String>>): String {
    if (headers.isEmpty()) return "No headers"
    
    return headers.entries.joinToString("\n") { (key, values) ->
        "$key: ${values.joinToString(", ")}"
    }
}
