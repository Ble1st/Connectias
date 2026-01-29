package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import com.ble1st.connectias.plugin.streaming.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced plugin manager with streaming support
 * Integrates with PluginStreamingManager for chunk-based downloads
 */
@Singleton
class StreamingPluginManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginStreamingManager: PluginStreamingManager,
    private val lazyPluginLoader: LazyPluginLoader,
    private val mappedPluginLoader: MappedPluginLoader,
    private val streamCache: IStreamCache, // Use interface to avoid circular dependency
    private val nativeLibraryManager: NativeLibraryManager,
    private val pluginPermissionManager: PluginPermissionManager
) {
    
    private val loadedPlugins = ConcurrentHashMap<String, StreamingPluginInfo>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pluginDirectory = File(context.filesDir, "plugins")
    
    init {
        pluginDirectory.mkdirs()
    }
    
    data class StreamingPluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val pluginFile: File,
        val instance: IPlugin,
        val classLoader: ClassLoader,
        val context: PluginContextImpl,
        var state: PluginState,
        val loadedAt: Long,
        val stream: PluginStream? = null,
        val isFromCache: Boolean = false
    )
    
    enum class PluginState {
        DOWNLOADING,
        STREAMING,
        LOADED,
        ENABLED,
        DISABLED,
        ERROR
    }
    
    /**
     * Stream and install a plugin from URL
     */
    suspend fun streamAndInstallPlugin(
        pluginId: String,
        downloadUrl: String,
        version: String,
        onProgress: (PluginLoadingState) -> Unit = {}
    ): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Update state
            onProgress(PluginLoadingState.Downloading(
                progress = 0f,
                stage = "Starting stream",
                bytesDownloaded = 0,
                totalBytes = 0
            ))
            
            // Stream the plugin
            val streamResult = pluginStreamingManager.streamPlugin(
                pluginId = pluginId,
                downloadUrl = downloadUrl,
                version = version
            ) { progress ->
                onProgress(PluginLoadingState.Downloading(
                    progress = progress,
                    stage = "Streaming chunks",
                    bytesDownloaded = (progress * 100).toLong(), // Simplified
                    totalBytes = 100
                ))
            }
            
            val stream = streamResult.getOrThrow()
            
            // Load from stream
            onProgress(PluginLoadingState.Installing(
                currentStep = 1,
                totalSteps = 2,
                currentOperation = "Processing stream"
            ))
            
            val packageResult = pluginStreamingManager.loadPluginInChunks(stream)
            val pluginPackage = packageResult.getOrThrow()
            
            // Load the plugin
            onProgress(PluginLoadingState.Installing(
                currentStep = 2,
                totalSteps = 2,
                currentOperation = "Loading plugin"
            ))
            
            val loadResult = loadPluginFromPackage(pluginPackage)
            val metadata = loadResult.getOrThrow()
            
            onProgress(PluginLoadingState.Completed)
            
            Timber.i("Successfully streamed and installed plugin: $pluginId")
            Result.success(metadata)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to stream and install plugin: $pluginId")
            onProgress(PluginLoadingState.Failed(e, "Streaming installation"))
            Result.failure(e)
        }
    }
    
    /**
     * Load plugin from a streamed package
     */
    private suspend fun loadPluginFromPackage(
        pluginPackage: PluginPackage
    ): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            val metadata = pluginPackage.metadata
            
            // Use mapped loader for better performance
            val classLoader = if (pluginPackage.stream != null) {
                mappedPluginLoader.loadDexMapped(pluginPackage.pluginFile.absolutePath)
            } else {
                dalvik.system.DexClassLoader(
                    pluginPackage.pluginFile.absolutePath,
                    File(context.cacheDir, "plugin_dex/${metadata.pluginId}").absolutePath,
                    null,
                    context.classLoader
                )
            }
            
            // Load plugin class
            val pluginClassName = metadata.fragmentClassName
                ?: return@withContext Result.failure(Exception("No plugin class specified"))
            
            val pluginClass = classLoader.loadClass(pluginClassName)
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as IPlugin
            
            // Create plugin context
            val pluginDataDir = File(pluginDirectory, "data/${metadata.pluginId}")
            pluginDataDir.mkdirs()
            
            val pluginContext = PluginContextImpl(
                context,
                metadata.pluginId,
                pluginDataDir,
                nativeLibraryManager,
                pluginPermissionManager
            )
            
            // Call onLoad
            val loadSuccess = withTimeoutOrNull(10000L) {
                PluginExceptionHandler.safePluginBooleanCall(
                    metadata.pluginId,
                    "onLoad",
                    onError = { exception ->
                        Timber.e(exception, "Plugin onLoad failed: ${metadata.pluginId}")
                    }
                ) {
                    pluginInstance.onLoad(pluginContext)
                }
            } ?: false
            
            if (!loadSuccess) {
                return@withContext Result.failure(Exception("Plugin onLoad returned false or timed out"))
            }
            
            // Store plugin info
            val pluginInfo = StreamingPluginInfo(
                pluginId = metadata.pluginId,
                metadata = metadata,
                pluginFile = pluginPackage.pluginFile,
                instance = pluginInstance,
                classLoader = classLoader,
                context = pluginContext,
                state = PluginState.LOADED,
                loadedAt = System.currentTimeMillis(),
                stream = pluginPackage.stream,
                isFromCache = pluginPackage.stream != null
            )
            
            loadedPlugins[metadata.pluginId] = pluginInfo
            
            Result.success(metadata)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin from package")
            Result.failure(e)
        }
    }
    
    /**
     * Get loading states for all plugins
     */
    fun getLoadingStates(): Flow<Map<String, PluginLoadingState>> {
        return pluginStreamingManager.loadingStates.map { states ->
            // Add our own states for locally managed plugins
            val ourStates = loadedPlugins.mapValues { (_, pluginInfo) ->
                when (pluginInfo.state) {
                    PluginState.DOWNLOADING -> PluginLoadingState.Downloading(
                        progress = 0f,
                        stage = "Preparing",
                        bytesDownloaded = 0,
                        totalBytes = 0
                    )
                    PluginState.STREAMING -> PluginLoadingState.Downloading(
                        progress = 0.5f,
                        stage = "Streaming",
                        bytesDownloaded = 0,
                        totalBytes = 0
                    )
                    PluginState.LOADED -> PluginLoadingState.Completed
                    PluginState.ENABLED -> PluginLoadingState.Completed
                    PluginState.DISABLED -> PluginLoadingState.Paused
                    PluginState.ERROR -> PluginLoadingState.Failed(
                        RuntimeException("Plugin in error state"),
                        "Loading"
                    )
                }
            }
            
            states + ourStates
        }
    }
    
    /**
     * Enable a plugin
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            if (pluginInfo.state == PluginState.ENABLED) {
                return@withContext Result.success(Unit)
            }
            
            val enableSuccess = withTimeoutOrNull(5000L) {
                PluginExceptionHandler.safePluginBooleanCall(
                    pluginId,
                    "onEnable",
                    onError = { exception ->
                        pluginInfo.state = PluginState.ERROR
                    }
                ) {
                    pluginInfo.instance.onEnable()
                }
            } ?: false
            
            if (!enableSuccess) {
                pluginInfo.state = PluginState.ERROR
                return@withContext Result.failure(Exception("Plugin onEnable returned false or timed out"))
            }
            
            pluginInfo.state = PluginState.ENABLED
            Timber.i("Plugin enabled: $pluginId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Disable a plugin
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            if (pluginInfo.state == PluginState.DISABLED) {
                return@withContext Result.success(Unit)
            }
            
            PluginExceptionHandler.safePluginBooleanCall(
                pluginId,
                "onDisable",
                onError = { exception ->
                    // Continue even on error
                }
            ) {
                pluginInfo.instance.onDisable()
            }
            
            pluginInfo.state = PluginState.DISABLED
            Timber.i("Plugin disabled: $pluginId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Get loaded plugins
     */
    fun getLoadedPlugins(): List<StreamingPluginInfo> {
        return loadedPlugins.values.toList()
    }
    
    /**
     * Get plugin by ID
     */
    fun getPlugin(pluginId: String): StreamingPluginInfo? {
        return loadedPlugins[pluginId]
    }
    
    /**
     * Create a fragment from a plugin
     */
    fun createPluginFragment(pluginId: String): androidx.fragment.app.Fragment? {
        val pluginInfo = loadedPlugins[pluginId] ?: return null
        
        return PluginExceptionHandler.safePluginFragmentCall(
            pluginId,
            "createFragment",
            onError = { exception ->
                pluginInfo.state = PluginState.ERROR
            }
        ) {
            val fragmentClassName = pluginInfo.metadata.fragmentClassName
                ?: return null
            
            val fragmentClass = pluginInfo.classLoader.loadClass(fragmentClassName)
            fragmentClass.getDeclaredConstructor().newInstance() as? androidx.fragment.app.Fragment
                ?: throw ClassCastException("Plugin class is not a Fragment")
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats {
        return streamCache.getCacheStats()
    }
    
    /**
     * Optimize cache
     */
    suspend fun optimizeCache(): CacheOptimizationResult {
        return streamCache.optimizeCache()
    }
    
    /**
     * Clear cache
     */
    suspend fun clearCache() {
        streamCache.clearCache()
        lazyPluginLoader.clearCache()
        mappedPluginLoader.unloadAll()
    }
    
    /**
     * Update plugin to new version
     */
    suspend fun updatePlugin(
        pluginId: String,
        targetVersion: com.ble1st.connectias.plugin.version.PluginVersion
    ): Result<Unit> {
        return try {
            Timber.d("Updating plugin $pluginId to version ${targetVersion.version}")
            
            // Download new version using streamAndInstallPlugin
            val downloadResult = streamAndInstallPlugin(
                pluginId = pluginId,
                downloadUrl = targetVersion.downloadUrl,
                version = targetVersion.version
            ) { state ->
                if (state is PluginLoadingState.Downloading) {
                    Timber.d("Download progress: ${(state.progress * 100).toInt()}%")
                }
            }
            
            // Disable current version first
            disablePlugin(pluginId)
            
            val installResult = downloadResult
            
            if (installResult.isSuccess) {
                Timber.d("Successfully updated $pluginId to ${targetVersion.version}")
                Result.success(Unit)
            } else {
                Result.failure(installResult.exceptionOrNull() ?: Exception("Install failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update plugin $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Shutdown the manager
     */
    suspend fun shutdown() {
        Timber.i("Shutting down streaming plugin manager")
        
        loadedPlugins.values.forEach { pluginInfo ->
            try {
                if (pluginInfo.state == PluginState.ENABLED) {
                    PluginExceptionHandler.safePluginBooleanCall(
                        pluginInfo.pluginId,
                        "onDisable",
                        onError = { }
                    ) {
                        pluginInfo.instance.onDisable()
                    }
                }
                PluginExceptionHandler.safePluginCall<Unit>(
                    pluginInfo.pluginId,
                    "onUnload",
                    onError = { }
                ) {
                    pluginInfo.instance.onUnload()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error shutting down plugin: ${pluginInfo.pluginId}")
            }
        }
        
        loadedPlugins.clear()
        scope.cancel()
    }
}
