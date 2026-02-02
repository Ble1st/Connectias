// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Adaptive Navigation für Plugin-Übersicht
 * 
 * Inspiriert von Now in Android's Adaptive Layout-Ansatz.
 * 
 * Features:
 * - List-Detail-Pattern für Tablets/Foldables
 * - Automatische Anpassung an Bildschirmgröße
 * - Unterstützung für verschiedene Postures (Laptop, Tablet, Handheld)
 * 
 * Verwendung:
 * ```kotlin
 * PluginListDetailScreen(
 *     plugins = availablePlugins,
 *     onPluginSelected = { pluginId -> /* ... */ }
 * )
 * ```
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PluginListDetailScreen(
    plugins: List<PluginItem>,
    selectedPluginId: String? = null,
    onPluginSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val coroutineScope = rememberCoroutineScope()
    
    // Synchronize selection with navigator
    LaunchedEffect(selectedPluginId) {
        selectedPluginId?.let {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it)
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            PluginListPane(
                plugins = plugins,
                selectedPluginId = selectedPluginId,
                onPluginClick = { pluginId ->
                    onPluginSelected(pluginId)
                    coroutineScope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, pluginId)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        },
        detailPane = {
            if (selectedPluginId != null) {
                val plugin = plugins.find { it.id == selectedPluginId }
                if (plugin != null) {
                    PluginDetailPane(
                        plugin = plugin,
                        onBack = {
                            coroutineScope.launch {
                                navigator.navigateBack()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PluginDetailEmptyPane()
                }
            } else {
                PluginDetailEmptyPane()
            }
        },
        modifier = modifier
    )
}

/**
 * Plugin-Liste (linke Seite auf Tablets)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginListPane(
    plugins: List<PluginItem>,
    selectedPluginId: String?,
    onPluginClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text("Plugins") }
        )
        
        if (plugins.isEmpty()) {
            PluginListEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plugins) { plugin ->
                    PluginListItem(
                        plugin = plugin,
                        isSelected = plugin.id == selectedPluginId,
                        onClick = { onPluginClick(plugin.id) }
                    )
                }
            }
        }
    }
}

/**
 * Plugin-Details (rechte Seite auf Tablets)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginDetailPane(
    plugin: PluginItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(plugin.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = plugin.name,
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodyLarge
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text("Version: ${plugin.version}") }
                )
                AssistChip(
                    onClick = { },
                    label = { Text("Author: ${plugin.author}") }
                )
            }
            
            HorizontalDivider()
            
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium
            )
            
            plugin.permissions.forEach { permission ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(permission)
                }
            }
        }
    }
}

@Composable
private fun PluginDetailEmptyPane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Select a plugin to view details",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PluginListEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No plugins installed",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Install plugins to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PluginListItem(
    plugin: PluginItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (plugin.isEnabled) {
                AssistChip(
                    onClick = { },
                    label = { Text("Enabled") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

// ============================================================================
// Data Models
// ============================================================================

/**
 * Plugin-Datenmodell für UI
 */
data class PluginItem(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val permissions: List<String>,
    val isEnabled: Boolean = false
)
