package com.ble1st.connectias.pluginmanager

import com.ble1st.connectias.api.IPlugin
import com.ble1st.connectias.api.PluginInfo
import com.ble1st.connectias.security.PluginSecurityManager
import com.ble1st.connectias.security.PluginValidator
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

class PluginLoader(
    private val pluginDir: File,
    private val securityManager: PluginSecurityManager,
    private val validator: PluginValidator
) {
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    
    suspend fun loadPlugin(pluginFile: File): Result<LoadedPlugin> {
        return try {
            // 1. Signatur verifizieren (jeder kann entwickeln, aber muss signiert sein)
            val signatureResult = securityManager.verifyPluginSignature(pluginFile)
            if (!signatureResult.isValid) {
                return Result.failure(Exception("Plugin signature verification failed: ${signatureResult.message}"))
            }
            
            // 2. ZIP extrahieren
            val extractDir = File(pluginDir, "extracted_${System.currentTimeMillis()}")
            extractDir.mkdirs()
            
            // 3. plugin.json parsen
            val manifest = extractManifest(pluginFile)
            if (manifest == null) {
                return Result.failure(Exception("Failed to extract plugin manifest"))
            }
            
            // 4. Validator aufrufen
            val validationResult = validator.validatePlugin(pluginFile)
            if (!validationResult.valid) {
                return Result.failure(Exception("Plugin validation failed: ${validationResult.errors.joinToString { it.message }}"))
            }
            
            // 5. Namenskonflikt prüfen
            if (loadedPlugins.containsKey(manifest.id)) {
                val existing = loadedPlugins[manifest.id]!!
                throw PluginConflictException(
                    "Plugin mit ID '${manifest.id}' ist bereits installiert (v${existing.info.version})"
                )
            }
            
            // 6. ClassLoader erstellen (separate pro Plugin)
            val classLoader = createPluginClassLoader(pluginFile, manifest.id)
            
            // 7. Entry-Point-Klasse laden
            val pluginClass = classLoader.loadClass(manifest.entryPoint)
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as IPlugin
            
            // 8. SecurityManager setzen
            val securityManager = securityManager.createSecurityManager(manifest)
            System.setSecurityManager(securityManager)
            
            // 9. Plugin in Registry speichern
            val loadedPlugin = LoadedPlugin(
                info = manifest,
                instance = pluginInstance,
                classLoader = classLoader,
                installPath = extractDir,
                state = PluginState.LOADED
            )
            
            loadedPlugins[manifest.id] = loadedPlugin
            Timber.i("Plugin loaded successfully: ${manifest.id}")
            
            Result.success(loadedPlugin)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin")
            Result.failure(e)
        }
    }
    
    fun unloadPlugin(pluginId: String): Result<Unit> {
        return try {
            val plugin = loadedPlugins[pluginId] ?: return Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Stop plugin if running
            plugin.instance.onStop()
            plugin.instance.onDestroy()
            
            // Remove from registry
            loadedPlugins.remove(pluginId)
            
            // Clean up files
            plugin.installPath.deleteRecursively()
            
            Timber.i("Plugin unloaded: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    suspend fun reloadPlugin(pluginId: String): Result<LoadedPlugin> {
        val plugin = loadedPlugins[pluginId] ?: return Result.failure(Exception("Plugin not found: $pluginId"))
        val pluginFile = File(plugin.installPath, "plugin.zip")
        
        // Unload first
        unloadPlugin(pluginId)
        
        // Load again
        return loadPlugin(pluginFile)
    }
    
    fun getLoadedPlugins(): List<LoadedPlugin> = loadedPlugins.values.toList()
    
    fun isPluginLoaded(pluginId: String): Boolean = loadedPlugins.containsKey(pluginId)
    
    private fun extractManifest(pluginFile: File): PluginInfo? {
        return try {
            val jarFile = JarFile(pluginFile)
            val manifestEntry = jarFile.getEntry("plugin.json")
            
            if (manifestEntry == null) {
                return null
            }
            
            jarFile.getInputStream(manifestEntry).use { inputStream ->
                val json = inputStream.bufferedReader().readText()
                Json.decodeFromString<PluginInfo>(json)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract manifest")
            null
        }
    }
    
    private fun createPluginClassLoader(pluginFile: File, pluginId: String): ClassLoader {
        val urls = arrayOf(pluginFile.toURI().toURL())
        return URLClassLoader.newInstance(urls, ClassLoader.getSystemClassLoader())
    }
}

data class LoadedPlugin(
    val info: PluginInfo,
    val instance: IPlugin,
    val classLoader: ClassLoader,
    val installPath: File,
    val state: PluginState
)

enum class PluginState { LOADED, RUNNING, STOPPED, ERROR }

class PluginConflictException(message: String) : Exception(message)
