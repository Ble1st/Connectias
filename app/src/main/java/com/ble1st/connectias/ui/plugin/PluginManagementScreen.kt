package com.ble1st.connectias.ui.plugin

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    onNavigateToPermissions: (String) -> Unit = {},
    onNavigateToSecurity: (String) -> Unit = {},
    onNavigateToNetworkPolicy: (String) -> Unit = {},
    onNavigateToSecurityAudit: (String) -> Unit = {},
    onNavigateToStore: () -> Unit = {},
    onNavigateToSecurityDashboard: () -> Unit = {},
    onNavigateToPrivacyDashboard: () -> Unit = {},
    onNavigateToAnalyticsDashboard: () -> Unit = {},
    onNavigateToDeclarativeBuilder: () -> Unit = {},
    onNavigateToDeveloperKeys: () -> Unit = {},
    onNavigateToDeclarativeRuns: () -> Unit = {}
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
    
    // Android system permission request state
    var pendingAndroidPermissionPlugin by remember { mutableStateOf<PluginManagerSandbox.PluginInfo?>(null) }
    var pendingAndroidPermissions by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingCustomPermissions by remember { mutableStateOf<List<String>>(emptyList()) }

    // Main FAB menu state (dashboards + store + import)
    var showFabMenu by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Launcher for Android system permission requests
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val plugin = pendingAndroidPermissionPlugin
        if (plugin != null) {
            val grantedPermissions = permissions.filter { it.value }.keys.toList()
            val deniedPermissions = permissions.filter { !it.value }.keys.toList()
            
            Timber.i("Android permission request result for ${plugin.pluginId}:")
            Timber.i("  Granted: $grantedPermissions")
            Timber.i("  Denied: $deniedPermissions")

            // Grant user consent for granted Android permissions
            if (grantedPermissions.isNotEmpty()) {
                permissionManager.grantUserConsent(plugin.pluginId, grantedPermissions)
            }

            // Also grant custom permissions (they don't need Android runtime request)
            if (pendingCustomPermissions.isNotEmpty()) {
                permissionManager.grantUserConsent(plugin.pluginId, pendingCustomPermissions)
            }

            // FIXED: Only try enabling plugin if ALL requested permissions were granted
            // This prevents endless permission request loop when user denies some permissions
            if (deniedPermissions.isEmpty()) {
                // All permissions granted - try to enable plugin
                scope.launch {
                    val result = pluginManager.enablePlugin(plugin.pluginId)
                    result.onSuccess {
                        Timber.i("Plugin enabled after permission grant: ${plugin.pluginId}")
                    }.onFailure { error ->
                        Timber.e(error, "Failed to enable plugin after permission grant: ${plugin.pluginId}")
                    }
                }
            } else {
                // Some permissions denied - show message to user
                Timber.w("Plugin ${plugin.pluginId} cannot be enabled - user denied permissions: $deniedPermissions")
                // The plugin remains in LOADED state and can be toggled later if user changes their mind
            }
            
            // Clear pending state
            pendingAndroidPermissionPlugin = null
            pendingAndroidPermissions = emptyList()
            pendingCustomPermissions = emptyList()
        }
    }
    
    val pluginImportHandler = remember {
        PluginImportHandler(
            context,
            pluginManager.getPlugin(plugins.firstOrNull()?.pluginId ?: "")?.pluginFile?.parentFile
                ?: context.filesDir.resolve("plugins"),
            manifestParser
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

                    // FIXED: Only load the plugin, don't enable it automatically
                    // This prevents endless permission request loop if user denies permissions
                    // User can enable the plugin manually via the toggle button in the UI
                    // NOTE: Despite the name, loadAndEnablePlugin() only LOADS new plugins (doesn't enable)
                    //       See PluginManagerSandbox.kt:916 - "Plugin is loaded but not enabled"
                    val loadResult = pluginManager.loadAndEnablePlugin(pluginId)
                    loadResult.onSuccess { metadata ->
                        importMessage = "Plugin imported and loaded: $pluginId\nYou can enable it by toggling the switch."

                        // Register plugin in module registry (inactive) so it appears in the list
                        withContext(Dispatchers.Main) {
                            val moduleInfo = ModuleInfo(
                                id = metadata.pluginId,
                                name = metadata.pluginName,
                                version = metadata.version,
                                isActive = false  // Inactive - user must enable manually
                            )
                            moduleRegistry.registerModule(moduleInfo)
                            Timber.i("Plugin registered in module registry (inactive): ${metadata.pluginName}")
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Default.Menu, "Menu")
                }

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    // Dashboards (only these three)
                    DropdownMenuItem(
                        text = { Text("Security Dashboard") },
                        onClick = {
                            showFabMenu = false
                            onNavigateToSecurityDashboard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Privacy Dashboard") },
                        onClick = {
                            showFabMenu = false
                            onNavigateToPrivacyDashboard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Plugin Analytics") },
                        onClick = {
                            showFabMenu = false
                            onNavigateToAnalyticsDashboard()
                        }
                    )

                    Divider()

                    // Keep store + import
                    DropdownMenuItem(
                        text = { Text("Plugin Store") },
                        onClick = {
                            showFabMenu = false
                            onNavigateToStore()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import Plugin") },
                        onClick = {
                            showFabMenu = false
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Declarative Plugins (Beta)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Erstelle einfache UI+Workflows direkt am Gerät und exportiere als signiertes .cplug.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = onNavigateToDeclarativeBuilder, modifier = Modifier.weight(1f)) {
                                    Text("Builder")
                                }
                                OutlinedButton(onClick = onNavigateToDeveloperKeys, modifier = Modifier.weight(1f)) {
                                    Text("Developer Keys")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(onClick = onNavigateToDeclarativeRuns, modifier = Modifier.fillMaxWidth()) {
                                Text("Flow Runs (Viewer)")
                            }
                        }
                    }
                }

                if (plugins.isEmpty()) {
                    item {
                        EmptyPluginState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                } else {
                    items(plugins) { plugin ->
                        PluginListItem(
                            plugin = plugin,
                            onShowPermissions = {
                                onNavigateToPermissions(plugin.pluginId)
                            },
                            onShowSecurity = {
                                onNavigateToSecurity(plugin.pluginId)
                            },
                            onShowNetworkPolicy = {
                                onNavigateToNetworkPolicy(plugin.pluginId)
                            },
                            onShowSecurityAudit = {
                                onNavigateToSecurityAudit(plugin.pluginId)
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
                                                val requiredPermissions = error.requiredPermissions
                                                Timber.i("Plugin requires permissions: $requiredPermissions")

                                                // Separate Android system permissions from custom permissions
                                                val androidPermissions = requiredPermissions.filter { permission ->
                                                    isAndroidSystemPermission(permission)
                                                }
                                                val customPermissions = requiredPermissions.filter { permission ->
                                                    !isAndroidSystemPermission(permission)
                                                }

                                                if (androidPermissions.isNotEmpty()) {
                                                    // Request Android system permissions via native dialog
                                                    // NOTE: LocalContext.current is often a ContextThemeWrapper, not an Activity.
                                                    // We do not need an Activity here: the ActivityResult API will route the request
                                                    // through the host Activity automatically, and checkSelfPermission works with Context.

                                                    // Check which permissions are not yet granted
                                                    val permissionsToRequest = androidPermissions.filter { permission ->
                                                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                                                    }

                                                    if (permissionsToRequest.isNotEmpty()) {
                                                        // Store pending state
                                                        pendingAndroidPermissionPlugin = plugin
                                                        pendingAndroidPermissions = permissionsToRequest
                                                        pendingCustomPermissions = customPermissions

                                                        // Request via native Android dialog
                                                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                                    } else {
                                                        // All Android permissions already granted at the OS level.
                                                        // BUT: User must still explicitly consent to the plugin using these permissions.
                                                        // Show custom dialog for plugin-level consent.
                                                        permissionDialogPlugin = plugin
                                                        permissionDialogPermissions = androidPermissions + customPermissions
                                                        showPermissionDialog = true
                                                    }
                                                } else {
                                                    // No Android system permissions, use custom dialog
                                                    permissionDialogPlugin = plugin
                                                    permissionDialogPermissions = requiredPermissions
                                                    showPermissionDialog = true
                                                }
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

/**
 * Checks if a permission is an Android system permission that requires runtime request.
 * These are permissions that must be requested via the Android runtime permission dialog.
 */
private fun isAndroidSystemPermission(permission: String): Boolean {
    // Android system permissions that require runtime request (dangerous permissions)
    val androidSystemPermissions = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.BODY_SENSORS",
        "android.permission.BODY_SENSORS_BACKGROUND"
    )
    
    return permission in androidSystemPermissions
}
