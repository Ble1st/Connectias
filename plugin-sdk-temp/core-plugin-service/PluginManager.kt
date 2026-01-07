package com.ble1st.connectias.core.plugin

import android.content.Context
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.ble1st.connectias.plugin.PluginMetadata

/**
 * Plugin manager that uses isolated sandbox process for plugin execution
 * Provides crash isolation and memory isolation
 */
class PluginManager(
    private val context: Context,
    private val pluginDirectory: File
) {
    
    private val sandboxProxy = PluginSandboxProxy(context)
    private val loadedPlugins = ConcurrentHashMap<String, PluginInfo>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val INIT_TIMEOUT_MS = 10000L
    }
    
    /**
     * Plugin information in main process
     */
    data class PluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val pluginFile: File,
        val state: PluginState,
        val loadedAt: Long
    )
    
    enum class PluginState {
        LOADED,
        ENABLED,
        DISABLED,
        ERROR
    }
    
    /**
     * Initializes the sandbox and loads available plugins
     */
    suspend fun initialize(): Result<List<PluginMetadata>> = withContext(Dispatchers.IO) {
        try {
            Timber.i("Initializing plugin sandbox...")
            
            // Connect to sandbox service
            val connectResult = withTimeoutOrNull(INIT_TIMEOUT_MS) {
                sandboxProxy.connect()
            }
            
            if (connectResult == null || connectResult.isFailure) {
                return@withContext Result.failure(
                    Exception("Failed to connect to sandbox: ${connectResult?.exceptionOrNull()?.message}")
                )
            }
            
            // Create plugin directory if needed
            if (!pluginDirectory.exists()) {
                pluginDirectory.mkdirs()
            }
            
            // Scan for plugin files
            val pluginFiles = pluginDirectory.listFiles { file ->
                file.extension in listOf("apk", "jar")
            } ?: emptyArray()
            
            Timber.d("Found ${pluginFiles.size} plugin files")
            
            // Load all plugins in sandbox
            val loadedMetadata = mutableListOf<PluginMetadata>()
            for (pluginFile in pluginFiles) {
                val result = loadPlugin(pluginFile)
                if (result.isSuccess) {
                    val pluginInfo = result.getOrNull()
                    if (pluginInfo != null) {
                        loadedMetadata.add(pluginInfo.metadata)
                    }
                }
            }
            
            Timber.i("Plugin sandbox initialized with ${loadedMetadata.size} plugins")
            Result.success(loadedMetadata)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize plugin sandbox")
            Result.failure(e)
        }
    }
    
    /**
     * Loads a plugin in the sandbox process
     */
    suspend fun loadPlugin(pluginFile: File): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Loading plugin in sandbox: ${pluginFile.name}")
            
            // Load via sandbox proxy (IPC call)
            val metadataResult = sandboxProxy.loadPlugin(pluginFile.absolutePath)
            
            if (metadataResult.isFailure) {
                return@withContext Result.failure(
                    metadataResult.exceptionOrNull() ?: Exception("Unknown error")
                )
            }
            
            val metadata = metadataResult.getOrNull()
                ?: return@withContext Result.failure(Exception("No metadata returned"))
            
            // Store plugin info in main process
            val pluginInfo = PluginInfo(
                pluginId = metadata.pluginId,
                metadata = metadata,
                pluginFile = pluginFile,
                state = PluginState.LOADED,
                loadedAt = System.currentTimeMillis()
            )
            
            loadedPlugins[metadata.pluginId] = pluginInfo
            
            Timber.i("Plugin loaded in sandbox: ${metadata.pluginName} v${metadata.version}")
            Result.success(pluginInfo)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin in sandbox: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    /**
     * Enables a plugin in the sandbox
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Enable via sandbox proxy (IPC call)
            val result = sandboxProxy.enablePlugin(pluginId)
            
            if (result.isFailure) {
                loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.ERROR)
                return@withContext result
            }
            
            // Update state in main process
            loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.ENABLED)
            
            Timber.i("Plugin enabled in sandbox: $pluginId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable plugin in sandbox: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Disables a plugin in the sandbox
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Disable via sandbox proxy (IPC call)
            val result = sandboxProxy.disablePlugin(pluginId)
            
            if (result.isFailure) {
                return@withContext result
            }
            
            // Update state in main process
            loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
            
            Timber.i("Plugin disabled in sandbox: $pluginId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable plugin in sandbox: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Unloads a plugin from the sandbox
     */
    suspend fun unloadPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Disable first if enabled
            if (pluginInfo.state == PluginState.ENABLED) {
                disablePlugin(pluginId)
            }
            
            // Unload via sandbox proxy (IPC call)
            val result = sandboxProxy.unloadPlugin(pluginId)
            
            if (result.isFailure) {
                return@withContext result
            }
            
            // Remove from main process
            loadedPlugins.remove(pluginId)
            
            Timber.i("Plugin unloaded from sandbox: $pluginId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin from sandbox: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Gets all loaded plugins
     */
    fun getLoadedPlugins(): List<PluginInfo> {
        return loadedPlugins.values.toList()
    }
    
    /**
     * Gets all enabled plugins
     */
    fun getEnabledPlugins(): List<PluginInfo> {
        return loadedPlugins.values.filter { it.state == PluginState.ENABLED }
    }
    
    /**
     * Gets a specific plugin
     */
    fun getPlugin(pluginId: String): PluginInfo? {
        return loadedPlugins[pluginId]
    }
    
    /**
     * Shuts down the plugin system and sandbox
     */
    fun shutdown() {
        Timber.i("Shutting down plugin sandbox")
        scope.cancel()
        sandboxProxy.disconnect()
        loadedPlugins.clear()
    }
}
