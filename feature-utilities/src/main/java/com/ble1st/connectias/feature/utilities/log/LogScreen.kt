package com.ble1st.connectias.feature.utilities.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
fun LogScreen(
    state: LogState,
    onLoadLogs: (String?) -> Unit,
    onFilterByLevel: (LogProvider.LogLevel?) -> Unit,
    onFilterByTag: (String) -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> String
) {
    var filterText by remember { mutableStateOf("") }
    var tagFilterText by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogProvider.LogLevel?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Log Viewer",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filters
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        label = { Text("Filter Text") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onLoadLogs(filterText) }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onLoadLogs(filterText) })
                    )
                    
                    Box(modifier = Modifier.weight(0.6f)) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.menuAnchor(),
                                readOnly = true,
                                value = selectedLevel?.tag ?: "All Levels",
                                onValueChange = {},
                                label = { Text("Level") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Levels") },
                                    onClick = {
                                        selectedLevel = null
                                        onFilterByLevel(null)
                                        expanded = false
                                    }
                                )
                                LogProvider.LogLevel.values().forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.tag) },
                                        onClick = {
                                            selectedLevel = level
                                            onFilterByLevel(level)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tagFilterText,
                        onValueChange = { tagFilterText = it },
                        label = { Text("Filter Tag") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onFilterByTag(tagFilterText) }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter by Tag")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onFilterByTag(tagFilterText) })
                    )
                    
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                    }
                    
                    IconButton(onClick = {
                        val logs = onExportLogs()
                        clipboardManager.setText(AnnotatedString(logs))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Export Logs")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (state is LogState.Loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state is LogState.Success) {
            Text(
                "${state.logs.size} logs found",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.logs) { log ->
                    LogItem(log)
                }
            }
        } else if (state is LogState.Error) {
            Text(
                text = "Error: ${state.message}",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LogItem(log: LogEntry) {
    val levelColor = when (log.level) {
        LogProvider.LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogProvider.LogLevel.WARN -> Color(0xFFFF9800)
        LogProvider.LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogProvider.LogLevel.DEBUG -> Color.Gray
        LogProvider.LogLevel.VERBOSE -> Color.LightGray
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.level.tag.substring(0, 1),
                    color = Color.White,
                    modifier = Modifier
                        .background(levelColor, shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
