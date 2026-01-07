package com.ble1st.connectias.testplugin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginContext
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)

/**
 * Test Plugin - Simple counter demonstration
 */
class TestPlugin : IPlugin {
    
    private var context: PluginContext? = null
    private var counter = mutableStateOf(0)
    
    override fun getMetadata(): com.ble1st.connectias.plugin.sdk.PluginMetadata {
        return com.ble1st.connectias.plugin.sdk.PluginMetadata(
            pluginId = "com.ble1st.connectias.testplugin",
            pluginName = "Test Plugin",
            version = "1.0.0",
            author = "Connectias Team",
            description = "A simple test plugin demonstrating plugin functionality with a counter UI",
            category = com.ble1st.connectias.plugin.sdk.PluginCategory.UTILITY,
            minApiLevel = 33,
            maxApiLevel = 36,
            minAppVersion = "1.0",
            fragmentClassName = "com.ble1st.connectias.testplugin.TestPlugin",
            permissions = emptyList(),
            dependencies = emptyList(),
            nativeLibraries = emptyList()
        )
    }
    
    override fun onLoad(context: PluginContext): Boolean {
        this.context = context
        context.logDebug("Test Plugin loaded successfully")
        Timber.d("Test Plugin onLoad called")
        return true
    }
    
    override fun onEnable(): Boolean {
        context?.logDebug("Test Plugin enabled")
        Timber.d("Test Plugin onEnable called")
        return true
    }
    
    override fun onDisable(): Boolean {
        context?.logDebug("Test Plugin disabled")
        Timber.d("Test Plugin onDisable called")
        return true
    }
    
    override fun onUnload(): Boolean {
        context?.logDebug("Test Plugin unloaded")
        Timber.d("Test Plugin onUnload called")
        context = null
        return true
    }
    
    @Composable
    fun TestPluginScreen() {
        var count by remember { counter }
        var showInfo by remember { mutableStateOf(false) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Test Plugin") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Info")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Plugin Icon
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Counter Display
                Text(
                    text = "Counter",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.size(150.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Decrement Button
                    FilledTonalButton(
                        onClick = {
                            count--
                            context?.logDebug("Counter decremented to $count")
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrement",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Reset Button
                    OutlinedButton(
                        onClick = {
                            count = 0
                            context?.logDebug("Counter reset")
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Increment Button
                    FilledTonalButton(
                        onClick = {
                            count++
                            context?.logDebug("Counter incremented to $count")
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increment",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Plugin Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "✓ Plugin loaded successfully\n✓ UI rendering correctly\n✓ Counter functionality working",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Info Dialog
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                icon = { Icon(Icons.Default.Info, null) },
                title = { Text("About Test Plugin") },
                text = {
                    Column {
                        Text("Plugin ID: com.ble1st.connectias.testplugin")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Version: 1.0.0")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Author: Connectias Team")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Description: A simple test plugin demonstrating plugin functionality with a counter UI")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfo = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
