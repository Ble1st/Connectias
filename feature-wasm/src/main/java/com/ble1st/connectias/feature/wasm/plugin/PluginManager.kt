package com.ble1st.connectias.feature.wasm.plugin

import com.ble1st.connectias.core.eventbus.EventBus
import com.ble1st.connectias.feature.wasm.events.PluginErrorEvent
import com.ble1st.connectias.feature.wasm.events.PluginExecutedEvent
import com.ble1st.connectias.feature.wasm.events.PluginLoadedEvent
import com.ble1st.connectias.feature.wasm.events.PluginUnloadedEvent
import com.ble1st.connectias.feature.wasm.plugin.models.PluginStatus
import com.ble1st.connectias.feature.wasm.plugin.models.WasmPlugin
import com.ble1st.connectias.feature.wasm.security.PluginSignatureVerifier
import com.ble1st.connectias.feature.wasm.wasm.WasmRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WASM plugin lifecycle: load, execute, unload.
 */
@Singleton
class PluginManager @Inject constructor(
    private val wasmRuntime: WasmRuntime,
    private val pluginZipParser: PluginZipParser,
    private val pluginExecutor: PluginExecutor,
    private val signatureVerifier: PluginSignatureVerifier,
    private val publicKeyManager: com.ble1st.connectias.feature.wasm.security.PluginPublicKeyManager,
    private val eventBus: EventBus
) {
    
    private val tag = "PluginManager"
    private val plugins = ConcurrentHashMap<String, WasmPlugin>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Load a plugin from a ZIP file.
     * 
     * @param zipFile Plugin ZIP file
     * @param publicKey Public key for signature verification (optional, uses default if null)
     * @return Loaded plugin
     * @throws PluginLoadException if loading fails
     */
    suspend fun loadPlugin(
        zipFile: File,
        publicKey: java.security.PublicKey? = null
    ): WasmPlugin {
        Timber.d(tag, "Loading plugin from: ${zipFile.absolutePath}")
        
        return try {
            // Parse ZIP file
            val zipData = pluginZipParser.parsePluginZip(zipFile)
            
            // Verify signature if provided
            val keyToUse = publicKey ?: publicKeyManager.getDefaultPublicKey()
            if (zipData.signature != null && keyToUse != null) {
                val isValid = signatureVerifier.verifySignature(
                    zipBytes = zipFile.readBytes(),
                    signatureBase64 = zipData.signature,
                    publicKey = keyToUse
                )
                
                if (!isValid) {
                    throw PluginLoadException("Invalid plugin signature")
                }
            } else if (zipData.signature != null && keyToUse == null) {
                Timber.w(tag, "Plugin has signature but no public key available - skipping verification")
            }
            
            // Load WASM module
            val wasmModule = wasmRuntime.loadModule(zipData.wasmBytes)
            
            // Create store
            val store = wasmRuntime.createStore(wasmModule, zipData.metadata.resourceLimits)
            
            // Create plugin instance
            val plugin = WasmPlugin(
                metadata = zipData.metadata,
                wasmModule = wasmModule,
                store = store
            )
            
            // Initialize plugin
            plugin.status.set(PluginStatus.READY)
            
            // Register plugin
            plugins[plugin.metadata.id] = plugin
            
            // Emit event
            scope.launch {
                eventBus.emit(PluginLoadedEvent(plugin.metadata.id, plugin.metadata))
            }
            
            Timber.d(tag, "Plugin loaded: ${plugin.metadata.id}")
            plugin
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin")
            throw PluginLoadException("Failed to load plugin: ${e.message}", e)
        }
    }
    
    /**
     * Execute a plugin command.
     * 
     * @param pluginId Plugin identifier
     * @param command Command to execute
     * @param args Command arguments
     * @return Execution result as JSON string
     * @throws PluginExecutionException if execution fails
     */
    suspend fun executePlugin(
        pluginId: String,
        command: String,
        args: Map<String, String> = emptyMap()
    ): String {
        Timber.d(tag, "Executing plugin $pluginId: $command")
        
        val plugin = plugins[pluginId]
            ?: throw PluginExecutionException("Plugin not found: $pluginId")
        
        if (!plugin.isReady()) {
            throw PluginExecutionException("Plugin is not ready: ${plugin.status.get()}")
        }
        
        return try {
            plugin.status.set(PluginStatus.RUNNING)
            
            val result = pluginExecutor.executeInIsolation(
                pluginId = pluginId,
                resourceLimits = plugin.metadata.resourceLimits
            ) {
                // Prepare function arguments
                val functionArgs = mapOf(
                    "command" to command,
                    "args" to args.toString() // TODO: Proper JSON serialization
                )
                
                wasmRuntime.executeFunction(
                    store = plugin.store,
                    functionName = "plugin_execute",
                    args = functionArgs
                )
            }
            
            plugin.status.set(PluginStatus.READY)
            
            // Emit event
            scope.launch {
                eventBus.emit(PluginExecutedEvent(pluginId, command, result))
            }
            
            result
            
        } catch (e: ResourceLimitExceededException) {
            plugin.status.set(PluginStatus.ERROR)
            scope.launch {
                eventBus.emit(PluginErrorEvent(pluginId, e.message ?: "Resource limit exceeded"))
            }
            throw PluginExecutionException("Resource limit exceeded: ${e.message}", e)
        } catch (e: Exception) {
            plugin.status.set(PluginStatus.ERROR)
            scope.launch {
                eventBus.emit(PluginErrorEvent(pluginId, e.message ?: "Execution failed"))
            }
            Timber.e(e, "Plugin execution failed")
            throw PluginExecutionException("Execution failed: ${e.message}", e)
        }
    }
    
    /**
     * Unload a plugin.
     * 
     * @param pluginId Plugin identifier
     */
    suspend fun unloadPlugin(pluginId: String) {
        Timber.d(tag, "Unloading plugin: $pluginId")
        
        val plugin = plugins.remove(pluginId)
            ?: throw PluginUnloadException("Plugin not found: $pluginId")
        
        try {
            // Cleanup executor resources
            pluginExecutor.cleanup(pluginId)
            
            // Mark as unloaded
            plugin.status.set(PluginStatus.UNLOADED)
            
            // Emit event
            scope.launch {
                eventBus.emit(PluginUnloadedEvent(pluginId))
            }
            
            Timber.d(tag, "Plugin unloaded: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin: $pluginId")
            throw PluginUnloadException("Failed to unload plugin: ${e.message}", e)
        }
    }
    
    /**
     * Get all loaded plugins.
     */
    fun getAllPlugins(): List<WasmPlugin> {
        return plugins.values.toList()
    }
    
    /**
     * Get a plugin by ID.
     */
    fun getPlugin(pluginId: String): WasmPlugin? {
        return plugins[pluginId]
    }
}

/**
 * Exception thrown when plugin loading fails.
 */
class PluginLoadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when plugin execution fails.
 */
class PluginExecutionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when plugin unloading fails.
 */
class PluginUnloadException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

