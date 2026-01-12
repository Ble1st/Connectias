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
import com.ble1st.connectias.core.module.ModuleInfo
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.plugin.PluginImportHandler
import com.ble1st.connectias.plugin.PluginManagerSandbox
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.PluginPermissionException
import com.ble1st.connectias.plugin.PluginManifestParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagementScreen(
    pluginManager: PluginManagerSandbox,
    moduleRegistry: ModuleRegistry,
    permissionManager: PluginPermissionManager,
    manifestParser: PluginManifestParser,
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: (String) -> Unit
) {
    val context = LocalContext.current
    val plugins by pluginManager.pluginsFlow.collectAsStateWithLifecycle()
    var selectedPlugin by remember { mutableStateOf<PluginManagerSandbox.PluginInfo?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }
    
    // Permission dialog state
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDialogPlugin by remember { mutableStateOf<PluginManagerSandbox.PluginInfo?>(null) }
    var permissionDialogPermissions by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    
    val pluginImportHandler = remember {
        PluginImportHandler(
            context,
            pluginManager.getPlugin(plugins.firstOrNull()?.pluginId ?: "")?.pluginFile?.parentFile
                ?: context.filesDir.resolve("plugins"),
            pluginManager,
            manifestParser,
            permissionManager
        )
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                val result = pluginImportHandler.importPlugin(uri)
                
                result.onSuccess { pluginId ->
                    importMessage = "Plugin imported successfully: $pluginId"
                    importError = false
                    showImportDialog = true
                    
                    // Load and enable the newly imported plugin immediately
                    val loadResult = pluginManager.loadAndEnablePlugin(pluginId)
                    loadResult.onSuccess { metadata ->
                        importMessage = "Plugin imported, loaded and enabled: $pluginId"
                        
                        // Register plugin in module registry so it appears in navigation
                        withContext(Dispatchers.Main) {
                            val moduleInfo = ModuleInfo(
                                id = metadata.pluginId,
                                name = metadata.pluginName,
                                version = metadata.version,
                                isActive = true
                            )
                            moduleRegistry.registerModule(moduleInfo)
                            Timber.i("Plugin registered in module registry: ${metadata.pluginName}")
                        }
                    }.onFailure { loadError ->
                        Timber.e(loadError, "Failed to load plugin after import")
                        importMessage = "Plugin imported but failed to load: ${loadError.message}"
                    }
                    
                    // No need to manually refresh - StateFlow updates automatically
                }.onFailure { error ->
                    importMessage = "Import failed: ${error.message}"
                    importError = true
                    showImportDialog = true
                }
                
                isImporting = false
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
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Importing plugin...")
                }
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
                        onShowPermissions = {
                            onNavigateToPermissions(plugin.pluginId)
                        },
                        onToggleEnabled = {
                            scope.launch {
                                if (plugin.state == PluginManagerSandbox.PluginState.ENABLED) {
                                    pluginManager.disablePlugin(plugin.pluginId)
                                } else {
                                    // Try to enable plugin
                                    val result = pluginManager.enablePlugin(plugin.pluginId)
                                    result.onFailure { error ->
                                        // Check if it's a permission error
                                        if (error is PluginPermissionException) {
                                            // Show permission dialog
                                            permissionDialogPlugin = plugin
                                            permissionDialogPermissions = error.requiredPermissions
                                            showPermissionDialog = true
                                            Timber.i("Plugin requires permissions: ${error.requiredPermissions}")
                                        } else {
                                            // Other error - log it
                                            Timber.e(error, "Failed to enable plugin: ${plugin.pluginId}")
                                        }
                                    }
                                }
                                // No need to manually refresh - StateFlow updates automatically
                                // ModuleRegistry is automatically updated in PluginManagerSandbox
                            }
                        },
                        onShowDetails = {
                            selectedPlugin = plugin
                            showDetailDialog = true
                        },
                        onUninstall = {
                            scope.launch {
                                pluginManager.unloadPlugin(plugin.pluginId)
                                // No need to manually refresh - StateFlow updates automatically
                                // ModuleRegistry is automatically updated in PluginManagerSandbox
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Permission consent dialog
    if (showPermissionDialog && permissionDialogPlugin != null) {
        PluginPermissionDialog(
            pluginName = permissionDialogPlugin!!.metadata.pluginName,
            permissions = permissionDialogPermissions,
            onDismiss = {
                showPermissionDialog = false
                permissionDialogPlugin = null
                permissionDialogPermissions = emptyList()
            },
            onGrant = {
                val plugin = permissionDialogPlugin
                if (plugin != null) {
                    // Grant permissions
                    permissionManager.grantUserConsent(plugin.pluginId, permissionDialogPermissions)
                    
                    // Try enabling plugin again
                    scope.launch {
                        val result = pluginManager.enablePlugin(plugin.pluginId)
                        result.onSuccess {
                            Timber.i("Plugin enabled after permission grant: ${plugin.pluginId}")
                        }.onFailure { error ->
                            Timber.e(error, "Failed to enable plugin after permission grant: ${plugin.pluginId}")
                        }
                    }
                }
                
                showPermissionDialog = false
                permissionDialogPlugin = null
                permissionDialogPermissions = emptyList()
            }
        )
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
