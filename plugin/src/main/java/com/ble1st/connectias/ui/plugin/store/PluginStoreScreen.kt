package com.ble1st.connectias.ui.plugin.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.sdk.PluginCategory
import com.ble1st.connectias.plugin.store.GitHubPluginStore
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(
    pluginStore: GitHubPluginStore,
    onNavigateBack: () -> Unit,
    onNavigateToPluginDetails: (String) -> Unit = {},
    onNavigateToPluginManagement: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var plugins by remember { mutableStateOf<List<GitHubPluginStore.StorePlugin>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<PluginCategory?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var installingPlugin by remember { mutableStateOf<GitHubPluginStore.StorePlugin?>(null) }
    var isInstalling by remember { mutableStateOf(false) }
    
    // Load plugins on first composition
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = pluginStore.getAvailablePlugins()
                result.onSuccess { 
                    plugins = it
                    Timber.d("Loaded ${it.size} plugins from store")
                }.onFailure { error ->
                    errorMessage = error.message ?: "Failed to load plugins"
                    Timber.e(error, "Failed to load plugins from store")
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    // Filter plugins based on search and category
    val filteredPlugins = remember(searchQuery, selectedCategory, plugins) {
        plugins.filter { plugin ->
            val matchesSearch = searchQuery.isEmpty() || 
                plugin.name.contains(searchQuery, ignoreCase = true) ||
                plugin.description.contains(searchQuery, ignoreCase = true) ||
                plugin.id.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = selectedCategory == null || plugin.category == selectedCategory
            
            matchesSearch && matchesCategory
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Store") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                val result = pluginStore.getAvailablePlugins()
                                result.onSuccess { 
                                    plugins = it
                                    Timber.d("Loaded ${it.size} plugins from store")
                                }.onFailure { error ->
                                    errorMessage = error.message ?: "Failed to load plugins"
                                    Timber.e(error, "Failed to load plugins from store")
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading plugins...")
                }
            }
        } else if (errorMessage != null) {
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
                        Icons.Default.Error,
                        "Error",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { 
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                val result = pluginStore.getAvailablePlugins()
                                result.onSuccess { 
                                    plugins = it
                                    Timber.d("Loaded ${it.size} plugins from store")
                                }.onFailure { error ->
                                    errorMessage = error.message ?: "Failed to load plugins"
                                    Timber.e(error, "Failed to load plugins from store")
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search plugins...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search")
                    },
                    singleLine = true
                )
                
                // Category filter
                CategoryFilterRow(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                // Plugin list
                if (filteredPlugins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedCategory != null) {
                                "No plugins found"
                            } else {
                                "No plugins available"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredPlugins) { plugin ->
                            PluginStoreItem(
                                plugin = plugin,
                                onInstall = {
                                    installingPlugin = plugin
                                    showInstallDialog = true
                                },
                                onDetails = {
                                    onNavigateToPluginDetails(plugin.id)
                                },
                                onActivate = {
                                    onNavigateToPluginManagement()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Install confirmation dialog
    if (showInstallDialog && installingPlugin != null) {
        AlertDialog(
            onDismissRequest = { 
                showInstallDialog = false
                installingPlugin = null
            },
            icon = {
                Icon(Icons.Default.Download, "Install")
            },
            title = {
                Text("Install Plugin")
            },
            text = {
                Column {
                    Text("Do you want to install ${installingPlugin!!.name}?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Version: ${installingPlugin!!.version}\nSize: ${formatFileSize(installingPlugin!!.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isInstalling = true
                            try {
                                val result = pluginStore.downloadAndInstallPlugin(installingPlugin!!)
                                result.onSuccess {
                                    // Update plugin status
                                    plugins = plugins.map { 
                                        if (it.id == installingPlugin!!.id) {
                                            it.copy(
                                                isInstalled = true,
                                                installedVersion = installingPlugin!!.version,
                                                canUpdate = false
                                            )
                                        } else it
                                    }
                                    Timber.i("Plugin installed successfully: ${installingPlugin!!.name}")
                                }.onFailure { error ->
                                    Timber.e(error, "Failed to install plugin: ${installingPlugin!!.name}")
                                    // Show error message (could use a snackbar here)
                                }
                            } finally {
                                isInstalling = false
                                showInstallDialog = false
                                installingPlugin = null
                            }
                        }
                    },
                    enabled = !isInstalling
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showInstallDialog = false
                        installingPlugin = null
                    },
                    enabled = !isInstalling
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: PluginCategory?,
    onCategorySelected: (PluginCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        null to "All",
        PluginCategory.UTILITY to "Utility",
        PluginCategory.NETWORK to "Network",
        PluginCategory.SECURITY to "Security",
        PluginCategory.MEDIA to "Media"
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (category, name) ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(name) }
            )
        }
    }
}

@Composable
private fun PluginStoreItem(
    plugin: GitHubPluginStore.StorePlugin,
    onInstall: () -> Unit,
    onDetails: () -> Unit,
    onActivate: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${plugin.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Status badges
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (plugin.isInstalled) {
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Installed",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        // Add note about activation
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Needs activation",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (plugin.canUpdate) {
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Update",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatFileSize(plugin.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDetails,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Details")
                    }
                    
                    if (plugin.canUpdate) {
                        Button(
                            onClick = onInstall,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update")
                        }
                    } else if (!plugin.isInstalled) {
                        Button(
                            onClick = onInstall,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Install")
                        }
                    } else if (plugin.isInstalled) {
                        // Show activate button for installed plugins
                        OutlinedButton(
                            onClick = onActivate,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Activate")
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
