package com.ble1st.connectias.feature.utilities.text

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

enum class TextTool(val label: String) {
    CONVERT_CASE("Convert Case"),
    COUNT_STATS("Count Words/Chars"),
    REGEX_TESTER("Regex Tester"),
    JSON_FORMATTER("Format/Validate JSON")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextScreen(
    state: TextState,
    onConvertCase: (String, TextProvider.CaseType) -> Unit,
    onCountStats: (String) -> Unit,
    onTestRegex: (String, String) -> Unit,
    onFormatJson: (String) -> Unit,
    onValidateJson: (String) -> Unit
) {
    var selectedTool by remember { mutableStateOf(TextTool.CONVERT_CASE) }
    var expanded by remember { mutableStateOf(false) }
    var expandedCase by remember { mutableStateOf(false) }
    
    // Inputs
    var inputText by remember { mutableStateOf("") }
    var regexPattern by remember { mutableStateOf("") }
    var selectedCaseType by remember { mutableStateOf(TextProvider.CaseType.UPPER) }
    
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Text Tools",
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
                            value = selectedTool.label,
                            onValueChange = {},
                            label = { Text("Tool") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            TextTool.values().forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(tool.label) },
                                    onClick = {
                                        selectedTool = tool
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
                    
                    when (selectedTool) {
                        TextTool.CONVERT_CASE -> {
                            ExposedDropdownMenuBox(
                                expanded = expandedCase,
                                onExpandedChange = { expandedCase = !expandedCase }
                            ) {
                                OutlinedTextField(
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    readOnly = true,
                                    value = selectedCaseType.name,
                                    onValueChange = {},
                                    label = { Text("Case Type") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCase) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedCase,
                                    onDismissRequest = { expandedCase = false }
                                ) {
                                    TextProvider.CaseType.values().forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.name) },
                                            onClick = {
                                                selectedCaseType = type
                                                expandedCase = false
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onConvertCase(inputText, selectedCaseType) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = inputText.isNotEmpty()
                            ) {
                                Text("Convert")
                            }
                        }
                        TextTool.COUNT_STATS -> {
                            Button(
                                onClick = { onCountStats(inputText) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = inputText.isNotEmpty()
                            ) {
                                Text("Count")
                            }
                        }
                        TextTool.REGEX_TESTER -> {
                            OutlinedTextField(
                                value = regexPattern,
                                onValueChange = { regexPattern = it },
                                label = { Text("Regex Pattern") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onTestRegex(inputText, regexPattern) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = inputText.isNotEmpty() && regexPattern.isNotEmpty()
                            ) {
                                Text("Test Regex")
                            }
                        }
                        TextTool.JSON_FORMATTER -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onFormatJson(inputText) },
                                    modifier = Modifier.weight(1f),
                                    enabled = inputText.isNotEmpty()
                                ) {
                                    Text("Format")
                                }
                                Button(
                                    onClick = { onValidateJson(inputText) },
                                    modifier = Modifier.weight(1f),
                                    enabled = inputText.isNotEmpty()
                                ) {
                                    Text("Validate")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state !is TextState.Idle) {
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
                            if (state is TextState.Converted || state is TextState.JsonFormatted) {
                                IconButton(onClick = {
                                    val text = when (state) {
                                        is TextState.Converted -> state.text
                                        is TextState.JsonFormatted -> state.json
                                        else -> ""
                                    }
                                    clipboardManager.setText(AnnotatedString(text))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy to clipboard")
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        when (state) {
                            is TextState.Converted -> Text(state.text, style = MaterialTheme.typography.bodyMedium)
                            is TextState.Counted -> {
                                Text("Words: ${state.count.wordCount}")
                                Text("Characters: ${state.count.charCount}")
                                Text("Chars (no spaces): ${state.count.charCountNoSpaces}")
                                Text("Lines: ${state.count.lineCount}")
                            }
                            is TextState.RegexTested -> {
                                if (state.result.isValid) {
                                    Text("Match found: ${state.result.matches.isNotEmpty()}")
                                    Text("Match count: ${state.result.matchCount}")
                                    if (state.result.matches.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Matches:", style = MaterialTheme.typography.labelMedium)
                                        state.result.matches.forEach { match ->
                                            Text("• $match", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                } else {
                                    Text("Invalid Regex: ${state.result.errorMessage}", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            is TextState.JsonFormatted -> Text(state.json, style = MaterialTheme.typography.bodyMedium)
                            is TextState.JsonValid -> Text(state.message, color = MaterialTheme.colorScheme.primary)
                            is TextState.JsonInvalid -> Text(state.message, color = MaterialTheme.colorScheme.error)
                            is TextState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
