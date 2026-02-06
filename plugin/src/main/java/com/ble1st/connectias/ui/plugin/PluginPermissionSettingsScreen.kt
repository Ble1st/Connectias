package com.ble1st.connectias.ui.plugin

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
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.PluginPermissionManager.Companion.HardwareCategory
import java.text.SimpleDateFormat
import java.util.*

/**
 * Plugin Permission Settings Screen
 * 
 * Shows all hardware permissions for a plugin and allows user to:
 * - View granted permissions
 * - Revoke permissions
 * - View hardware access log (audit trail)
 * 
 * SECURITY:
 * - User can revoke any permission at any time
 * - Shows when each hardware was last accessed
 * - Clear audit trail of hardware usage
 * 
 * @param pluginId Plugin ID
 * @param pluginName Plugin name
 * @param permissionManager Permission manager instance
 * @param onNavigateBack Callback to navigate back
 * 
 * @since 2.0.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPermissionSettingsScreen(
    pluginId: String,
    pluginName: String,
    permissionManager: PluginPermissionManager,
    onNavigateBack: () -> Unit
) {
    var showRevokeDialog by remember { mutableStateOf(false) }
    var categoryToRevoke by remember { mutableStateOf<HardwareCategory?>(null) }
    
    // Get all hardware categories this plugin has permissions for
    val grantedCategories = remember(pluginId) {
        HardwareCategory.values().filter { category ->
            permissionManager.hasHardwareAccess(pluginId, category)
        }
    }
    
    // Get hardware access log
    val accessLog = remember(pluginId) {
        permissionManager.getHardwareAccessLog(pluginId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware-Berechtigungen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plugin info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = pluginName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Plugin ID: $pluginId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Granted permissions section
            item {
                Text(
                    text = "Aktive Berechtigungen",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (grantedCategories.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Keine Hardware-Berechtigungen erteilt")
                        }
                    }
                }
            } else {
                items(grantedCategories) { category ->
                    GrantedPermissionCard(
                        category = category,
                        description = permissionManager.getHardwareCategoryDescription(category),
                        lastAccessed = accessLog.firstOrNull { it.first == category }?.second,
                        onRevoke = {
                            categoryToRevoke = category
                            showRevokeDialog = true
                        }
                    )
                }
            }
            
            // Access log section
            if (accessLog.isNotEmpty()) {
                item {
                    Text(
                        text = "Zugriffsverlauf",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(accessLog.take(10)) { (category, timestamp) ->
                    AccessLogCard(
                        category = category,
                        timestamp = timestamp
                    )
                }
                
                if (accessLog.size > 10) {
                    item {
                        Text(
                            text = "... und ${accessLog.size - 10} weitere Zugriffe",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Revoke confirmation dialog
    if (showRevokeDialog && categoryToRevoke != null) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Berechtigung widerrufen?") },
            text = {
                Text("Möchtest du die ${categoryToRevoke!!.name}-Berechtigung für $pluginName widerrufen? Das Plugin kann dann nicht mehr auf diese Hardware zugreifen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        categoryToRevoke?.let { category ->
                            // Get all permissions for this category
                            val permsToRevoke = PluginPermissionManager.HARDWARE_PERMISSION_MAP
                                .filter { it.value == category }
                                .keys
                                .toList()
                            
                            permissionManager.revokeUserConsent(pluginId, permsToRevoke)
                        }
                        showRevokeDialog = false
                        categoryToRevoke = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Widerrufen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRevokeDialog = false
                    categoryToRevoke = null
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

/**
 * Card showing granted permission with revoke button
 */
@Composable
private fun GrantedPermissionCard(
    category: HardwareCategory,
    description: String,
    lastAccessed: Long?,
    onRevoke: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getHardwareIcon(category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (lastAccessed != null) {
                    Text(
                        text = "Zuletzt: ${formatTimestamp(lastAccessed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onRevoke,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Widerrufen"
                )
            }
        }
    }
}

/**
 * Card showing single access log entry
 */
@Composable
private fun AccessLogCard(
    category: HardwareCategory,
    timestamp: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getHardwareIcon(category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatTimestamp(timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Get icon for hardware category
 */
private fun getHardwareIcon(category: HardwareCategory) = when (category) {
    HardwareCategory.CAMERA -> Icons.Filled.CameraAlt
    HardwareCategory.NETWORK -> Icons.Filled.Wifi
    HardwareCategory.PRINTER -> Icons.Filled.Print
    HardwareCategory.BLUETOOTH -> Icons.Filled.Bluetooth
    HardwareCategory.STORAGE -> Icons.Filled.Storage
    HardwareCategory.LOCATION -> Icons.Filled.LocationOn
    HardwareCategory.AUDIO -> Icons.Filled.Mic
    HardwareCategory.SENSORS -> Icons.Filled.Sensors
}

/**
 * Format timestamp to human-readable string
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}
