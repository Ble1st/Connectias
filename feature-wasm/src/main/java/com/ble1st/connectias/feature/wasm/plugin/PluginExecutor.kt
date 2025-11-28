package com.ble1st.connectias.feature.wasm.plugin

import com.ble1st.connectias.feature.wasm.plugin.models.ResourceLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes WASM plugins in isolated threads.
 * Each plugin gets its own CoroutineScope and ExecutorService for isolation.
 */
@Singleton
class PluginExecutor @Inject constructor(
    private val resourceMonitor: ResourceMonitor
) {
    
    private val tag = "PluginExecutor"
    
    // Isolated execution contexts per plugin
    private val pluginScopes = ConcurrentHashMap<String, CoroutineScope>()
    private val pluginExecutors = ConcurrentHashMap<String, java.util.concurrent.ExecutorService>()
    
    /**
     * Execute code in isolation for a specific plugin.
     * 
     * @param pluginId Plugin identifier
     * @param resourceLimits Resource limits for this execution
     * @param block Code block to execute
     * @return Execution result
     */
    suspend fun <T> executeInIsolation(
        pluginId: String,
        resourceLimits: ResourceLimits,
        block: suspend () -> T
    ): T {
        Timber.d(tag, "Executing in isolation for plugin: $pluginId")
        
        val scope = getOrCreateScope(pluginId)
        return withContext(scope.coroutineContext) {
            // Execute with resource monitoring
            resourceMonitor.enforceLimits(pluginId, resourceLimits) {
                block()
            }
        }
    }
    
    /**
     * Get or create CoroutineScope for a plugin.
     */
    private fun getOrCreateScope(pluginId: String): CoroutineScope {
        return pluginScopes.getOrPut(pluginId) {
            val executor = getOrCreateExecutor(pluginId)
            val dispatcher = executor.asCoroutineDispatcher()
            CoroutineScope(SupervisorJob() + dispatcher)
        }
    }
    
    /**
     * Get or create ExecutorService for a plugin.
     */
    private fun getOrCreateExecutor(pluginId: String): java.util.concurrent.ExecutorService {
        return pluginExecutors.getOrPut(pluginId) {
            Executors.newFixedThreadPool(
                1,
                ThreadFactory { r ->
                    Thread(r, "WasmPlugin-$pluginId").apply {
                        isDaemon = true
                    }
                }
            )
        }
    }
    
    /**
     * Cleanup resources for a plugin.
     */
    fun cleanup(pluginId: String) {
        Timber.d(tag, "Cleaning up resources for plugin: $pluginId")
        
        pluginScopes[pluginId]?.cancel()
        pluginScopes.remove(pluginId)
        
        pluginExecutors[pluginId]?.shutdown()
        pluginExecutors.remove(pluginId)
    }
    
    /**
     * Cleanup all resources.
     */
    fun cleanupAll() {
        Timber.d(tag, "Cleaning up all plugin resources")
        
        pluginScopes.values.forEach { it.cancel() }
        pluginScopes.clear()
        
        pluginExecutors.values.forEach { it.shutdown() }
        pluginExecutors.clear()
    }
}


