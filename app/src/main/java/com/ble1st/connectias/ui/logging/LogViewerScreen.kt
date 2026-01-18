package com.ble1st.connectias.ui.logging

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.core.model.LogEntry
import com.ble1st.connectias.core.model.LogLevel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.text.Regex

/**
 * Compose screen for viewing system logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: LogEntryViewModel,
    onNavigateBack: () -> Unit
) {
    val logsResult by viewModel.logsResult.collectAsState(initial = null)
    val exportState by viewModel.exportState.collectAsState()
    val context = LocalContext.current
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyPlugins by remember { mutableStateOf(false) }
    
    val logs = logsResult?.logs ?: emptyList()
    // Use same regex pattern for filtering as in LogEntryCard
    val pluginLogFilterPattern = remember { Regex("""^\[([a-zA-Z0-9_.-]+)\]\s*""") }
    val filteredLogs = remember(logs, selectedLevel, searchQuery, showOnlyPlugins) {
        logs.filter { log ->
            val matchesLevel = selectedLevel?.let { log.level == it } ?: true
            val matchesSearch = searchQuery.isEmpty() || 
                log.message.contains(searchQuery, ignoreCase = true) ||
                log.tag.contains(searchQuery, ignoreCase = true)
            val matchesPluginFilter = if (showOnlyPlugins) {
                // Use regex for more reliable plugin log detection
                pluginLogFilterPattern.find(log.message) != null
            } else {
                true
            }
            matchesLevel && matchesSearch && matchesPluginFilter
        }
    }
    
    // Create file launcher for export
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportLogs(context, it, filteredLogs)
        }
    }
    
    // Handle export state changes
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is LogEntryViewModel.ExportState.Success -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
                viewModel.resetExportState()
            }
            is LogEntryViewModel.ExportState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetExportState()
            }
            else -> {}
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Log Viewer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export button
                    IconButton(
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                                .format(Date())
                            val fileName = "connectias_logs_$timestamp.txt"
                            fileLauncher.launch(fileName)
                        },
                        enabled = filteredLogs.isNotEmpty() && exportState !is LogEntryViewModel.ExportState.Exporting
                    ) {
                        if (exportState is LogEntryViewModel.ExportState.Exporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Export logs"
                            )
                        }
                    }
                    
                    // Statistics
                    if (logsResult != null) {
                        Text(
                            text = "${logsResult!!.totalCount} logs",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Level Filter
                    Text(
                        text = "Filter by Level",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LogLevel.values().forEach { level ->
                            FilterChip(
                                selected = selectedLevel == level,
                                onClick = { 
                                    selectedLevel = if (selectedLevel == level) null else level
                                },
                                label = { Text(level.name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search logs") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Plugin Filter Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showOnlyPlugins,
                            onCheckedChange = { showOnlyPlugins = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Show only plugin logs",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Statistics
                    if (logsResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatBadge("Errors", logsResult!!.errorCount, MaterialTheme.colorScheme.error)
                            StatBadge("Warnings", logsResult!!.warningCount, MaterialTheme.colorScheme.primary)
                            StatBadge("Total", logsResult!!.totalCount, MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            
            // Logs List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (logs.isEmpty()) "No logs available" else "No logs match filter",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs) { log ->
                        LogEntryCard(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogEntryCard(log: LogEntry) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val dateString = dateFormat.format(Date(log.timestamp))
    
    // Check if this is a plugin log (format: [pluginId] message)
    // Regex ensures we only match valid plugin IDs at the start of the message
    val pluginLogPattern = remember { Regex("""^\[([a-zA-Z0-9_.-]+)\]\s*""") }
    val pluginMatch = pluginLogPattern.find(log.message)
    val isPluginLog = pluginMatch != null
    val pluginId = pluginMatch?.groupValues?.get(1)
    
    val levelColor = when (log.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.primary
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant
        LogLevel.VERBOSE -> MaterialTheme.colorScheme.surfaceVariant
        LogLevel.ASSERT -> MaterialTheme.colorScheme.error
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isPluginLog) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header: Level, Tag, Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = levelColor.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = log.level.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = levelColor
                        )
                    }
                    if (isPluginLog && pluginId != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Plugin: $pluginId",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = log.tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Message
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Exception trace
            val throwableText = log.throwable
            if (!throwableText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = throwableText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
