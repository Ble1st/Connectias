package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.plugin.PluginSandboxProxy
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * PluginManager implementation that uses a separate sandbox process for plugin execution.
 * This provides crash isolation - plugin crashes do not affect the main app process.
 * 
 * Architecture:
 * - Plugin lifecycle (onLoad, onEnable, onDisable, onUnload) runs in sandbox process via IPC
 * - Fragments are created in main process (UI must run on main thread)
 * - Plugin instances run in sandbox process, fragments are just UI wrappers
 * - Permission enforcement via PluginPermissionManager
 * 
 * This class implements the same API as PluginManager but delegates plugin lifecycle
 * operations to a separate sandbox process via IPC.
 */
class PluginManagerSandbox(
    private val context: Context,
    private val pluginDirectory: File,
    private val moduleRegistry: ModuleRegistry? = null,
    private val permissionManager: PluginPermissionManager? = null,
    private val manifestParser: PluginManifestParser? = null
) {
    
    private val loadedPlugins = ConcurrentHashMap<String, PluginInfo>()
    private val _pluginsFlow = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsFlow: StateFlow<List<PluginInfo>> = _pluginsFlow.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sandboxProxy = PluginSandboxProxy(context)
    private val dexOutputDir = File(context.cacheDir, "plugin_dex")
    private val nativeLibraryManager = NativeLibraryManager(pluginDirectory)
    
    init {
        dexOutputDir.mkdirs()
    }
    
    private fun updateFlow() {
        _pluginsFlow.value = loadedPlugins.values.toList()
    }
    
    data class PluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val pluginFile: File,
        val instance: IPlugin, // Dummy instance for compatibility - actual plugin runs in sandbox
        val classLoader: DexClassLoader,
        val context: PluginContextImpl,
        var state: PluginState,
        val loadedAt: Long,
        val permissionInfo: PluginPermissionInfo? = null
    )
    
    enum class PluginState {
        LOADED,
        ENABLED,
        DISABLED,
        ERROR
    }
    
    suspend fun initialize(): Result<List<PluginMetadata>> = withContext(Dispatchers.IO) {
        try {
            // Connect to sandbox service
            val connectResult = sandboxProxy.connect()
            if (connectResult.isFailure) {
                Timber.e(connectResult.exceptionOrNull(), "Failed to connect to sandbox service")
                return@withContext Result.failure(
                    connectResult.exceptionOrNull() ?: Exception("Sandbox connection failed")
                )
            }
            
            // Connect hardware bridge
            val hardwareConnected = sandboxProxy.connectHardwareBridge()
            if (!hardwareConnected) {
                Timber.w("Failed to connect hardware bridge - hardware access will be unavailable")
            }
            
            // Connect file system bridge
            val fileSystemConnected = sandboxProxy.connectFileSystemBridge()
            if (!fileSystemConnected) {
                Timber.w("Failed to connect file system bridge - file access will be unavailable")
            }
            
            if (!pluginDirectory.exists()) {
                pluginDirectory.mkdirs()
            }
            
            // Copy plugins from assets on first run
            copyPluginsFromAssets()
            
            val pluginFiles = pluginDirectory.listFiles { file ->
                file.extension in listOf("apk", "jar")
            } ?: emptyArray()
            
            Timber.d("Found ${pluginFiles.size} plugin files")
            
            val loadedMetadata = mutableListOf<PluginMetadata>()
            
            pluginFiles.forEach { pluginFile ->
                try {
                    val loadResult = loadPlugin(pluginFile)
                    loadResult.onSuccess { metadata ->
                        loadedMetadata.add(metadata)
                        Timber.i("Loaded plugin in sandbox: ${metadata.pluginName} v${metadata.version}")
                    }.onFailure { error ->
                        Timber.e(error, "Failed to load plugin: ${pluginFile.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading plugin: ${pluginFile.name}")
                }
            }
            
            Timber.i("Plugin initialization completed: ${loadedMetadata.size} plugins loaded")
            Result.success(loadedMetadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize plugin manager")
            Result.failure(e)
        }
    }
    
    private suspend fun loadPlugin(pluginFile: File): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Extract metadata locally first
            var metadata = extractMetadata(pluginFile)
                ?: return@withContext Result.failure(Exception("Failed to extract metadata"))
            
            // Extract and validate permissions from APK + JSON
            val permissionInfo = manifestParser?.extractPermissions(pluginFile)?.getOrNull()
            
            if (permissionInfo != null) {
                // Block plugins with critical permissions
                if (permissionInfo.hasCriticalPermissions()) {
                    Timber.e("Plugin ${metadata.pluginName} requests critical permissions: ${permissionInfo.critical}")
                    return@withContext Result.failure(
                        SecurityException(
                            "Plugin requests critical permissions: ${permissionInfo.critical.joinToString()}"
                        )
                    )
                }
                
                // Update metadata with merged permissions (APK + JSON)
                // permissionInfo.allPermissions already contains both sources merged
                metadata = metadata.copy(permissions = permissionInfo.allPermissions)
                
                Timber.i("Plugin ${metadata.pluginName} permissions merged: ${permissionInfo.allPermissions.size} total, " +
                    "${permissionInfo.dangerous.size} dangerous, ${permissionInfo.critical.size} critical")
            }
            
            // Load plugin in sandbox process via IPC using ParcelFileDescriptor
            // This works even with isolatedProcess="true"
            val sandboxResult = sandboxProxy.loadPluginFromFile(pluginFile, metadata.pluginId)
            if (sandboxResult.isFailure) {
                return@withContext Result.failure(
                    sandboxResult.exceptionOrNull() ?: Exception("Sandbox load failed")
                )
            }
            
            // Extract native libraries if any (for fragment creation in main process)
            if (metadata.nativeLibraries.isNotEmpty()) {
                nativeLibraryManager.extractNativeLibraries(pluginFile, metadata.pluginId)
            }
            
            // Create local ClassLoader for fragment creation (fragments must run in main process)
            val pluginDexDir = File(dexOutputDir, metadata.pluginId)
            pluginDexDir.mkdirs()
            
            val classLoader = DexClassLoader(
                pluginFile.absolutePath,
                pluginDexDir.absolutePath,
                null,
                context.classLoader
            )
            
            // Create plugin context with permission manager
            val pluginDataDir = File(pluginDirectory, "data/${metadata.pluginId}")
            pluginDataDir.mkdirs()
            
            val pluginContext = PluginContextImpl(
                context,
                metadata.pluginId,
                pluginDataDir,
                nativeLibraryManager,
                permissionManager ?: PluginPermissionManager(context)
            )
            
            // Create a dummy plugin instance for compatibility
            // The actual plugin instance runs in the sandbox process
            // This dummy instance is only used for type compatibility
            val dummyInstance = object : IPlugin {
                override fun getMetadata(): PluginMetadata = metadata
                override fun onLoad(context: com.ble1st.connectias.plugin.sdk.PluginContext): Boolean = true
                override fun onEnable(): Boolean = true
                override fun onDisable(): Boolean = true
                override fun onUnload(): Boolean = true
            }
            
            // Store plugin info with permission information
            val pluginInfo = PluginInfo(
                pluginId = metadata.pluginId,
                metadata = metadata,
                pluginFile = pluginFile,
                instance = dummyInstance, // Dummy for compatibility - actual plugin in sandbox
                classLoader = classLoader,
                context = pluginContext,
                state = PluginState.LOADED,
                loadedAt = System.currentTimeMillis(),
                permissionInfo = permissionInfo
            )
            
            loadedPlugins[metadata.pluginId] = pluginInfo
            updateFlow()
            
            // Register plugin in ModuleRegistry when loaded
            moduleRegistry?.let { registry ->
                val moduleMetadata = com.ble1st.connectias.core.module.ModuleCatalog.ModuleMetadata(
                    id = metadata.pluginId,
                    name = metadata.pluginName,
                    version = metadata.version,
                    fragmentClassName = metadata.fragmentClassName ?: "",
                    category = com.ble1st.connectias.core.module.ModuleCatalog.ModuleCategory.UTILITY,
                    isCore = false,
                    description = metadata.description
                )
                registry.registerFromMetadata(moduleMetadata, isActive = false)
                Timber.i("[PLUGIN MANAGER] Registered plugin in ModuleRegistry: ${metadata.pluginId} (inactive)")
            }
            
            Timber.i("Plugin loaded in sandbox: ${metadata.pluginName}")
            Result.success(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    private fun extractMetadata(pluginFile: File): PluginMetadata? {
        return try {
            java.util.zip.ZipFile(pluginFile).use { zip ->
                // Try both locations: root (APK) and assets/ (AAR)
                val manifestEntry = zip.getEntry("plugin-manifest.json")
                    ?: zip.getEntry("assets/plugin-manifest.json")
                    ?: return null
                
                val jsonString = zip.getInputStream(manifestEntry).bufferedReader().readText()
                val json = JSONObject(jsonString)
                
                val requirements = json.optJSONObject("requirements") ?: JSONObject()
                
                PluginMetadata(
                    pluginId = json.getString("pluginId"),
                    pluginName = json.getString("pluginName"),
                    version = json.getString("version"),
                    author = json.optString("author", "Unknown"),
                    minApiLevel = requirements.optInt("minApiLevel", 33),
                    maxApiLevel = requirements.optInt("maxApiLevel", 36),
                    minAppVersion = requirements.optString("minAppVersion", "1.0.0"),
                    nativeLibraries = json.optJSONArray("nativeLibraries")?.let {
                        (0 until it.length()).map { i -> it.getString(i) }
                    } ?: emptyList(),
                    fragmentClassName = json.getString("fragmentClassName"),
                    description = json.optString("description", ""),
                    permissions = json.optJSONArray("permissions")?.let {
                        (0 until it.length()).map { i -> it.getString(i) }
                    } ?: emptyList(),
                    category = com.ble1st.connectias.plugin.sdk.PluginCategory.valueOf(
                        json.optString("category", "UTILITY")
                    ),
                    dependencies = json.optJSONArray("dependencies")?.let {
                        (0 until it.length()).map { i -> it.getString(i) }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract metadata from: ${pluginFile.name}")
            null
        }
    }
    
    fun getLoadedPlugins(): List<PluginInfo> {
        return loadedPlugins.values.toList()
    }
    
    fun getEnabledPlugins(): List<PluginInfo> {
        return loadedPlugins.values.filter { it.state == PluginState.ENABLED }
    }
    
    fun getPlugin(pluginId: String): PluginInfo? {
        return loadedPlugins[pluginId]
    }
    
    /**
     * Creates a Fragment instance from a plugin.
     * Fragments are created in the main process using local ClassLoader.
     * The fragment is wrapped with PluginFragmentWrapper to catch exceptions.
     * 
     * @param pluginId The plugin ID
     * @param onCriticalError Callback invoked when plugin encounters critical error (navigate to dashboard)
     */
    fun createPluginFragment(
        pluginId: String,
        onCriticalError: (() -> Unit)? = null
    ): androidx.fragment.app.Fragment? {
        val pluginInfo = loadedPlugins[pluginId] ?: return null
        
        val wrappedFragment = PluginExceptionHandler.safePluginFragmentCall(
            pluginId,
            "createFragment",
            onError = { exception ->
                // Create new PluginInfo with ERROR state to trigger Compose recomposition
                loadedPlugins[pluginId]?.let { currentInfo ->
                    loadedPlugins[pluginId] = currentInfo.copy(state = PluginState.ERROR)
                    updateFlow()
                }
            }
        ) {
            val fragmentClassName = pluginInfo.metadata.fragmentClassName
                ?: return null
            
            val fragmentClass = pluginInfo.classLoader.loadClass(fragmentClassName)
            val fragmentInstance = fragmentClass.getDeclaredConstructor().newInstance() as? androidx.fragment.app.Fragment
                ?: throw ClassCastException("Plugin class is not a Fragment")
            
            // CRITICAL: Initialize PluginContext for Fragment instance
            // The Fragment runs in Main process and needs its own PluginContext
            if (fragmentInstance is IPlugin) {
                val pluginDir = File(context.filesDir, "plugins/${pluginInfo.metadata.pluginId}")
                pluginDir.mkdirs()
                val pluginContext = PluginContextImpl(
                    appContext = context,
                    pluginId = pluginInfo.metadata.pluginId,
                    pluginDataDir = pluginDir,
                    nativeLibraryManager = nativeLibraryManager,
                    permissionManager = permissionManager ?: throw IllegalStateException("PermissionManager required for plugin fragments")
                )
                fragmentInstance.onLoad(pluginContext)
                Timber.d("[PLUGIN MANAGER] Fragment instance initialized with PluginContext: $pluginId")
            }
            
            fragmentInstance
        } ?: return null
        
        // Wrap the fragment with PluginFragmentWrapper to catch exceptions in lifecycle methods
        // and Compose event handlers (via UncaughtExceptionHandler)
        // SECURITY: Pass required permissions and permission manager for pre-UI permission check
        return PluginFragmentWrapper(
            pluginId = pluginId,
            wrappedFragment = wrappedFragment,
            requiredPermissions = pluginInfo.metadata.permissions, // Pass required permissions
            permissionManager = permissionManager, // Pass permission manager for checking
            onError = { exception ->
                // Create new PluginInfo with ERROR state to trigger Compose recomposition
                loadedPlugins[pluginId]?.let { currentInfo ->
                    loadedPlugins[pluginId] = currentInfo.copy(state = PluginState.ERROR)
                    updateFlow()
                }
            },
            onCriticalError = onCriticalError
        )
    }
    
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            if (pluginInfo.state == PluginState.ENABLED) {
                return@withContext Result.success(Unit)
            }
            
            // Validate permissions before enabling
            if (permissionManager != null) {
                val validationResult = permissionManager.validatePermissions(pluginInfo.metadata)
                if (validationResult.isFailure) {
                    Timber.e(validationResult.exceptionOrNull(), "Permission validation failed for plugin: $pluginId")
                    return@withContext Result.failure(
                        validationResult.exceptionOrNull() ?: Exception("Permission validation failed")
                    )
                }
                
                val permValidation = validationResult.getOrNull()
                if (permValidation != null && !permValidation.isValid) {
                    if (permValidation.requiresUserConsent) {
                        // Return special error code for UI to show permission dialog
                        // Use allRequestedPermissions instead of just dangerousPermissions
                        Timber.i("Plugin $pluginId requires user consent for permissions: ${permValidation.allRequestedPermissions}")
                        return@withContext Result.failure(
                            PluginPermissionException(
                                "User consent required for: ${permValidation.allRequestedPermissions.joinToString()}",
                                permValidation.allRequestedPermissions
                            )
                        )
                    }
                    
                    // Other validation failure (e.g., critical permissions)
                    Timber.e("Plugin $pluginId permission validation failed: ${permValidation.reason}")
                    return@withContext Result.failure(
                        SecurityException(permValidation.reason)
                    )
                }
                
                Timber.d("Plugin $pluginId permission validation passed")
            }
            
            // If plugin is in ERROR state, it might have been killed in sandbox
            // We need to reload it completely in the sandbox process
            if (pluginInfo.state == PluginState.ERROR) {
                Timber.i("Plugin is in ERROR state, reloading in sandbox: $pluginId")
                
                // Try to reload plugin in sandbox (will fail if already loaded, that's ok)
                val reloadResult = sandboxProxy.loadPluginFromFile(pluginInfo.pluginFile, pluginId)
                reloadResult.onSuccess {
                    Timber.i("Plugin reloaded in sandbox after ERROR: $pluginId")
                }.onFailure { error ->
                    // If reload fails, it might be already loaded - try to disable first
                    Timber.w(error, "Plugin reload failed, trying disable+load: $pluginId")
                    sandboxProxy.disablePlugin(pluginId) // Ignore result
                    sandboxProxy.unloadPlugin(pluginId) // Ignore result
                    
                    // Try loading again
                    val secondLoadResult = sandboxProxy.loadPluginFromFile(pluginInfo.pluginFile, pluginId)
                    secondLoadResult.onFailure { secondError ->
                        Timber.e(secondError, "Failed to reload plugin after ERROR: $pluginId")
                    }
                }
                
                // Update local state to DISABLED before enabling
                loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
                updateFlow()
            }
            
            // Enable plugin in sandbox process via IPC
            val sandboxResult = sandboxProxy.enablePlugin(pluginId)
            if (sandboxResult.isFailure) {
                // Create new PluginInfo with ERROR state to trigger Compose recomposition
                loadedPlugins[pluginId] = loadedPlugins[pluginId]?.copy(state = PluginState.ERROR) 
                    ?: pluginInfo.copy(state = PluginState.ERROR)
                updateFlow()
                return@withContext Result.failure(
                    sandboxResult.exceptionOrNull() ?: Exception("Sandbox enable failed")
                )
            }
            
            // Create new PluginInfo with ENABLED state to trigger Compose recomposition
            loadedPlugins[pluginId] = loadedPlugins[pluginId]?.copy(state = PluginState.ENABLED)
                ?: pluginInfo.copy(state = PluginState.ENABLED)
            updateFlow()
            
            // Update ModuleRegistry to make plugin visible in navigation
            if (moduleRegistry != null) {
                moduleRegistry.updateModuleState(pluginId, isActive = true)
                Timber.i("[PLUGIN MANAGER] Updated ModuleRegistry for plugin: $pluginId (isActive=true)")
            } else {
                Timber.w("[PLUGIN MANAGER] ModuleRegistry is NULL - plugin will not appear in navigation: $pluginId")
            }
            
            Timber.i("Plugin enabled in sandbox: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable plugin: $pluginId")
            // Update to ERROR state on exception
            loadedPlugins[pluginId]?.let { currentInfo ->
                loadedPlugins[pluginId] = currentInfo.copy(state = PluginState.ERROR)
                updateFlow()
            }
            Result.failure(e)
        }
    }
    
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            if (pluginInfo.state == PluginState.DISABLED) {
                return@withContext Result.success(Unit)
            }
            
            // Disable plugin in sandbox process via IPC
            val sandboxResult = sandboxProxy.disablePlugin(pluginId)
            if (sandboxResult.isFailure) {
                Timber.w(sandboxResult.exceptionOrNull(), "Sandbox disable failed, but continuing")
            }
            
            // Create new PluginInfo with DISABLED state to trigger Compose recomposition
            loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
            updateFlow()
            
            // Update ModuleRegistry to hide plugin from navigation
            moduleRegistry?.updateModuleState(pluginId, isActive = false)
            
            Timber.i("Plugin disabled in sandbox: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    suspend fun unloadPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins.remove(pluginId)
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Disable first if enabled
            if (pluginInfo.state == PluginState.ENABLED) {
                sandboxProxy.disablePlugin(pluginId)
            }
            
            // Unload plugin in sandbox process via IPC
            val sandboxResult = sandboxProxy.unloadPlugin(pluginId)
            if (sandboxResult.isFailure) {
                Timber.w(sandboxResult.exceptionOrNull(), "Sandbox unload failed, but continuing")
            }
            
            // Cleanup local resources
            nativeLibraryManager.cleanupLibraries(pluginId)
            File(dexOutputDir, pluginId).deleteRecursively()
            
            // Delete the actual plugin file to prevent re-loading on app restart
            try {
                if (pluginInfo.pluginFile.exists()) {
                    val deleted = pluginInfo.pluginFile.delete()
                    if (deleted) {
                        Timber.i("Plugin file deleted: ${pluginInfo.pluginFile.absolutePath}")
                    } else {
                        Timber.w("Failed to delete plugin file: ${pluginInfo.pluginFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete plugin file: ${pluginInfo.pluginFile.absolutePath}")
            }
            
            // Delete plugin data directory
            try {
                val pluginDataDir = File(pluginDirectory, "data/${pluginId}")
                if (pluginDataDir.exists()) {
                    val deleted = pluginDataDir.deleteRecursively()
                    if (deleted) {
                        Timber.i("Plugin data directory deleted: ${pluginDataDir.absolutePath}")
                    } else {
                        Timber.w("Failed to delete plugin data directory: ${pluginDataDir.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete plugin data directory for plugin: $pluginId")
            }
            
            updateFlow()
            
            // Remove from ModuleRegistry
            moduleRegistry?.unregisterModule(pluginId)
            
            Timber.i("Plugin unloaded from sandbox: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    suspend fun loadAndEnablePlugin(pluginId: String): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Check if plugin is already loaded
            val existingPlugin = loadedPlugins[pluginId]
            if (existingPlugin != null) {
                if (existingPlugin.state != PluginState.ENABLED) {
                    enablePlugin(pluginId)
                }
                return@withContext Result.success(existingPlugin.metadata)
            }
            
            // Find plugin file
            val exactMatchFile = File(pluginDirectory, "$pluginId.apk")
            val pluginFile = if (exactMatchFile.exists() && exactMatchFile.extension in listOf("apk", "jar")) {
                exactMatchFile
            } else {
                pluginDirectory.listFiles { file ->
                    file.extension in listOf("apk", "jar")
                }?.firstOrNull { file ->
                    try {
                        val metadata = extractMetadata(file)
                        metadata?.pluginId == pluginId
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            
            if (pluginFile == null) {
                return@withContext Result.failure(Exception("Plugin file not found for ID: $pluginId"))
            }
            
            // Load the plugin only (don't enable automatically)
            val loadResult = loadPlugin(pluginFile)
            loadResult.onSuccess { metadata ->
                Timber.i("Plugin loaded in sandbox (not enabled): ${metadata.pluginName} v${metadata.version}")
                // Note: Plugin is loaded but not enabled - user must enable it manually
            }
            
            loadResult
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    suspend fun shutdown() {
        Timber.i("Shutting down plugin manager sandbox")
        
        // Unload all plugins in sandbox
        loadedPlugins.keys.toList().forEach { pluginId ->
            try {
                unloadPlugin(pluginId)
            } catch (e: Exception) {
                Timber.e(e, "Error unloading plugin during shutdown: $pluginId")
            }
        }
        
        // Disconnect from sandbox
        sandboxProxy.disconnect()
        
        loadedPlugins.clear()
        scope.cancel()
    }
    
    /**
     * Copy plugins from assets to plugin directory
     */
    private fun copyPluginsFromAssets() {
        try {
            val assetManager = context.assets
            val assetPlugins = assetManager.list("plugins") ?: emptyArray()
            
            assetPlugins.forEach { assetFile ->
                if (assetFile.endsWith(".apk") || assetFile.endsWith(".jar")) {
                    val targetFile = File(pluginDirectory, assetFile)
                    
                    if (!targetFile.exists()) {
                        assetManager.open("plugins/$assetFile").use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        targetFile.setReadOnly()
                        Timber.i("Copied plugin from assets: $assetFile")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy plugins from assets")
        }
    }
}

/**
 * Exception thrown when a plugin requires user consent for permissions
 */
class PluginPermissionException(
    message: String,
    val requiredPermissions: List<String>
) : Exception(message)
