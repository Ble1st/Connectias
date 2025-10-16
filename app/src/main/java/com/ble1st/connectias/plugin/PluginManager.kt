package com.ble1st.connectias.plugin

import android.content.Context
import android.net.Uri
import com.ble1st.connectias.api.*
import com.ble1st.connectias.storage.PluginDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PluginManager(private val context: Context) {
    
    private val databaseManager = PluginDatabaseManager(context)
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    
    data class LoadedPlugin(
        val pluginInfo: PluginInfo,
        val plugin: IPlugin,
        val isActive: Boolean = false
    )
    
    suspend fun installPlugin(uri: Uri): Result<PluginInfo> = withContext(Dispatchers.IO) {
        try {
            Timber.d("=== PLUGIN IMPORT START ===")
            Timber.d("URI: $uri")
            
            // 1. Plugin-Datei kopieren
            Timber.d("Step 1: Copying plugin file from URI")
            val pluginFile = copyPluginFile(uri)
            Timber.d("Plugin file copied to: ${pluginFile.absolutePath}")
            Timber.d("Plugin file exists: ${pluginFile.exists()}")
            Timber.d("Plugin file size: ${pluginFile.length()} bytes")
            
            // 2. Plugin validieren
            if (!pluginFile.exists()) {
                Timber.e("Plugin file does not exist after copying")
                return@withContext Result.failure(Exception("Plugin file not found"))
            }
            
            // 3. Plugin-Info aus plugin.json extrahieren
            Timber.d("Step 2: Extracting plugin manifest")
            val pluginInfo = extractPluginInfo(pluginFile)
            if (pluginInfo == null) {
                Timber.e("Failed to extract plugin manifest from: ${pluginFile.absolutePath}")
                return@withContext Result.failure(Exception("Failed to extract plugin manifest"))
            }
            
            Timber.d("Plugin manifest extracted successfully:")
            Timber.d("  - ID: ${pluginInfo.id}")
            Timber.d("  - Name: ${pluginInfo.name}")
            Timber.d("  - Version: ${pluginInfo.version}")
            Timber.d("  - Author: ${pluginInfo.author}")
            Timber.d("  - Permissions: ${pluginInfo.permissions}")
            Timber.d("  - Entry Point: ${pluginInfo.entryPoint}")
            
            // 4. Plugin in Datenbank speichern
            Timber.d("Step 3: Saving plugin to database")
            databaseManager.savePlugin(pluginInfo)
            
            Timber.i("=== PLUGIN IMPORT SUCCESS ===")
            Timber.i("Plugin installed successfully: ${pluginInfo.name}")
            Result.success(pluginInfo)
            
        } catch (e: Exception) {
            Timber.e("=== PLUGIN IMPORT FAILED ===")
            Timber.e(e, "Failed to install plugin: ${e.message}")
            Result.failure(e)
        }
    }
    
    private fun copyPluginFile(uri: Uri): File {
        Timber.d("copyPluginFile: Starting file copy from URI: $uri")
        
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open plugin file")
        
        Timber.d("copyPluginFile: Input stream opened successfully")
        
        val pluginDir = File(context.filesDir, "plugins")
        Timber.d("copyPluginFile: Plugin directory: ${pluginDir.absolutePath}")
        
        if (!pluginDir.exists()) {
            Timber.d("copyPluginFile: Creating plugin directory")
            pluginDir.mkdirs()
        }
        
        val pluginFile = File(pluginDir, "plugin_${System.currentTimeMillis()}.zip")
        Timber.d("copyPluginFile: Target file: ${pluginFile.absolutePath}")
        
        val outputStream = FileOutputStream(pluginFile)
        
        Timber.d("copyPluginFile: Starting file copy...")
        inputStream.use { input ->
            outputStream.use { output ->
                val bytesCopied = input.copyTo(output)
                Timber.d("copyPluginFile: Copied $bytesCopied bytes")
            }
        }
        
        Timber.d("copyPluginFile: File copy completed. Final size: ${pluginFile.length()} bytes")
        return pluginFile
    }
    
    private fun extractPluginInfo(pluginFile: File): PluginInfo? {
        return try {
            Timber.d("extractPluginInfo: Opening ZIP file: ${pluginFile.absolutePath}")
            val zipFile = java.util.zip.ZipFile(pluginFile)
            
            Timber.d("extractPluginInfo: Looking for plugin.json in ZIP")
            val manifestEntry = zipFile.getEntry("plugin.json")
            
            if (manifestEntry == null) {
                Timber.e("extractPluginInfo: plugin.json not found in plugin archive")
                Timber.d("extractPluginInfo: Available entries in ZIP:")
                zipFile.entries().asSequence().forEach { entry ->
                    Timber.d("  - ${entry.name}")
                }
                return null
            }
            
            Timber.d("extractPluginInfo: Found plugin.json, size: ${manifestEntry.size} bytes")
            
            zipFile.getInputStream(manifestEntry).use { inputStream ->
                val json = inputStream.bufferedReader().readText()
                Timber.d("extractPluginInfo: Raw JSON content:")
                Timber.d(json)
                
                val jsonObject = org.json.JSONObject(json)
                Timber.d("extractPluginInfo: Parsing JSON fields...")
                
                val id = jsonObject.getString("id")
                val name = jsonObject.getString("name")
                val version = jsonObject.getString("version")
                val description = jsonObject.getString("description")
                val author = jsonObject.getString("author")
                
                Timber.d("extractPluginInfo: Basic fields parsed:")
                Timber.d("  - ID: $id")
                Timber.d("  - Name: $name")
                Timber.d("  - Version: $version")
                Timber.d("  - Description: $description")
                Timber.d("  - Author: $author")
                
                val permissions = if (jsonObject.has("permissions")) {
                    Timber.d("extractPluginInfo: Parsing permissions...")
                    val permissionsArray = jsonObject.getJSONArray("permissions")
                    Timber.d("extractPluginInfo: Found ${permissionsArray.length()} permissions")
                    
                    (0 until permissionsArray.length()).mapNotNull { index ->
                        val permissionString = permissionsArray.getString(index)
                        Timber.d("extractPluginInfo: Processing permission: $permissionString")
                        try {
                            val permission = com.ble1st.connectias.api.PluginPermission.valueOf(permissionString)
                            Timber.d("extractPluginInfo: Permission converted successfully: $permission")
                            permission
                        } catch (e: IllegalArgumentException) {
                            Timber.w("extractPluginInfo: Unknown permission: $permissionString")
                            null
                        }
                    }
                } else {
                    Timber.d("extractPluginInfo: No permissions found in manifest")
                    emptyList()
                }
                
                val minCoreVersion = jsonObject.optString("minCoreVersion", "1.0.0")
                val maxCoreVersion = jsonObject.optString("maxCoreVersion", "2.0.0")
                val entryPoint = jsonObject.optString("entryPoint", "")
                
                Timber.d("extractPluginInfo: Optional fields:")
                Timber.d("  - Min Core Version: $minCoreVersion")
                Timber.d("  - Max Core Version: $maxCoreVersion")
                Timber.d("  - Entry Point: $entryPoint")
                
                val pluginInfo = PluginInfo(
                    id = id,
                    name = name,
                    version = version,
                    description = description,
                    author = author,
                    permissions = permissions,
                    minCoreVersion = minCoreVersion,
                    maxCoreVersion = maxCoreVersion,
                    entryPoint = entryPoint
                )
                
                Timber.d("extractPluginInfo: PluginInfo object created successfully")
                pluginInfo
            }
        } catch (e: Exception) {
            Timber.e("extractPluginInfo: Failed to extract plugin manifest")
            Timber.e(e, "Exception details: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    suspend fun loadPlugin(pluginInfo: PluginInfo): Result<LoadedPlugin> = withContext(Dispatchers.IO) {
        try {
            // Simplified plugin loading
            val plugin = object : IPlugin {
                override fun onCreate(context: PluginContext) {
                    Timber.d("Plugin ${pluginInfo.id} created")
                }
                
                override fun onStart() {
                    Timber.d("Plugin ${pluginInfo.id} started")
                }
                
                override fun onStop() {
                    Timber.d("Plugin ${pluginInfo.id} stopped")
                }
                
                override fun onDestroy() {
                    Timber.d("Plugin ${pluginInfo.id} destroyed")
                }
                
                override fun getPluginInfo(): PluginInfo {
                    return pluginInfo
                }
            }
            
            val loadedPlugin = LoadedPlugin(pluginInfo, plugin, true)
            loadedPlugins[pluginInfo.id] = loadedPlugin
            
            Timber.i("Plugin loaded successfully: ${pluginInfo.name}")
            Result.success(loadedPlugin)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: ${pluginInfo.name}")
            Result.failure(e)
        }
    }
    
    suspend fun unloadPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            loadedPlugins.remove(pluginId)
            Timber.i("Plugin unloaded: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    fun getLoadedPlugins(): List<LoadedPlugin> = loadedPlugins.values.toList()
    
    fun isPluginLoaded(pluginId: String): Boolean = loadedPlugins.containsKey(pluginId)
}