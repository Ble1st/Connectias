package com.ble1st.connectias.core.plugin

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PluginImportService(
    private val context: Context,
    private val pluginDirectory: File,
    private val validator: PluginValidator,
    private val signatureValidator: PluginSignatureValidator
) {
    
    companion object {
        private const val MAX_PLUGIN_SIZE = 100 * 1024 * 1024L // 100 MB
        private val ALLOWED_EXTENSIONS = setOf("apk", "jar")
    }
    
    suspend fun importFromExternalPath(sourcePath: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            
            if (!sourceFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Source file does not exist: $sourcePath")
                )
            }
            
            validateFileSize(sourceFile).getOrElse { 
                return@withContext Result.failure(it)
            }
            
            validateFileExtension(sourceFile).getOrElse {
                return@withContext Result.failure(it)
            }
            
            signatureValidator.validateSignature(sourceFile).getOrElse {
                Timber.w("Signature validation failed, but continuing: ${it.message}")
            }
            
            validator.validate(sourceFile).getOrElse {
                return@withContext Result.failure(it)
            }
            
            val metadata = extractMetadata(sourceFile).getOrElse {
                return@withContext Result.failure(it)
            }
            
            val destFile = File(pluginDirectory, "${metadata.pluginId}.${sourceFile.extension}")
            
            if (destFile.exists()) {
                Timber.w("Plugin already exists, will be overwritten: ${metadata.pluginId}")
                destFile.delete()
            }
            
            sourceFile.copyTo(destFile, overwrite = true)
            
            Timber.i("Plugin imported successfully: ${metadata.pluginName} -> ${destFile.absolutePath}")
            Result.success(destFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to import plugin from: $sourcePath")
            Result.failure(e)
        }
    }
    
    suspend fun importFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot open URI: $uri"))
            
            val tempFile = File(context.cacheDir, "plugin_import_${System.currentTimeMillis()}.tmp")
            
            try {
                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                validateFileSize(tempFile).getOrElse {
                    tempFile.delete()
                    return@withContext Result.failure(it)
                }
                
                signatureValidator.validateSignature(tempFile).getOrElse {
                    Timber.w("Signature validation failed: ${it.message}")
                }
                
                validator.validate(tempFile).getOrElse {
                    tempFile.delete()
                    return@withContext Result.failure(it)
                }
                
                val metadata = extractMetadata(tempFile).getOrElse {
                    tempFile.delete()
                    return@withContext Result.failure(it)
                }
                
                val extension = getFileExtensionFromUri(uri) ?: "apk"
                val destFile = File(pluginDirectory, "${metadata.pluginId}.$extension")
                
                if (destFile.exists()) {
                    Timber.w("Plugin already exists, will be overwritten: ${metadata.pluginId}")
                    destFile.delete()
                }
                
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
                
                Timber.i("Plugin imported from URI: ${metadata.pluginName} -> ${destFile.absolutePath}")
                Result.success(destFile)
                
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to import plugin from URI: $uri")
            Result.failure(e)
        }
    }
    
    suspend fun getPluginHash(file: File): Result<String> = withContext(Dispatchers.IO) {
        signatureValidator.getPluginHash(file)
    }
    
    private fun validateFileSize(file: File): Result<Unit> {
        return if (file.length() > MAX_PLUGIN_SIZE) {
            Result.failure(
                IllegalArgumentException(
                    "Plugin file too large: ${file.length()} bytes (max: $MAX_PLUGIN_SIZE)"
                )
            )
        } else {
            Result.success(Unit)
        }
    }
    
    private fun validateFileExtension(file: File): Result<Unit> {
        return if (file.extension in ALLOWED_EXTENSIONS) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "Invalid file extension: ${file.extension} (allowed: ${ALLOWED_EXTENSIONS.joinToString()})"
                )
            )
        }
    }
    
    private suspend fun extractMetadata(file: File): Result<com.ble1st.connectias.plugin.PluginMetadata> = 
        withContext(Dispatchers.IO) {
            try {
                val jarFile = java.util.jar.JarFile(file)
                jarFile.use { jar ->
                    val manifestEntry = jar.getEntry("plugin-manifest.json")
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("No plugin-manifest.json found in plugin file")
                        )
                    
                    val jsonString = jar.getInputStream(manifestEntry).bufferedReader().readText()
                    val json = org.json.JSONObject(jsonString)
                    
                    val requirements = json.optJSONObject("requirements") ?: org.json.JSONObject()
                    
                    val metadata = com.ble1st.connectias.plugin.PluginMetadata(
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
                    
                    Result.success(metadata)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract metadata from: ${file.name}")
                Result.failure(e)
            }
        }
    
    private fun getFileExtensionFromUri(uri: Uri): String? {
        val path = uri.path ?: return null
        val lastDot = path.lastIndexOf('.')
        return if (lastDot >= 0) {
            path.substring(lastDot + 1)
        } else {
            null
        }
    }
}
