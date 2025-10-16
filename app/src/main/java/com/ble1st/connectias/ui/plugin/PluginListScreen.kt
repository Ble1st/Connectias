package com.ble1st.connectias.ui.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.storage.database.entity.PluginEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginListScreen(
    plugins: List<PluginEntity>,
    onPluginClick: (String) -> Unit,
    onInstallPlugin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Manager") },
                actions = {
                    IconButton(onClick = onInstallPlugin) {
                        Icon(Icons.Filled.Add, contentDescription = "Install Plugin")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (plugins.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Keine Plugins installiert",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Tippen Sie auf + um ein Plugin zu installieren",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                items(plugins) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        onClick = { onPluginClick(plugin.id) }
                    )
                }
            }
        }
    }
}
