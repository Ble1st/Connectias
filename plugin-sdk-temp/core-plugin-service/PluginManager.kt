package com.ble1st.connectias.core.plugin

import android.content.Context
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import org.json.JSONObject
import org.json.JSONArray

/**
 * Manages plugin lifecycle and discovery
 */
class PluginManager(
    private val context: Context,
    private val pluginDirectory: File
) {
    
    private val loadedPlugins = ConcurrentHashMap<String, PluginInfo>()
    private val classLoaders = ConcurrentHashMap<String, URLClassLoader>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Plugin information during runtime
     */
    data class PluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val instance: IPlugin,
        val classLoader: URLClassLoader,
        val state: PluginState,
        val loadedAt: Long
    )
    
    enum class PluginState {
        LOADED,      // Loaded but not activated
        ENABLED,     // Activated and working
        DISABLED,    // Disabled but still in memory
        ERROR        // Error during loading
    }
    
    /**
     * Initializes plugin directory and loads available plugins
     */
    suspend fun initialize(): Result<List<PluginMetadata>> = withContext(Dispatchers.IO) {
        try {
            // Create plugin directory if it doesn't exist
            if (!pluginDirectory.exists()) {
                pluginDirectory.mkdirs()
            }
            
            // Scan for plugin AABs
            val pluginFiles = pluginDirectory.listFiles { file ->
                file.extension == "aab" || file.extension == "jar" || file.extension == "apk"
            } ?: emptyArray()
            
            Timber.d("Found ${pluginFiles.size} plugin files")
            
            // Load all plugins
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
            
            Result.success(loadedMetadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize plugin manager")
            Result.failure(e)
        }
    }
    
    /**
     * Loads a plugin from an AAB/JAR file
     */
    suspend fun loadPlugin(pluginFile: File): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            // 1. Extract plugin manifest
            val metadata = extractPluginMetadata(pluginFile)
            
            // 2. Validate plugin
            validatePlugin(metadata)
            
            // 3. Create custom ClassLoader
            val classLoader = URLClassLoader(
                arrayOf(pluginFile.toURI().toURL()),
                context.classLoader  // Parent ClassLoader is App ClassLoader
            )
            
            // 4. Instantiate plugin class
            // Note: For AAB files, we need to extract DEX files first
            // This is a simplified version - in production, use bundletool to extract AAB
            val pluginClass = classLoader.loadClass(metadata.fragmentClassName ?: throw IllegalArgumentException("fragmentClassName is required"))
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as? IPlugin
                ?: throw ClassCastException("Plugin class does not implement IPlugin")
            
            // 5. Create PluginContext
            val pluginContext = PluginContextImpl(
                appContext = context,
                pluginDir = File(pluginDirectory, metadata.pluginId),
                nativeLibManager = NativeLibraryManager()
            )
            
            // 6. Call onLoad()
            val loadSuccess = pluginInstance.onLoad(pluginContext)
            if (!loadSuccess) {
                return@withContext Result.failure(Exception("Plugin.onLoad() returned false"))
            }
            
            // 7. Store plugin info
            val pluginInfo = PluginInfo(
                pluginId = metadata.pluginId,
                metadata = metadata,
                instance = pluginInstance,
                classLoader = classLoader,
                state = PluginState.LOADED,
                loadedAt = System.currentTimeMillis()
            )
            
            loadedPlugins[metadata.pluginId] = pluginInfo
            classLoaders[metadata.pluginId] = classLoader
            
            Timber.i("Plugin loaded: ${metadata.pluginName} v${metadata.version}")
            Result.success(pluginInfo)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    /**
     * Enables a plugin
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            val enableSuccess = pluginInfo.instance.onEnable()
            if (!enableSuccess) {
                return@withContext Result.failure(Exception("Plugin.onEnable() returned false"))
            }
            
            // Update state
            loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.ENABLED)
            
            Timber.i("Plugin enabled: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Disables a plugin (but doesn't unload it)
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            val disableSuccess = pluginInfo.instance.onDisable()
            if (!disableSuccess) {
                return@withContext Result.failure(Exception("Plugin.onDisable() returned false"))
            }
            
            // Update state
            loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
            
            Timber.i("Plugin disabled: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Unloads a plugin
     */
    suspend fun unloadPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Disable first
            disablePlugin(pluginId).getOrNull()
            
            // Call onUnload()
            pluginInfo.instance.onUnload()
            
            // Cleanup
            classLoaders[pluginId]?.close()
            classLoaders.remove(pluginId)
            loadedPlugins.remove(pluginId)
            
            Timber.i("Plugin unloaded: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Returns all loaded plugins
     */
    fun getLoadedPlugins(): List<PluginInfo> =
        loadedPlugins.values.toList()
    
    /**
     * Returns all enabled plugins
     */
    fun getEnabledPlugins(): List<PluginInfo> =
        loadedPlugins.values.filter { it.state == PluginState.ENABLED }
    
    /**
     * Gets a specific plugin by ID
     */
    fun getPlugin(pluginId: String): PluginInfo? =
        loadedPlugins[pluginId]
    
    private suspend fun extractPluginMetadata(pluginFile: File): PluginMetadata = withContext(Dispatchers.Default) {
        // Read plugin-manifest.json from JAR/AAB
        JarFile(pluginFile).use { jar ->
            val manifestEntry = jar.getEntry("plugin-manifest.json")
                ?: throw IllegalArgumentException("No plugin-manifest.json found")
            
            val jsonString = jar.getInputStream(manifestEntry).bufferedReader().readText()
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
                category = PluginCategory.valueOf(json.optString("category", "UTILITY")),
                dependencies = json.optJSONArray("dependencies")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList()
            )
        }
    }
    
    private fun validatePlugin(metadata: PluginMetadata) {
        // Validate that app version is compatible
        val currentAppVersion = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        
        if (metadata.minAppVersion > currentAppVersion) {
            throw IllegalArgumentException(
                "Plugin requires app version ${metadata.minAppVersion}, " +
                "but current is $currentAppVersion"
            )
        }
        
        // Validate that fragmentClassName is not null
        if (metadata.fragmentClassName == null) {
            throw IllegalArgumentException("fragmentClassName is required")
        }
        
        // Validate API level
        val currentApiLevel = android.os.Build.VERSION.SDK_INT
        if (currentApiLevel < metadata.minApiLevel || currentApiLevel > metadata.maxApiLevel) {
            throw IllegalArgumentException(
                "Plugin requires API level ${metadata.minApiLevel}-${metadata.maxApiLevel}, " +
                "but current is $currentApiLevel"
            )
        }
    }
    
    fun shutdown() {
        scope.cancel()
        classLoaders.values.forEach { it.close() }
        classLoaders.clear()
        loadedPlugins.clear()
    }
}

/**
 * Implementation of PluginContext
 */
private class PluginContextImpl(
    private val appContext: Context,
    private val pluginDir: File,
    private val nativeLibManager: INativeLibraryManager
) : PluginContext {
    
    private val services = ConcurrentHashMap<String, Any>()
    
    override fun getApplicationContext(): Context = appContext
    override fun getPluginDirectory(): File = pluginDir.apply { mkdirs() }
    override fun getNativeLibraryManager(): INativeLibraryManager = nativeLibManager
    override fun registerService(name: String, service: Any) {
        services[name] = service
    }
    override fun getService(name: String): Any? = services[name]
    override fun logDebug(message: String) = Timber.d(message)
    override fun logError(message: String, throwable: Throwable?) =
        Timber.e(throwable, message)
}

/**
 * Native Library Manager implementation
 */
class NativeLibraryManager : INativeLibraryManager {
    
    private val loadedLibraries = mutableSetOf<String>()
    private val loadLock = Any()
    
    override suspend fun loadLibrary(
        libraryName: String,
        libraryPath: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            synchronized(loadLock) {
                if (loadedLibraries.contains(libraryName)) {
                    return@withContext Result.success(Unit)
                }
                
                // Copy .so to app lib directory
                val libDir = File("/data/data/com.ble1st.connectias/lib")
                if (!libDir.exists()) {
                    libDir.mkdirs()
                }
                
                val destFile = File(libDir, "lib$libraryName.so")
                libraryPath.copyTo(destFile, overwrite = true)
                
                // Load with System.load (absolute path)
                System.load(destFile.absolutePath)
                
                loadedLibraries.add(libraryName)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun unloadLibrary(libraryName: String): Result<Unit> = try {
        synchronized(loadLock) {
            loadedLibraries.remove(libraryName)
            // Note: Java/Android cannot actually unload .so files!
            // This is a known limitation
            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    override fun isLoaded(libraryName: String): Boolean =
        loadedLibraries.contains(libraryName)
    
    override fun getLoadedLibraries(): List<String> =
        loadedLibraries.toList()
}
