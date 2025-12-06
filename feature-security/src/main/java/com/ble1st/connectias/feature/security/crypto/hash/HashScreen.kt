package com.ble1st.connectias.feature.security.crypto.hash

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashScreen(
    state: HashState,
    onCalculateText: (String, HashProvider.HashAlgorithm) -> Unit,
    onCalculateFile: (String, HashProvider.HashAlgorithm) -> Unit,
    onVerifyText: (String, String, HashProvider.HashAlgorithm) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var expectedHash by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(HashProvider.HashAlgorithm.SHA256) }
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val filePath = it.path // Note: This might need proper Uri to path conversion in real app
            if (filePath != null) {
                onCalculateFile(filePath, selectedAlgorithm)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Hash Calculator",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = selectedAlgorithm.name,
                            onValueChange = {},
                            label = { Text("Algorithm") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            HashProvider.HashAlgorithm.values().forEach { algo ->
                                DropdownMenuItem(
                                    text = { Text(algo.name) },
                                    onClick = {
                                        selectedAlgorithm = algo
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Input Text") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCalculateText(inputText, selectedAlgorithm) },
                            modifier = Modifier.weight(1f),
                            enabled = state !is HashState.Loading && inputText.isNotEmpty()
                        ) {
                            Text("Calculate Text")
                        }
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            enabled = state !is HashState.Loading
                        ) {
                            Text("Select File")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Verification", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = expectedHash,
                        onValueChange = { expectedHash = it },
                        label = { Text("Expected Hash") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { onVerifyText(inputText, expectedHash, selectedAlgorithm) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state !is HashState.Loading && inputText.isNotEmpty() && expectedHash.isNotEmpty()
                    ) {
                        Text("Verify Text Hash")
                    }
                }
            }
        }

        if (state is HashState.Success) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Result", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(state.hash))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy to clipboard")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.hash, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (state is HashState.VerificationSuccess) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(state.message, color = Color(0xFF2E7D32))
                    }
                }
            }
        } else if (state is HashState.VerificationFailed) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        } else if (state is HashState.Error) {
            item {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        
        if (state is HashState.Loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
