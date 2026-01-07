package com.ble1st.connectias.core.plugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.ble1st.connectias.plugin.IPluginSandbox
import com.ble1st.connectias.plugin.PluginMetadataParcel
import com.ble1st.connectias.plugin.PluginResultParcel
import dalvik.system.DexClassLoader
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.ble1st.connectias.plugin.IPlugin
import com.ble1st.connectias.plugin.PluginMetadata
import org.json.JSONObject
import java.util.jar.JarFile

/**
 * Isolated service that runs plugins in a separate process
 * This provides crash isolation and memory isolation from the main app
 */
class PluginSandboxService : Service() {
    
    private val loadedPlugins = ConcurrentHashMap<String, SandboxPluginInfo>()
    private val classLoaders = ConcurrentHashMap<String, DexClassLoader>()
    
    data class SandboxPluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val instance: IPlugin,
        val state: PluginState
    )
    
    enum class PluginState {
        LOADED,
        ENABLED,
        DISABLED
    }
    
    private val binder = object : IPluginSandbox.Stub() {
        
        override fun loadPlugin(pluginPath: String): PluginResultParcel {
            return try {
                Timber.d("[SANDBOX] Loading plugin from: $pluginPath")
                
                val pluginFile = File(pluginPath)
                if (!pluginFile.exists()) {
                    return PluginResultParcel.failure("Plugin file not found: $pluginPath")
                }
                
                // Extract metadata
                val metadata = extractPluginMetadata(pluginFile)
                
                // Create DEX output directory
                val dexOutputDir = File(cacheDir, "sandbox_plugins/${metadata.pluginId}")
                dexOutputDir.mkdirs()
                
                // Create DexClassLoader
                val classLoader = DexClassLoader(
                    pluginFile.absolutePath,
                    dexOutputDir.absolutePath,
                    null,
                    this@PluginSandboxService.classLoader
                )
                
                // Load plugin class
                val pluginClass = classLoader.loadClass(
                    metadata.fragmentClassName ?: throw IllegalArgumentException("fragmentClassName required")
                )
                val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as? IPlugin
                    ?: throw ClassCastException("Plugin does not implement IPlugin")
                
                // Create minimal PluginContext for sandbox
                val pluginContext = SandboxPluginContext(
                    appContext = applicationContext,
                    pluginDir = File(filesDir, "sandbox_plugins/${metadata.pluginId}"),
                    pluginId = metadata.pluginId
                )
                
                // Call onLoad
                val loadSuccess = try {
                    pluginInstance.onLoad(pluginContext)
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onLoad failed")
                    false
                }
                
                if (!loadSuccess) {
                    dexOutputDir.deleteRecursively()
                    return PluginResultParcel.failure("Plugin onLoad() returned false")
                }
                
                // Store plugin info
                val pluginInfo = SandboxPluginInfo(
                    pluginId = metadata.pluginId,
                    metadata = metadata,
                    instance = pluginInstance,
                    state = PluginState.LOADED
                )
                
                loadedPlugins[metadata.pluginId] = pluginInfo
                classLoaders[metadata.pluginId] = classLoader
                
                Timber.i("[SANDBOX] Plugin loaded: ${metadata.pluginName}")
                PluginResultParcel.success(PluginMetadataParcel.fromPluginMetadata(metadata))
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to load plugin")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }
        
        override fun enablePlugin(pluginId: String): PluginResultParcel {
            return try {
                val pluginInfo = loadedPlugins[pluginId]
                    ?: return PluginResultParcel.failure("Plugin not found: $pluginId")
                
                val enableSuccess = try {
                    pluginInfo.instance.onEnable()
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onEnable failed")
                    false
                }
                
                if (!enableSuccess) {
                    return PluginResultParcel.failure("Plugin onEnable() returned false")
                }
                
                loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.ENABLED)
                Timber.i("[SANDBOX] Plugin enabled: $pluginId")
                PluginResultParcel.success()
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to enable plugin")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }
        
        override fun disablePlugin(pluginId: String): PluginResultParcel {
            return try {
                val pluginInfo = loadedPlugins[pluginId]
                    ?: return PluginResultParcel.failure("Plugin not found: $pluginId")
                
                val disableSuccess = try {
                    pluginInfo.instance.onDisable()
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onDisable failed")
                    false
                }
                
                if (!disableSuccess) {
                    return PluginResultParcel.failure("Plugin onDisable() returned false")
                }
                
                loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
                Timber.i("[SANDBOX] Plugin disabled: $pluginId")
                PluginResultParcel.success()
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to disable plugin")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }
        
        override fun unloadPlugin(pluginId: String): PluginResultParcel {
            return try {
                val pluginInfo = loadedPlugins[pluginId]
                    ?: return PluginResultParcel.failure("Plugin not found: $pluginId")
                
                // Disable first
                if (pluginInfo.state == PluginState.ENABLED) {
                    disablePlugin(pluginId)
                }
                
                // Call onUnload
                try {
                    pluginInfo.instance.onUnload()
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onUnload failed")
                }
                
                // Cleanup
                classLoaders.remove(pluginId)
                loadedPlugins.remove(pluginId)
                File(cacheDir, "sandbox_plugins/$pluginId").deleteRecursively()
                
                Timber.i("[SANDBOX] Plugin unloaded: $pluginId")
                PluginResultParcel.success()
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to unload plugin")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }
        
        override fun getLoadedPlugins(): List<String> {
            return loadedPlugins.keys.toList()
        }
        
        override fun getPluginMetadata(pluginId: String): PluginMetadataParcel? {
            val pluginInfo = loadedPlugins[pluginId] ?: return null
            return PluginMetadataParcel.fromPluginMetadata(pluginInfo.metadata)
        }
        
        override fun ping(): Boolean {
            return true
        }
        
        override fun shutdown() {
            Timber.i("[SANDBOX] Shutting down sandbox")
            loadedPlugins.keys.toList().forEach { pluginId ->
                try {
                    unloadPlugin(pluginId)
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Error unloading plugin during shutdown")
                }
            }
            stopSelf()
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        Timber.i("[SANDBOX] Service bound")
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("[SANDBOX] Service created in process: ${android.os.Process.myPid()}")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[SANDBOX] Service destroyed")
    }
    
    private fun extractPluginMetadata(pluginFile: File): PluginMetadata {
        JarFile(pluginFile).use { jar ->
            val manifestEntry = jar.getEntry("plugin-manifest.json")
                ?: throw IllegalArgumentException("No plugin-manifest.json found")
            
            val jsonString = jar.getInputStream(manifestEntry).bufferedReader().readText()
            val json = JSONObject(jsonString)
            
            val requirements = json.optJSONObject("requirements") ?: JSONObject()
            
            return PluginMetadata(
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
                category = com.ble1st.connectias.plugin.PluginCategory.valueOf(
                    json.optString("category", "UTILITY")
                ),
                dependencies = json.optJSONArray("dependencies")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList()
            )
        }
    }
}
