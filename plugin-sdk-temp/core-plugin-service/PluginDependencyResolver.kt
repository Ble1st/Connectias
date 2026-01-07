package com.ble1st.connectias.core.plugin

import com.ble1st.connectias.plugin.PluginMetadata
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PluginDependencyResolver(
    private val pluginManager: PluginManager
) {
    
    suspend fun resolveDependencies(metadata: PluginMetadata): Result<List<String>> = 
        withContext(Dispatchers.Default) {
            try {
                if (metadata.dependencies.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                
                val loadOrder = mutableListOf<String>()
                val visited = mutableSetOf<String>()
                val visiting = mutableSetOf<String>()
                
                fun visit(pluginId: String, pluginMeta: PluginMetadata): Boolean {
                    if (visited.contains(pluginId)) {
                        return true
                    }
                    
                    if (visiting.contains(pluginId)) {
                        Timber.e("Circular dependency detected: $pluginId")
                        return false
                    }
                    
                    visiting.add(pluginId)
                    
                    for (depId in pluginMeta.dependencies) {
                        val depPlugin = pluginManager.getPlugin(depId)
                        if (depPlugin == null) {
                            Timber.e("Dependency not found: $depId (required by $pluginId)")
                            return false
                        }
                        
                        if (!visit(depId, depPlugin.metadata)) {
                            return false
                        }
                    }
                    
                    visiting.remove(pluginId)
                    visited.add(pluginId)
                    loadOrder.add(pluginId)
                    
                    return true
                }
                
                if (!visit(metadata.pluginId, metadata)) {
                    return@withContext Result.failure(
                        IllegalStateException("Failed to resolve dependencies for ${metadata.pluginId}")
                    )
                }
                
                Timber.d("Dependency resolution for ${metadata.pluginId}: $loadOrder")
                Result.success(loadOrder)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve dependencies for: ${metadata.pluginId}")
                Result.failure(e)
            }
        }
    
    fun checkDependenciesLoaded(pluginId: String): Result<Boolean> {
        return try {
            val plugin = pluginManager.getPlugin(pluginId)
                ?: return Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
            
            val allLoaded = plugin.metadata.dependencies.all { depId ->
                val depPlugin = pluginManager.getPlugin(depId)
                depPlugin != null && depPlugin.state == PluginManager.PluginState.LOADED
            }
            
            Result.success(allLoaded)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check dependencies for: $pluginId")
            Result.failure(e)
        }
    }
    
    fun checkDependenciesEnabled(pluginId: String): Result<Boolean> {
        return try {
            val plugin = pluginManager.getPlugin(pluginId)
                ?: return Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
            
            val allEnabled = plugin.metadata.dependencies.all { depId ->
                val depPlugin = pluginManager.getPlugin(depId)
                depPlugin != null && depPlugin.state == PluginManager.PluginState.ENABLED
            }
            
            Result.success(allEnabled)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check enabled dependencies for: $pluginId")
            Result.failure(e)
        }
    }
    
    fun getMissingDependencies(metadata: PluginMetadata): List<String> {
        return metadata.dependencies.filter { depId ->
            pluginManager.getPlugin(depId) == null
        }
    }
    
    fun getDisabledDependencies(pluginId: String): List<String> {
        val plugin = pluginManager.getPlugin(pluginId) ?: return emptyList()
        
        return plugin.metadata.dependencies.filter { depId ->
            val depPlugin = pluginManager.getPlugin(depId)
            depPlugin == null || depPlugin.state != PluginManager.PluginState.ENABLED
        }
    }
}
