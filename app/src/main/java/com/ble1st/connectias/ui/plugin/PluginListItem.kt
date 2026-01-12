package com.ble1st.connectias.ui.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.PluginManagerSandbox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginListItem(
    plugin: PluginManagerSandbox.PluginInfo,
    onToggleEnabled: () -> Unit,
    onShowDetails: () -> Unit,
    onShowPermissions: () -> Unit,
    onUninstall: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onShowDetails
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plugin Icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Plugin Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = plugin.metadata.pluginName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v${plugin.metadata.version} • ${plugin.metadata.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Status Badge
                PluginStatusBadge(state = plugin.state)
            }
            
            // Permission button (if plugin has permissions)
            val hasPermissions = (plugin.permissionInfo?.allPermissions?.size ?: 0) > 0
            if (hasPermissions) {
                IconButton(onClick = onShowPermissions) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Manage Permissions",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Enable/Disable Switch
            Switch(
                checked = plugin.state == PluginManagerSandbox.PluginState.ENABLED,
                onCheckedChange = { onToggleEnabled() }
            )
            
            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More options")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Details") },
                        onClick = {
                            showMenu = false
                            onShowDetails()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Info, null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Uninstall") },
                        onClick = {
                            showMenu = false
                            showUninstallDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null)
                        }
                    )
                }
            }
        }
    }
    
    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Uninstall Plugin?") },
            text = {
                Text("Are you sure you want to uninstall ${plugin.metadata.pluginName}? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallDialog = false
                        onUninstall()
                    }
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PluginStatusBadge(state: PluginManagerSandbox.PluginState) {
    val (text, color) = when (state) {
        PluginManagerSandbox.PluginState.ENABLED -> "Active" to MaterialTheme.colorScheme.primary
        PluginManagerSandbox.PluginState.LOADED -> "Loaded" to MaterialTheme.colorScheme.secondary
        PluginManagerSandbox.PluginState.DISABLED -> "Disabled" to MaterialTheme.colorScheme.outline
        PluginManagerSandbox.PluginState.ERROR -> "Error" to MaterialTheme.colorScheme.error
    }
    
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
