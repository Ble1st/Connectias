package com.ble1st.connectias.plugin

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import com.ble1st.connectias.api.*
import com.ble1st.connectias.security.PluginValidator
import com.ble1st.connectias.storage.PluginDatabaseManager
import com.ble1st.connectias.storage.database.entity.PluginEntity
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class PluginManager(private val context: Context) {
    
    private val pluginValidator = PluginValidator()
    private val databaseManager = PluginDatabaseManager(context)
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    
    data class LoadedPlugin(
        val pluginInfo: PluginInfo,
        val plugin: IPlugin,
        val classLoader: DexClassLoader,
        val isActive: Boolean = false
    )
    
    suspend fun installPlugin(pluginUri: Uri): PluginInstallResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Plugin-Datei kopieren
                val pluginFile = copyPluginFile(pluginUri)
                
                // 2. Plugin validieren
                val validationResult = pluginValidator.validatePluginZip(pluginFile)
                if (validationResult is PluginValidator.ValidationResult.Error) {
                    return@withContext PluginInstallResult.Error("Validation failed: ${validationResult.message}")
                }
                
                val pluginInfo = (validationResult as PluginValidator.ValidationResult.Success).pluginInfo
                
                // 3. Plugin laden
                val loadedPlugin = loadPluginFromZip(pluginFile, pluginInfo)
                
                // 4. In Datenbank speichern
                savePluginToDatabase(pluginInfo)
                
                // 5. Plugin registrieren
                loadedPlugins[pluginInfo.id] = loadedPlugin
                
                Timber.i("Plugin installed successfully: ${pluginInfo.id}")
                PluginInstallResult.Success(pluginInfo)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to install plugin")
                PluginInstallResult.Error("Installation failed: ${e.message}")
            }
        }
    }
    
    suspend fun uninstallPlugin(pluginId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val plugin = loadedPlugins[pluginId] ?: return@withContext false
                
                // Plugin stoppen
                plugin.plugin.onStop()
                plugin.plugin.onDestroy()
                
                // Aus Registry entfernen
                loadedPlugins.remove(pluginId)
                
                // Aus Datenbank entfernen
                databaseManager.getDatabase().pluginDao().deletePlugin(pluginId)
                
                // Plugin-Dateien löschen
                deletePluginFiles(pluginId)
                
                Timber.i("Plugin uninstalled: $pluginId")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to uninstall plugin: $pluginId")
                false
            }
        }
    }
    
    suspend fun startPlugin(pluginId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val plugin = loadedPlugins[pluginId] ?: return@withContext false
                
                // Plugin-Context erstellen
                val pluginContext = createPluginContext(pluginId)
                
                // Plugin starten
                plugin.plugin.onCreate(pluginContext)
                plugin.plugin.onStart()
                
                // Status aktualisieren
                loadedPlugins[pluginId] = plugin.copy(isActive = true)
                
                Timber.i("Plugin started: $pluginId")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to start plugin: $pluginId")
                false
            }
        }
    }
    
    suspend fun stopPlugin(pluginId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val plugin = loadedPlugins[pluginId] ?: return@withContext false
                
                // Plugin stoppen
                plugin.plugin.onStop()
                plugin.plugin.onDestroy()
                
                // Status aktualisieren
                loadedPlugins[pluginId] = plugin.copy(isActive = false)
                
                Timber.i("Plugin stopped: $pluginId")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop plugin: $pluginId")
                false
            }
        }
    }
    
    fun getLoadedPlugins(): List<LoadedPlugin> {
        return loadedPlugins.values.toList()
    }
    
    fun getActivePlugins(): List<LoadedPlugin> {
        return loadedPlugins.values.filter { it.isActive }
    }
    
    private fun copyPluginFile(pluginUri: Uri): File {
        val pluginDir = File(context.filesDir, "plugins")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
        
        val pluginFile = File(pluginDir, "temp_plugin.zip")
        context.contentResolver.openInputStream(pluginUri)?.use { inputStream ->
            FileOutputStream(pluginFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        return pluginFile
    }
    
    private fun loadPluginFromZip(pluginFile: File, pluginInfo: PluginInfo): LoadedPlugin {
        val pluginDir = File(context.filesDir, "plugins/${pluginInfo.id}")
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
        
        // DEX-Datei extrahieren
        val dexFile = File(pluginDir, "${pluginInfo.id}.dex")
        ZipInputStream(pluginFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".dex")) {
                    FileOutputStream(dexFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        
        // ClassLoader erstellen
        val optimizedDir = File(pluginDir, "optimized")
        if (!optimizedDir.exists()) {
            optimizedDir.mkdirs()
        }
        
        val classLoader = DexClassLoader(
            dexFile.absolutePath,
            optimizedDir.absolutePath,
            null,
            context.classLoader
        )
        
        // Plugin-Instanz laden
        val pluginClass = classLoader.loadClass(pluginInfo.entryPoint ?: "com.example.helloplugin.HelloPlugin")
        val plugin = pluginClass.newInstance() as IPlugin
        
        return LoadedPlugin(pluginInfo, plugin, classLoader)
    }
    
    private fun createPluginContext(pluginId: String): PluginContext {
        return object : PluginContext {
            override val storageService = PluginStorageService(context, pluginId)
            override val networkService = PluginNetworkService(pluginId)
            override val logger = PluginLoggerImpl(context, pluginId)
            override val systemInfoService = SystemInfoServiceImpl(context, pluginId)
        }
    }
    
    private suspend fun savePluginToDatabase(pluginInfo: PluginInfo) {
        val pluginEntity = PluginEntity(
            pluginId = pluginInfo.id,
            name = pluginInfo.name,
            version = pluginInfo.version,
            description = pluginInfo.description,
            developer = pluginInfo.developer,
            isActive = false,
            installDate = System.currentTimeMillis()
        )
        
        databaseManager.getDatabase().pluginDao().insertPlugin(pluginEntity)
    }
    
    private fun deletePluginFiles(pluginId: String) {
        val pluginDir = File(context.filesDir, "plugins/$pluginId")
        if (pluginDir.exists()) {
            pluginDir.deleteRecursively()
        }
    }
}

sealed class PluginInstallResult {
    data class Success(val pluginInfo: PluginInfo) : PluginInstallResult()
    data class Error(val message: String) : PluginInstallResult()
}