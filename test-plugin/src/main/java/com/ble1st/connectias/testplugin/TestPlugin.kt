package com.ble1st.connectias.testplugin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginCategory
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import timber.log.Timber

/**
 * Test-Plugin für das Connectias Plugin-System
 * 
 * Implementiert sowohl IPlugin als auch Fragment
 * Das Plugin-System erwartet, dass die Klasse über fragmentClassName geladen wird
 * und IPlugin implementiert
 */
class TestPlugin : Fragment(), IPlugin {
    
    private var pluginContext: PluginContext? = null
    private var clickCount by mutableStateOf(0)
    private var isPluginEnabled by mutableStateOf(false)
    
    // IPlugin implementation
    override fun onLoad(context: PluginContext): Boolean {
        Timber.i("TestPlugin: onLoad called")
        this.pluginContext = context
        context.logVerbose("TestPlugin: Verbose log - detailed initialization info")
        context.logInfo("TestPlugin loaded successfully")
        context.logDebug("TestPlugin: Debug info - context initialized")
        return true
    }
    
    override fun onEnable(): Boolean {
        Timber.i("TestPlugin: onEnable called")
        isPluginEnabled = true
        pluginContext?.logInfo("TestPlugin enabled")
        pluginContext?.logDebug("TestPlugin: State changed to enabled")
        return true
    }
    
    override fun onDisable(): Boolean {
        Timber.i("TestPlugin: onDisable called")
        isPluginEnabled = false
        pluginContext?.logWarning("TestPlugin disabled - features may not be available")
        pluginContext?.logDebug("TestPlugin: State changed to disabled")
        return true
    }
    
    override fun onUnload(): Boolean {
        Timber.i("TestPlugin: onUnload called")
        pluginContext?.logInfo("TestPlugin unloaded")
        pluginContext?.logDebug("TestPlugin: Cleanup completed")
        return true
    }
    
    override fun getMetadata(): PluginMetadata {
        return PluginMetadata(
            pluginId = "com.ble1st.connectias.testplugin",
            pluginName = "Test Plugin",
            version = "1.0.0",
            author = "Connectias Team",
            minApiLevel = 33,
            maxApiLevel = 36,
            minAppVersion = "1.0.0",
            nativeLibraries = emptyList(),
            fragmentClassName = "com.ble1st.connectias.testplugin.TestPlugin",
            description = "Ein Test-Plugin für das Connectias Plugin-System mit Compose UI",
            permissions = emptyList(),
            category = PluginCategory.UTILITY,
            dependencies = emptyList()
        )
    }
    
    // Fragment implementation
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("TestPlugin: onCreateView called")
        pluginContext?.logDebug("TestPlugin: Creating Compose view")
        
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    TestPluginScreen(
                        clickCount = clickCount,
                        isPluginEnabled = isPluginEnabled,
                        onIncrementClick = { 
                            clickCount++
                            pluginContext?.logDebug("Counter incremented to $clickCount")
                        },
                        onResetClick = { 
                            pluginContext?.logInfo("Counter reset from $clickCount to 0")
                            clickCount = 0 
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("TestPlugin: View created successfully")
        pluginContext?.logInfo("TestPlugin UI view created and ready")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("TestPlugin: onDestroy called")
        pluginContext?.logDebug("TestPlugin: Fragment destroyed")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPluginScreen(
    clickCount: Int,
    isPluginEnabled: Boolean,
    onIncrementClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test Plugin") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPluginEnabled) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isPluginEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (isPluginEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isPluginEnabled) "Plugin Aktiv" else "Plugin Geladen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Plugin-Informationen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    InfoRow(
                        icon = Icons.Default.Build,
                        label = "Plugin ID",
                        value = "com.ble1st.connectias.testplugin"
                    )
                    
                    InfoRow(
                        icon = Icons.Default.Person,
                        label = "Autor",
                        value = "Connectias Team"
                    )
                    
                    InfoRow(
                        icon = Icons.Default.Settings,
                        label = "Kategorie",
                        value = "UTILITY"
                    )
                }
            }

            // Counter Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Interaktiver Counter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "$clickCount",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onIncrementClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Erhöhen")
                        }
                        
                        OutlinedButton(
                            onClick = onResetClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset")
                        }
                    }
                }
            }

            // Features Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Plugin-Features",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    FeatureItem("✅ Jetpack Compose UI")
                    FeatureItem("✅ Material 3 Design")
                    FeatureItem("✅ Fragment-basiert")
                    FeatureItem("✅ Lifecycle-Management")
                    FeatureItem("✅ Timber Logging")
                    FeatureItem("✅ Database Logging")
                    FeatureItem("✅ Standalone APK")
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun FeatureItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
