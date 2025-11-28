package com.ble1st.connectias.feature.wasm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.wasm.plugin.models.PluginStatus
import com.ble1st.connectias.feature.wasm.plugin.models.WasmPlugin

/**
 * Jetpack Compose screen for plugin management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(
    viewModel: PluginManagerViewModel,
    onLoadPlugin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WASM Plugins") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onLoadPlugin,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("+")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error message
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
            
            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            // Plugin list
            if (uiState.hasPlugins) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.plugins) { plugin ->
                        PluginCard(
                            plugin = plugin,
                            onUnload = { viewModel.unloadPlugin(plugin.metadata.id) },
                            onExecute = { command, args ->
                                viewModel.executePlugin(plugin.metadata.id, command, args)
                            }
                        )
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No plugins loaded",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Card displaying plugin information.
 */
@Composable
fun PluginCard(
    plugin: WasmPlugin,
    onUnload: () -> Unit,
    onExecute: (String, Map<String, String>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = plugin.metadata.name,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "v${plugin.metadata.version}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = plugin.metadata.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            // Status
            StatusChip(status = plugin.status.get())
            
            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onExecute("hello", emptyMap()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Execute")
                }
                OutlinedButton(
                    onClick = onUnload,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Unload")
                }
            }
        }
    }
}

/**
 * Status chip for plugin status.
 */
@Composable
fun StatusChip(status: PluginStatus) {
    val (text, color) = when (status) {
        PluginStatus.LOADED -> "Loaded" to MaterialTheme.colorScheme.primary
        PluginStatus.READY -> "Ready" to MaterialTheme.colorScheme.primary
        PluginStatus.RUNNING -> "Running" to MaterialTheme.colorScheme.secondary
        PluginStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
        PluginStatus.UNLOADED -> "Unloaded" to MaterialTheme.colorScheme.outline
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

