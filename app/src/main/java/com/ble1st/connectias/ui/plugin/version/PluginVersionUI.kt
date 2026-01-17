package com.ble1st.connectias.ui.plugin.version

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.R
import com.ble1st.connectias.plugin.version.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginVersionDialog(
    pluginId: String,
    currentVersion: PluginVersion?,
    availableUpdates: List<PluginVersionUpdate>,
    versionHistory: List<PluginVersionHistory>,
    canRollback: Boolean,
    onDismiss: () -> Unit,
    onUpdateClick: (PluginVersionUpdate) -> Unit,
    onRollbackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Current", "Updates", "History")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxSize(0.9f),
        title = {
            Text(
                text = "Plugin Version Management",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tab content
                when (selectedTab) {
                    0 -> CurrentVersionTab(currentVersion)
                    1 -> UpdatesTab(availableUpdates, onUpdateClick)
                    2 -> HistoryTab(versionHistory)
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1 && availableUpdates.isNotEmpty()) {
                TextButton(
                    onClick = { 
                        availableUpdates.firstOrNull()?.let { onUpdateClick(it) }
                    }
                ) {
                    Text("Update All")
                }
            } else if (selectedTab == 0 && canRollback) {
                TextButton(onClick = onRollbackClick) {
                    Text("Rollback")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CurrentVersionTab(
    currentVersion: PluginVersion?
) {
    if (currentVersion != null) {
        Column {
            VersionInfoCard(currentVersion)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Version Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoRow("Version Code", currentVersion.versionCode.toString())
            InfoRow("Release Date", formatDate(currentVersion.releaseDate))
            InfoRow("Min Host Version", currentVersion.minHostVersion)
            InfoRow("Size", formatBytes(currentVersion.size))
            InfoRow("Checksum", currentVersion.checksum.take(8) + "...")
            
            if (currentVersion.dependencies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dependencies:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                currentVersion.dependencies.forEach { dep ->
                    Text("• $dep", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            if (currentVersion.changelog.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Changelog:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(currentVersion.changelog, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Text(
            text = "No version information available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UpdatesTab(
    updates: List<PluginVersionUpdate>,
    onUpdateClick: (PluginVersionUpdate) -> Unit
) {
    if (updates.isNotEmpty()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(updates) { update ->
                UpdateCard(update, onUpdateClick)
            }
        }
    } else {
        Text(
            text = "No updates available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryTab(
    history: List<PluginVersionHistory>
) {
    if (history.isNotEmpty()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history) { entry ->
                HistoryCard(entry)
            }
        }
    } else {
        Text(
            text = "No version history available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VersionInfoCard(
    version: PluginVersion
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (version.isPrerelease) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = version.getDisplayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (version.isPrerelease) {
                    Text(
                        text = "BETA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                if (version.isRollback) {
                    Text(
                        text = "ROLLBACK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (version.isPrerelease) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This is a pre-release version. Use with caution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun UpdateCard(
    update: PluginVersionUpdate,
    onUpdateClick: (PluginVersionUpdate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = update.availableVersion.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "From ${update.currentVersion.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = updateTypeToString(update.updateType),
                        style = MaterialTheme.typography.bodySmall,
                        color = updateTypeColor(update.updateType)
                    )
                }
                
                if (update.isMandatory) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Mandatory Update",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { onUpdateClick(update) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update")
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: PluginVersionHistory
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = entry.version.getDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            InfoRow("Installed", formatDate(entry.installedAt))
            
            entry.uninstalledAt?.let { uninstalled ->
                InfoRow("Uninstalled", formatDate(uninstalled))
            }
            
            entry.rollbackReason?.let { reason ->
                InfoRow("Rollback Reason", reason)
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDate(date: Date): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024
    return "%.1f GB".format(gb)
}

private fun updateTypeToString(type: PluginVersionUpdate.UpdateType): String {
    return when (type) {
        PluginVersionUpdate.UpdateType.PATCH -> "Patch Update"
        PluginVersionUpdate.UpdateType.MINOR -> "Minor Update"
        PluginVersionUpdate.UpdateType.MAJOR -> "Major Update"
        PluginVersionUpdate.UpdateType.PRERELEASE -> "Pre-release"
    }
}

@Composable
private fun updateTypeColor(type: PluginVersionUpdate.UpdateType): androidx.compose.ui.graphics.Color {
    return when (type) {
        PluginVersionUpdate.UpdateType.PATCH -> MaterialTheme.colorScheme.primary
        PluginVersionUpdate.UpdateType.MINOR -> MaterialTheme.colorScheme.secondary
        PluginVersionUpdate.UpdateType.MAJOR -> MaterialTheme.colorScheme.error
        PluginVersionUpdate.UpdateType.PRERELEASE -> MaterialTheme.colorScheme.tertiary
    }
}
