package com.ble1st.connectias.core.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main service for plugin management.
 * Handles plugin discovery, loading, and lifecycle management.
 */
@Singleton
class PluginService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginManager: PluginManager,
    private val pluginDownloadManager: GitHubPluginDownloadManager,
    private val pluginValidator: PluginValidator
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Initializes the plugin system.
     * Scans for installed plugins and loads them.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing PluginService")
            
            // 1. Scan for installed plugins
            val installedPlugins = scanInstalledPlugins()
            Timber.d("Found ${installedPlugins.size} installed plugins")
            
            // 2. Load each plugin
            installedPlugins.forEach { pluginFile ->
                loadPlugin(pluginFile).onFailure { error ->
                    Timber.e(error, "Failed to load plugin: ${pluginFile.name}")
                }
            }
            
            // 3. Check for updates (async, non-blocking)
            scope.launch {
                checkForUpdates()
            }
            
            Timber.i("PluginService initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize PluginService")
            Result.failure(e)
        }
    }
    
    /**
     * Loads a plugin from a file (APK or AAB).
     */
    suspend fun loadPlugin(pluginFile: File): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            // 1. Validate plugin
            val validationResult = pluginValidator.validate(pluginFile)
            if (validationResult.isFailure) {
                return@withContext Result.failure(
                    validationResult.exceptionOrNull() ?: Exception("Plugin validation failed")
                )
            }
            
            // 2. Load via PluginManager
            val result = pluginManager.loadPlugin(pluginFile)
            
            result.onSuccess { pluginInfo ->
                Timber.i("Plugin loaded: ${pluginInfo.metadata.pluginName} v${pluginInfo.metadata.version}")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    /**
     * Downloads and installs a plugin from GitHub Releases.
     */
    suspend fun installPluginFromGitHub(
        pluginId: String,
        version: String? = null,
        onProgress: (current: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            // 1. Find plugin release
            val release = if (version != null) {
                pluginDownloadManager.findRelease(pluginId, version).getOrNull()
            } else {
                pluginDownloadManager.getLatestRelease(pluginId).getOrNull()
            } ?: return@withContext Result.failure(Exception("Plugin release not found"))
            
            // 2. Download plugin
            val pluginFile = pluginDownloadManager.downloadPlugin(
                release = release,
                onProgress = onProgress
            ).getOrNull() ?: return@withContext Result.failure(Exception("Download failed"))
            
            // 3. Load plugin
            loadPlugin(pluginFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to install plugin from GitHub: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Unloads a plugin.
     */
    suspend fun unloadPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        pluginManager.unloadPlugin(pluginId)
    }
    
    /**
     * Gets all loaded plugins.
     */
    fun getLoadedPlugins(): List<PluginInfo> {
        return pluginManager.getLoadedPlugins()
    }
    
    /**
     * Gets a specific plugin by ID.
     */
    fun getPlugin(pluginId: String): PluginInfo? {
        return pluginManager.getPlugin(pluginId)
    }
    
    /**
     * Scans the plugin directory for installed plugins.
     */
    private suspend fun scanInstalledPlugins(): List<File> = withContext(Dispatchers.IO) {
        val pluginDir = File(context.filesDir, "plugins")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
            return@withContext emptyList()
        }
        
        pluginDir.listFiles { file ->
            file.extension == "apk" || file.extension == "aab"
        }?.toList() ?: emptyList()
    }
    
    /**
     * Checks for plugin updates asynchronously.
     */
    private suspend fun checkForUpdates() {
        try {
            val loadedPlugins = getLoadedPlugins()
            loadedPlugins.forEach { pluginInfo ->
                val updateResult = pluginDownloadManager.checkForUpdate(
                    pluginId = pluginInfo.metadata.pluginId,
                    currentVersion = pluginInfo.metadata.version
                )
                
                updateResult.onSuccess { update ->
                    if (update != null) {
                        Timber.d("Update available for ${pluginInfo.metadata.pluginName}: ${update.version}")
                        // TODO: Notify user or auto-update
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for updates")
        }
    }
    
    fun shutdown() {
        scope.cancel()
        pluginManager.shutdown()
    }
}
