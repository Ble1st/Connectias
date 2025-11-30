package com.ble1st.connectias.feature.utilities.encoding

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
fun EncodingScreen(
    state: EncodingState,
    onEncode: (String, EncodingProvider.EncodingType) -> Unit,
    onDecode: (String, EncodingProvider.EncodingType) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(EncodingProvider.EncodingType.BASE64) }
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Encoding Tools",
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
                            value = selectedType.name,
                            onValueChange = {},
                            label = { Text("Encoding Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            EncodingProvider.EncodingType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        selectedType = type
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onEncode(inputText, selectedType) },
                            modifier = Modifier.weight(1f),
                            enabled = state !is EncodingState.Loading && inputText.isNotEmpty()
                        ) {
                            Text("Encode")
                        }
                        Button(
                            onClick = { onDecode(inputText, selectedType) },
                            modifier = Modifier.weight(1f),
                            enabled = state !is EncodingState.Loading && inputText.isNotEmpty()
                        ) {
                            Text("Decode")
                        }
                    }
                }
            }
        }

        if (state is EncodingState.Success) {
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
                                clipboardManager.setText(AnnotatedString(state.result))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy to clipboard")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.result, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (state is EncodingState.Error) {
            item {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
