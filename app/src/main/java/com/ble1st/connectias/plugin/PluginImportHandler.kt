package com.ble1st.connectias.plugin

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Handles importing plugin files from external sources
 */
class PluginImportHandler(
    private val context: Context,
    private val pluginDirectory: File,
    private val pluginManager: PluginManagerSandbox
) {
    
    /**
     * Import a plugin from a URI (file picker result)
     */
    suspend fun importPlugin(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        // Create temporary file
        val tempFile = File(context.cacheDir, "temp_plugin_${System.currentTimeMillis()}.apk")
        
        try {
            // Read file from URI and copy to temp file
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open file"))
            
            // Validate plugin file
            val validationResult = validatePluginFile(tempFile)
            if (validationResult.isFailure) {
                return@withContext validationResult
            }
            
            val pluginId = validationResult.getOrNull()
                ?: return@withContext Result.failure(Exception("Failed to extract plugin ID"))
            
            // Check if plugin already exists
            val targetFile = File(pluginDirectory, "$pluginId.apk")
            if (targetFile.exists()) {
                return@withContext Result.failure(Exception("Plugin already installed: $pluginId"))
            }
            
            // Copy to plugin directory
            tempFile.copyTo(targetFile, overwrite = false)
            
            // Set file to read-only to comply with Android security requirements
            // Android 10+ requires DEX files to be non-writable
            targetFile.setReadOnly()
            
            Timber.i("Plugin imported successfully: $pluginId")
            Result.success(pluginId)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to import plugin")
            Result.failure(e)
        } finally {
            // Cleanup temp file
            tempFile.delete()
        }
    }
    
    /**
     * Validate plugin file and extract plugin ID
     */
    private fun validatePluginFile(file: File): Result<String> {
        return try {
            // Check file extension
            if (!file.name.endsWith(".apk") && !file.name.endsWith(".jar")) {
                return Result.failure(Exception("Invalid file type. Only .apk and .jar files are supported"))
            }
            
            // Check file size (max 100MB)
            val maxSize = 100 * 1024 * 1024 // 100MB
            if (file.length() > maxSize) {
                return Result.failure(Exception("File too large. Maximum size is 100MB"))
            }
            
            // Validate ZIP structure
            ZipFile(file).use { zip ->
                // Check for manifest - try both locations: root (APK) and assets/ (AAR)
                val manifestEntry = zip.getEntry("plugin-manifest.json")
                    ?: zip.getEntry("assets/plugin-manifest.json")
                    ?: return Result.failure(Exception("Invalid plugin: Missing plugin-manifest.json"))
                
                // Extract and validate manifest
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(manifestJson)
                
                // Validate required fields
                val pluginId = json.optString("pluginId")
                if (pluginId.isEmpty()) {
                    return Result.failure(Exception("Invalid manifest: Missing pluginId"))
                }
                
                val pluginName = json.optString("pluginName")
                if (pluginName.isEmpty()) {
                    return Result.failure(Exception("Invalid manifest: Missing pluginName"))
                }
                
                val version = json.optString("version")
                if (version.isEmpty()) {
                    return Result.failure(Exception("Invalid manifest: Missing version"))
                }
                
                val fragmentClassName = json.optString("fragmentClassName")
                if (fragmentClassName.isEmpty()) {
                    return Result.failure(Exception("Invalid manifest: Missing fragmentClassName"))
                }
                
                Timber.d("Plugin validated: $pluginName v$version ($pluginId)")
                Result.success(pluginId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Plugin validation failed")
            Result.failure(Exception("Invalid plugin file: ${e.message}"))
        }
    }
    
    /**
     * Import plugin from file path
     */
    suspend fun importPluginFromPath(filePath: String): Result<String> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $filePath"))
            }
            
            importPlugin(Uri.fromFile(file))
        } catch (e: Exception) {
            Timber.e(e, "Failed to import plugin from path")
            Result.failure(e)
        }
    }
}
