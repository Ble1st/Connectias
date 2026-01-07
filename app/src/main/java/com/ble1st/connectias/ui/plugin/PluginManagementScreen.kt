package com.ble1st.connectias.ui.plugin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.PluginImportHandler
import com.ble1st.connectias.plugin.PluginManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagementScreen(
    pluginManager: PluginManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var plugins by remember { mutableStateOf(pluginManager.getLoadedPlugins()) }
    var selectedPlugin by remember { mutableStateOf<PluginManager.PluginInfo?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val pluginImportHandler = remember {
        PluginImportHandler(
            context,
            pluginManager.getPlugin(plugins.firstOrNull()?.pluginId ?: "")?.pluginFile?.parentFile
                ?: context.filesDir.resolve("plugins"),
            pluginManager
        )
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isRefreshing = true
                val result = pluginImportHandler.importPlugin(uri)
                
                result.onSuccess { pluginId ->
                    importMessage = "Plugin imported successfully: $pluginId"
                    importError = false
                    showImportDialog = true
                    
                    // Reload plugins
                    pluginManager.initialize()
                    plugins = pluginManager.getLoadedPlugins()
                }.onFailure { error ->
                    importMessage = "Import failed: ${error.message}"
                    importError = true
                    showImportDialog = true
                }
                
                isRefreshing = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isRefreshing = true
                                pluginManager.initialize()
                                plugins = pluginManager.getLoadedPlugins()
                                isRefreshing = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch("*/*")
                }
            ) {
                Icon(Icons.Default.Add, "Add Plugin")
            }
        }
    ) { padding ->
        if (isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (plugins.isEmpty()) {
            EmptyPluginState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(plugins) { plugin ->
                    PluginListItem(
                        plugin = plugin,
                        onToggleEnabled = {
                            scope.launch {
                                if (plugin.state == PluginManager.PluginState.ENABLED) {
                                    pluginManager.disablePlugin(plugin.pluginId)
                                } else {
                                    pluginManager.enablePlugin(plugin.pluginId)
                                }
                                plugins = pluginManager.getLoadedPlugins()
                            }
                        },
                        onShowDetails = {
                            selectedPlugin = plugin
                            showDetailDialog = true
                        },
                        onUninstall = {
                            scope.launch {
                                pluginManager.unloadPlugin(plugin.pluginId)
                                plugins = pluginManager.getLoadedPlugins()
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showDetailDialog && selectedPlugin != null) {
        PluginDetailDialog(
            plugin = selectedPlugin!!,
            onDismiss = { showDetailDialog = false }
        )
    }
    
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = {
                Icon(
                    if (importError) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null
                )
            },
            title = {
                Text(if (importError) "Import Failed" else "Import Successful")
            },
            text = {
                Text(importMessage)
            },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun EmptyPluginState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Plugins Installed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add plugins to extend functionality",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
