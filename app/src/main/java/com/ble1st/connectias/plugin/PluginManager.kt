package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Complete plugin manager with DexClassLoader-based loading
 * Supports dynamic plugin loading, native libraries, and full lifecycle management
 */
class PluginManager(
    private val context: Context,
    private val pluginDirectory: File
) {
    
    private val loadedPlugins = ConcurrentHashMap<String, PluginInfo>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nativeLibraryManager = NativeLibraryManager(pluginDirectory)
    private val dexOutputDir = File(context.cacheDir, "plugin_dex")
    
    init {
        dexOutputDir.mkdirs()
    }
    
    data class PluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val pluginFile: File,
        val instance: IPlugin,
        val classLoader: DexClassLoader,
        val context: PluginContextImpl,
        var state: PluginState,
        val loadedAt: Long
    )
    
    enum class PluginState {
        LOADED,
        ENABLED,
        DISABLED,
        ERROR
    }
    
    suspend fun initialize(): Result<List<PluginMetadata>> = withContext(Dispatchers.IO) {
        try {
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
                        Timber.i("Loaded plugin: ${metadata.pluginName} v${metadata.version}")
                    }.onFailure { error ->
                        Timber.e(error, "Failed to load plugin: ${pluginFile.name}")
                        // Check if file is invalid (missing classes.dex) and delete it
                        if (error.message?.contains("classes.dex") == true || 
                            error.message?.contains("ClassNotFoundException") == true ||
                            error.cause?.message?.contains("classes.dex") == true) {
                            try {
                                // Make file writable and readable before deleting (in case it's read-only)
                                // Set permissions for owner only (ownerOnly=true) to avoid security risks
                                pluginFile.setWritable(true, true) // Make writable for owner only
                                pluginFile.setReadable(true, true) // Make readable for owner only
                                pluginFile.setExecutable(true, true) // Make executable for owner only
                                
                                if (pluginFile.delete()) {
                                    Timber.w("Deleted invalid plugin file: ${pluginFile.name}")
                                } else {
                                    // Try to delete parent directory's file if direct delete fails
                                    val parentDir = pluginFile.parentFile
                                    if (parentDir != null && parentDir.canWrite()) {
                                        // Force delete using FileChannel or try renaming first
                                        val tempName = "${pluginFile.name}.deleted.${System.currentTimeMillis()}"
                                        val renamed = pluginFile.renameTo(File(parentDir, tempName))
                                        if (renamed) {
                                            File(parentDir, tempName).delete()
                                            Timber.w("Deleted invalid plugin file via rename: ${pluginFile.name}")
                                        } else {
                                            Timber.w("Failed to delete invalid plugin file (may be in use): ${pluginFile.name}")
                                        }
                                    } else {
                                        Timber.w("Failed to delete invalid plugin file (no write permission): ${pluginFile.name}")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to delete invalid plugin file: ${pluginFile.name}")
                            }
                        }
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
    
    /**
     * Public method to load a plugin from file
     * Used by VersionedPluginManager for version tracking
     */
    suspend fun loadPluginFromFile(pluginFile: File): Result<PluginMetadata> {
        return loadPlugin(pluginFile)
    }
    
    /**
     * Export a plugin to a file
     * Returns the original plugin file
     */
    suspend fun exportPlugin(pluginId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
            
            Result.success(pluginInfo.pluginFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    private suspend fun loadPlugin(pluginFile: File): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Extract metadata
            val metadata = extractMetadata(pluginFile)
                ?: return@withContext Result.failure(Exception("Failed to extract metadata"))
            
            // Extract native libraries if any
            if (metadata.nativeLibraries.isNotEmpty()) {
                nativeLibraryManager.extractNativeLibraries(pluginFile, metadata.pluginId)
            }
            
            // Create DexClassLoader
            val pluginDexDir = File(dexOutputDir, metadata.pluginId)
            pluginDexDir.mkdirs()
            
            val classLoader = DexClassLoader(
                pluginFile.absolutePath,
                pluginDexDir.absolutePath,
                null,
                context.classLoader
            )
            
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
                PluginPermissionManager(context) // Fallback permission manager for non-sandbox usage
            )
            
            // Call onLoad
            val loadSuccess = withTimeoutOrNull(10000L) {
                PluginExceptionHandler.safePluginBooleanCall(
                    metadata.pluginId,
                    "onLoad",
                    onError = { exception ->
                        // Plugin will be set to ERROR state if loadSuccess is false
                    }
                ) {
                    pluginInstance.onLoad(pluginContext)
                }
            } ?: false
            
            if (!loadSuccess) {
                // Set state to ERROR if load fails
                val pluginInfo = PluginInfo(
                    pluginId = metadata.pluginId,
                    metadata = metadata,
                    pluginFile = pluginFile,
                    instance = pluginInstance,
                    classLoader = classLoader,
                    context = pluginContext,
                    state = PluginState.ERROR,
                    loadedAt = System.currentTimeMillis()
                )
                loadedPlugins[metadata.pluginId] = pluginInfo
                return@withContext Result.failure(Exception("Plugin onLoad returned false or timed out"))
            }
            
            // Store plugin info
            val pluginInfo = PluginInfo(
                pluginId = metadata.pluginId,
                metadata = metadata,
                pluginFile = pluginFile,
                instance = pluginInstance,
                classLoader = classLoader,
                context = pluginContext,
                state = PluginState.LOADED,
                loadedAt = System.currentTimeMillis()
            )
            loadedPlugins[metadata.pluginId] = pluginInfo
            
            Result.success(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    private fun extractMetadata(pluginFile: File): PluginMetadata? {
        return try {
            ZipFile(pluginFile).use { zip ->
                // Try both locations: root (APK) and assets/ (AAR)
                val manifestEntry = zip.getEntry("plugin-manifest.json") 
                    ?: zip.getEntry("assets/plugin-manifest.json")
                    ?: return null
                    
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val json = JSONObject(manifestJson)
                
                PluginMetadata(
                    pluginId = json.getString("pluginId"),
                    pluginName = json.getString("pluginName"),
                    version = json.getString("version"),
                    author = json.getString("author"),
                    minApiLevel = json.getInt("minApiLevel"),
                    maxApiLevel = json.getInt("maxApiLevel"),
                    minAppVersion = json.getString("minAppVersion"),
                    nativeLibraries = json.optJSONArray("nativeLibraries")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    fragmentClassName = json.optString("fragmentClassName"),
                    description = json.getString("description"),
                    permissions = json.optJSONArray("permissions")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    category = com.ble1st.connectias.plugin.sdk.PluginCategory.valueOf(
                        json.getString("category")
                    ),
                    dependencies = json.optJSONArray("dependencies")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract metadata")
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
     * The fragment class is loaded from the plugin's ClassLoader.
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
     * Loads and enables a single plugin by its ID.
     * This is useful after importing a new plugin without restarting the app.
     */
    suspend fun loadAndEnablePlugin(pluginId: String): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Check if plugin is already loaded
            val existingPlugin = loadedPlugins[pluginId]
            if (existingPlugin != null) {
                // Plugin already loaded, just enable it if not already enabled
                if (existingPlugin.state != PluginState.ENABLED) {
                    enablePlugin(pluginId)
                }
                return@withContext Result.success(existingPlugin.metadata)
            }
            
            // Find plugin file - first try exact match (as saved by PluginImportHandler)
            val exactMatchFile = File(pluginDirectory, "$pluginId.apk")
            val pluginFile = if (exactMatchFile.exists() && exactMatchFile.extension in listOf("apk", "jar")) {
                exactMatchFile
            } else {
                // Fallback: search all plugin files and match by metadata
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
            
            // Load the plugin
            val loadResult = loadPlugin(pluginFile)
            loadResult.onSuccess { metadata ->
                // Automatically enable the plugin after loading
                enablePlugin(pluginId)
                Timber.i("Plugin loaded and enabled: ${metadata.pluginName} v${metadata.version}")
            }
            
            loadResult
        } catch (e: Exception) {
            Timber.e(e, "Failed to load and enable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
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
    
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            if (pluginInfo.state == PluginState.DISABLED) {
                return@withContext Result.success(Unit)
            }
            
            val disableSuccess = withTimeoutOrNull(5000L) {
                PluginExceptionHandler.safePluginBooleanCall(
                    pluginId,
                    "onDisable",
                    onError = { exception ->
                        // Disable should continue even on errors (no ERROR state needed)
                    }
                ) {
                    pluginInfo.instance.onDisable()
                }
            } ?: false
            
            if (!disableSuccess) {
                Timber.w("Plugin onDisable returned false or timed out: $pluginId")
            }
            
            pluginInfo.state = PluginState.DISABLED
            Timber.i("Plugin disabled: $pluginId")
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
            
            if (pluginInfo.state == PluginState.ENABLED) {
                PluginExceptionHandler.safePluginBooleanCall(
                    pluginId,
                    "onDisable",
                    onError = { exception ->
                        // Unload should continue even on errors
                    }
                ) {
                    pluginInfo.instance.onDisable()
                }
            }
            
            withTimeoutOrNull(5000L) {
                PluginExceptionHandler.safePluginCall<Unit>(
                    pluginId,
                    "onUnload",
                    onError = { exception ->
                        // Unload should continue even on errors
                    }
                ) {
                    pluginInfo.instance.onUnload()
                }
            }
            
            nativeLibraryManager.cleanupLibraries(pluginId)
            
            Timber.i("Plugin unloaded: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    fun shutdown() {
        Timber.i("Shutting down plugin manager")
        
        loadedPlugins.values.forEach { pluginInfo ->
            try {
                if (pluginInfo.state == PluginState.ENABLED) {
                    PluginExceptionHandler.safePluginBooleanCall(
                        pluginInfo.pluginId,
                        "onDisable",
                        onError = { exception ->
                            // Shutdown should continue even on errors
                        }
                    ) {
                        pluginInfo.instance.onDisable()
                    }
                }
                PluginExceptionHandler.safePluginCall<Unit>(
                    pluginInfo.pluginId,
                    "onUnload",
                    onError = { exception ->
                        // Shutdown should continue even on errors
                    }
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
                    
                    // Only copy if file doesn't exist or is older
                    if (!targetFile.exists()) {
                        assetManager.open("plugins/$assetFile").use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Set file to read-only to comply with Android security requirements
                        // Android 10+ requires DEX files to be non-writable
                        targetFile.setReadOnly()
                        
                        Timber.i("Copied plugin from assets: $assetFile (read-only)")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy plugins from assets")
        }
    }
}
