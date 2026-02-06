package com.ble1st.connectias.ui.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.PluginPermissionManager.Companion.HardwareCategory

/**
 * Hardware Permission Request Dialog
 * 
 * Shows hardware permissions required by a plugin and allows user to grant/deny.
 * 
 * SECURITY:
 * - All permissions are OFF by default
 * - User must explicitly enable each hardware category
 * - Shows clear descriptions of what each permission does
 * 
 * @param pluginId Plugin requesting permissions
 * @param pluginName Human-readable plugin name
 * @param requiredCategories Hardware categories needed by plugin
 * @param permissionManager Permission manager instance
 * @param onDismiss Callback when dialog is closed without granting
 * @param onGranted Callback when permissions are granted
 * 
 * @since 2.0.0
 */
@Composable
fun HardwarePermissionDialog(
    pluginId: String,
    pluginName: String,
    requiredCategories: Set<HardwareCategory>,
    permissionManager: PluginPermissionManager,
    onDismiss: () -> Unit,
    onGranted: () -> Unit
) {
    // Track enabled state for each category (default: OFF)
    val enabledCategories = remember {
        mutableStateMapOf<HardwareCategory, Boolean>().apply {
            requiredCategories.forEach { category ->
                put(category, false) // Default: OFF
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Hardware-Berechtigungen",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "$pluginName benötigt Zugriff auf folgende Hardware:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Warning about security
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alle Berechtigungen sind standardmäßig DEAKTIVIERT. Aktiviere nur, was du dem Plugin erlauben möchtest.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // List of required categories
                requiredCategories.forEach { category ->
                    HardwarePermissionItem(
                        category = category,
                        description = permissionManager.getHardwareCategoryDescription(category),
                        enabled = enabledCategories[category] ?: false,
                        onToggle = { enabled ->
                            enabledCategories[category] = enabled
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Grant only enabled categories
                    val enabledPerms = enabledCategories
                        .filter { it.value }
                        .keys
                        .flatMap { category ->
                            // Get all permissions for this category
                            PluginPermissionManager.HARDWARE_PERMISSION_MAP
                                .filter { it.value == category }
                                .keys
                        }
                    
                    if (enabledPerms.isNotEmpty()) {
                        permissionManager.grantUserConsent(pluginId, enabledPerms)
                        onGranted()
                    } else {
                        // No permissions granted - treat as dismissal
                        onDismiss()
                    }
                }
            ) {
                Text("Bestätigen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

/**
 * Single hardware permission item with toggle
 */
@Composable
private fun HardwarePermissionItem(
    category: HardwareCategory,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
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
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Get icon for hardware category
 */
private fun getHardwareIcon(category: HardwareCategory): ImageVector {
    return when (category) {
        HardwareCategory.CAMERA -> Icons.Filled.CameraAlt
        HardwareCategory.NETWORK -> Icons.Filled.Wifi
        HardwareCategory.PRINTER -> Icons.Filled.Print
        HardwareCategory.BLUETOOTH -> Icons.Filled.Bluetooth
        HardwareCategory.STORAGE -> Icons.Filled.Storage
        HardwareCategory.LOCATION -> Icons.Filled.LocationOn
        HardwareCategory.AUDIO -> Icons.Filled.Mic
        HardwareCategory.SENSORS -> Icons.Filled.Sensors
    }
}
