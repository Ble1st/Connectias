package com.ble1st.connectias.ui.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.PluginManagerSandbox
import com.ble1st.connectias.plugin.PluginPermissionManager
import timber.log.Timber

/**
 * Detailed permission management screen for a single plugin
 * Shows all requested permissions and allows enabling/disabling them
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPermissionDetailScreen(
    plugin: PluginManagerSandbox.PluginInfo,
    permissionManager: PluginPermissionManager,
    onDismiss: () -> Unit,
    onPermissionsChanged: () -> Unit
) {
    val permissions = remember(plugin) {
        plugin.permissionInfo?.allPermissions ?: plugin.metadata.permissions
    }
    
    val dangerousPermissions = remember(plugin) {
        plugin.permissionInfo?.dangerous ?: permissionManager.getDangerousPermissions(permissions)
    }
    
    val criticalPermissions = remember(plugin) {
        plugin.permissionInfo?.critical ?: permissionManager.getCriticalPermissions(permissions)
    }
    
    // Track which permissions are granted (user consent only, not host app permissions)
    // Use derivedStateOf to make it reactive to permission changes
    val permissionStates = remember(plugin) {
        permissions.associateWith { permission ->
            mutableStateOf(permissionManager.hasUserConsentForPermission(plugin.pluginId, permission))
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = plugin.metadata.pluginName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Permission Management",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (permissions.isEmpty()) {
            // No permissions requested
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "No Permissions Requested",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "This plugin doesn't require any special permissions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Summary Card
                item {
                    PermissionSummaryCard(
                        totalPermissions = permissions.size,
                        dangerousCount = dangerousPermissions.size,
                        criticalCount = criticalPermissions.size
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Critical Permissions Section (if any)
                if (criticalPermissions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Critical Permissions (Blocked)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(criticalPermissions) { permission ->
                        PermissionDetailItem(
                            permission = permission,
                            isGranted = false,
                            isDangerous = false,
                            isCritical = true,
                            enabled = false, // Critical permissions cannot be toggled
                            onToggle = { /* Cannot toggle critical permissions */ }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Dangerous Permissions Section (if any)
                if (dangerousPermissions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Dangerous Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(dangerousPermissions) { permission ->
                        val state = permissionStates[permission]
                        if (state != null) {
                            PermissionDetailItem(
                                permission = permission,
                                isGranted = state.value,
                                isDangerous = true,
                                isCritical = false,
                                enabled = true,
                                onToggle = { newValue ->
                                    if (newValue) {
                                        permissionManager.grantUserConsent(plugin.pluginId, listOf(permission))
                                        Timber.i("Granted permission $permission to ${plugin.pluginId}")
                                    } else {
                                        permissionManager.revokeUserConsent(plugin.pluginId, listOf(permission))
                                        Timber.i("Revoked permission $permission from ${plugin.pluginId}")
                                    }
                                    state.value = newValue
                                    onPermissionsChanged()
                                }
                            )
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Normal Permissions Section
                val normalPermissions = permissions.filter { 
                    it !in dangerousPermissions && it !in criticalPermissions 
                }
                
                if (normalPermissions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Normal Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(normalPermissions) { permission ->
                        val state = permissionStates[permission]
                        if (state != null) {
                            PermissionDetailItem(
                                permission = permission,
                                isGranted = state.value,
                                isDangerous = false,
                                isCritical = false,
                                enabled = true,
                                onToggle = { newValue ->
                                    if (newValue) {
                                        permissionManager.grantUserConsent(plugin.pluginId, listOf(permission))
                                        Timber.i("Granted normal permission $permission to ${plugin.pluginId}")
                                    } else {
                                        permissionManager.revokeUserConsent(plugin.pluginId, listOf(permission))
                                        Timber.i("Revoked normal permission $permission from ${plugin.pluginId}")
                                    }
                                    state.value = newValue
                                    onPermissionsChanged()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Summary card showing permission statistics
 */
@Composable
private fun PermissionSummaryCard(
    totalPermissions: Int,
    dangerousCount: Int,
    criticalCount: Int
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
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PermissionStatItem(
                icon = Icons.Default.Info,
                label = "Total",
                count = totalPermissions,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            PermissionStatItem(
                icon = Icons.Default.Warning,
                label = "Dangerous",
                count = dangerousCount,
                color = MaterialTheme.colorScheme.error
            )
            
            PermissionStatItem(
                icon = Icons.Default.Block,
                label = "Blocked",
                count = criticalCount,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Single stat item in the summary card
 */
@Composable
private fun PermissionStatItem(
    icon: ImageVector,
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Detailed item for a single permission
 */
@Composable
private fun PermissionDetailItem(
    permission: String,
    isGranted: Boolean,
    isDangerous: Boolean,
    isCritical: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val (icon, label, description) = getPermissionInfo(permission)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCritical -> MaterialTheme.colorScheme.errorContainer
                isDangerous && !isGranted -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = when {
                    isCritical -> MaterialTheme.colorScheme.error
                    isDangerous -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            
            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = permission.substringAfterLast("."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            
            // Toggle or Status
            if (isCritical) {
                // Critical permissions cannot be toggled
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Blocked",
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                // ALL permissions (dangerous and normal) can be toggled by user
                Switch(
                    checked = isGranted,
                    enabled = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        disabledCheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )
            }
        }
    }
}
