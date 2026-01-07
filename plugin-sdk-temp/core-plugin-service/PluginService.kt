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
    private val pluginValidator: PluginValidator,
    private val pluginImportService: PluginImportService,
    private val pluginDependencyResolver: PluginDependencyResolver,
    private val pluginPermissionManager: PluginPermissionManager,
    private val notificationManager: com.ble1st.connectias.ui.PluginNotificationManager? = null
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
                notificationManager?.notifyPluginLoaded(pluginInfo.metadata.pluginName)
            }.onFailure { error ->
                notificationManager?.notifyPluginError(
                    pluginFile.name,
                    error.message ?: "Unbekannter Fehler"
                )
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
     * Enables a plugin.
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val plugin = pluginManager.getPlugin(pluginId)
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Check dependencies are enabled
            val depsEnabled = pluginDependencyResolver.checkDependenciesEnabled(pluginId)
            if (depsEnabled.isFailure || depsEnabled.getOrNull() == false) {
                val disabledDeps = pluginDependencyResolver.getDisabledDependencies(pluginId)
                return@withContext Result.failure(
                    Exception("Plugin has disabled dependencies: $disabledDeps")
                )
            }
            
            // Check permissions
            val permResult = pluginPermissionManager.validatePermissions(plugin.metadata)
            if (permResult.isFailure) {
                return@withContext Result.failure(
                    permResult.exceptionOrNull() ?: Exception("Permission validation failed")
                )
            }
            
            val permValidation = permResult.getOrNull()
            if (permValidation != null && !permValidation.isValid) {
                if (permValidation.requiresUserConsent) {
                    notificationManager?.notifyPluginPermissionRequired(
                        plugin.metadata.pluginName,
                        permValidation.dangerousPermissions
                    )
                }
                return@withContext Result.failure(
                    SecurityException("Plugin permissions not granted: ${permValidation.reason}")
                )
            }
            
            // Enable via manager
            val result = pluginManager.enablePlugin(pluginId)
            result.onSuccess {
                notificationManager?.notifyPluginEnabled(plugin.metadata.pluginName)
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Disables a plugin.
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        pluginManager.disablePlugin(pluginId)
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
     * Gets all enabled plugins.
     */
    fun getEnabledPlugins(): List<PluginInfo> {
        return pluginManager.getEnabledPlugins()
    }
    
    /**
     * Imports a plugin from an external path.
     */
    suspend fun importPlugin(sourcePath: String): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            // Import the file
            val pluginFile = pluginImportService.importFromExternalPath(sourcePath).getOrElse {
                return@withContext Result.failure(it)
            }
            
            // Load the plugin
            loadPlugin(pluginFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import plugin from: $sourcePath")
            Result.failure(e)
        }
    }
    
    /**
     * Imports a plugin from a URI (e.g., from SAF).
     */
    suspend fun importPluginFromUri(uri: android.net.Uri): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            // Import the file
            val pluginFile = pluginImportService.importFromUri(uri).getOrElse {
                return@withContext Result.failure(it)
            }
            
            // Load the plugin
            loadPlugin(pluginFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import plugin from URI: $uri")
            Result.failure(e)
        }
    }
    
    /**
     * Grants user consent for plugin permissions.
     */
    fun grantPermissionConsent(pluginId: String, permissions: List<String>) {
        pluginPermissionManager.grantUserConsent(pluginId, permissions)
    }
    
    /**
     * Revokes user consent for plugin permissions.
     */
    fun revokePermissionConsent(pluginId: String, permissions: List<String>? = null) {
        pluginPermissionManager.revokeUserConsent(pluginId, permissions)
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
                val update = pluginDownloadManager.getLatestRelease(pluginInfo.metadata.pluginId).getOrNull()
                if (update != null && update.version != pluginInfo.metadata.version) {
                    Timber.d("Update available for ${pluginInfo.metadata.pluginName}: ${update.version}")
                    notificationManager?.notifyPluginUpdateAvailable(
                        pluginInfo.metadata.pluginName,
                        pluginInfo.metadata.version,
                        update.version
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for updates")
        }
    }
    
    /**
     * Gets missing dependencies for a plugin.
     */
    fun getMissingDependencies(pluginId: String): List<String> {
        val plugin = pluginManager.getPlugin(pluginId) ?: return emptyList()
        return pluginDependencyResolver.getMissingDependencies(plugin.metadata)
    }
    
    fun shutdown() {
        scope.cancel()
        pluginManager.shutdown()
    }
}
