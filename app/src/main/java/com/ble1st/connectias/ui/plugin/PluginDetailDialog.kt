package com.ble1st.connectias.ui.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.PluginManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PluginDetailDialog(
    plugin: PluginManager.PluginInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = plugin.metadata.pluginName,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Basic Info
                DetailSection(title = "Basic Information") {
                    DetailRow("Version", plugin.metadata.version)
                    DetailRow("Author", plugin.metadata.author)
                    DetailRow("Category", plugin.metadata.category.name)
                    DetailRow(
                        "Loaded",
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                            .format(Date(plugin.loadedAt))
                    )
                }
                
                // Description
                if (plugin.metadata.description.isNotEmpty()) {
                    DetailSection(title = "Description") {
                        Text(
                            text = plugin.metadata.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // API Requirements
                DetailSection(title = "Requirements") {
                    DetailRow("Min API Level", plugin.metadata.minApiLevel.toString())
                    DetailRow("Max API Level", plugin.metadata.maxApiLevel.toString())
                    DetailRow("Min App Version", plugin.metadata.minAppVersion)
                }
                
                // Permissions
                if (plugin.metadata.permissions.isNotEmpty()) {
                    DetailSection(title = "Permissions") {
                        plugin.metadata.permissions.forEach { permission ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = permission.substringAfterLast("."),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Dependencies
                if (plugin.metadata.dependencies.isNotEmpty()) {
                    DetailSection(title = "Dependencies") {
                        plugin.metadata.dependencies.forEach { dep ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = dep,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Native Libraries
                if (plugin.metadata.nativeLibraries.isNotEmpty()) {
                    DetailSection(title = "Native Libraries") {
                        plugin.metadata.nativeLibraries.forEach { lib ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = lib,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Technical Info
                DetailSection(title = "Technical Information") {
                    DetailRow("Plugin ID", plugin.pluginId)
                    DetailRow("File", plugin.pluginFile.name)
                    DetailRow("State", plugin.state.name)
                    plugin.metadata.fragmentClassName?.let {
                        DetailRow("Main Class", it)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Divider()
        content()
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
