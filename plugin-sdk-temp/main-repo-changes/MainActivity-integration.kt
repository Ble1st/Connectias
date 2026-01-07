// Example integration of PluginService in MainActivity
// Add this to: app/src/main/kotlin/com/ble1st/connectias/MainActivity.kt

package com.ble1st.connectias

import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.core.plugin.PluginService
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.module.ModuleInfo
import kotlinx.coroutines.launch
import timber.log.Timber
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var pluginService: PluginService
    
    @Inject
    lateinit var moduleRegistry: ModuleRegistry
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ... existing security check code ...
        
        lifecycleScope.launch {
            // Initialize Plugin Service
            pluginService.initialize().onSuccess {
                Timber.i("PluginService initialized")
                
                // Register plugins with ModuleRegistry for compatibility
                val loadedPlugins = pluginService.getLoadedPlugins()
                loadedPlugins.forEach { pluginInfo ->
                    val moduleInfo = ModuleInfo(
                        id = pluginInfo.metadata.pluginId,
                        name = pluginInfo.metadata.pluginName,
                        version = pluginInfo.metadata.version,
                        isActive = pluginInfo.state == PluginManager.PluginState.ENABLED
                    )
                    moduleRegistry.registerModule(moduleInfo)
                    
                    // Enable plugin automatically
                    pluginService.loadPlugin(/* plugin file */).onSuccess {
                        pluginService.enablePlugin(pluginInfo.metadata.pluginId)
                    }
                }
            }.onFailure { error ->
                Timber.e(error, "Failed to initialize PluginService")
            }
        }
        
        // ... rest of onCreate ...
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pluginService.shutdown()
    }
}
