package com.ble1st.connectias.plugin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ble1st.connectias.plugin.declarative.model.DeclarativeJson
import com.ble1st.connectias.plugin.declarative.model.DeclarativePluginValidator
import com.ble1st.connectias.plugin.security.DeclarativeDeveloperTrustStore
import com.ble1st.connectias.plugin.security.DeclarativePluginSignatureVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Handles importing plugin files from external sources
 * Validates permissions and enforces security policies
 */
class PluginImportHandler(
    private val context: Context,
    private val pluginDirectory: File,
    private val pluginManager: PluginManagerSandbox,
    private val manifestParser: PluginManifestParser,
    private val permissionManager: PluginPermissionManager
) {

    init {
        if (!pluginDirectory.exists()) {
            pluginDirectory.mkdirs()
        }
    }
    
    /**
     * Import a plugin from a URI (file picker result)
     */
    suspend fun importPlugin(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val displayName = getDisplayName(uri) ?: "plugin_${System.currentTimeMillis()}"
        val ext = displayName.substringAfterLast('.', missingDelimiterValue = "apk").lowercase()
        val normalizedExt = when (ext) {
            "apk", "jar", "cplug" -> ext
            else -> "apk"
        }

        // Create temporary file (keep extension so validation can branch)
        val tempFile = File(context.cacheDir, "temp_plugin_${System.currentTimeMillis()}.$normalizedExt")
        
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
            val targetExt = when (normalizedExt) {
                "cplug" -> "cplug"
                "jar" -> "jar"
                else -> "apk"
            }
            val targetFile = File(pluginDirectory, "$pluginId.$targetExt")
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
     * Validate plugin file, extract plugin ID, and check permissions
     */
    private suspend fun validatePluginFile(file: File): Result<String> {
        return try {
            // Check file extension
            val ext = file.extension.lowercase()
            if (ext !in setOf("apk", "jar", "cplug")) {
                return Result.failure(Exception("Invalid file type. Only .apk, .jar and .cplug files are supported"))
            }
            
            // Check file size (max 500MB)
            val maxSize = 500 * 1024 * 1024 // 500MB
            if (file.length() > maxSize) {
                return Result.failure(Exception("File too large. Maximum size is 500MB"))
            }
            
            // Validate ZIP structure
            ZipFile(file).use { zip ->
                // Basic zip entry safety (prevent path traversal)
                zip.entries().asSequence().forEach { entry ->
                    val name = entry.name
                    if (name.startsWith("/") || name.contains("..")) {
                        return Result.failure(Exception("Invalid package: suspicious entry path"))
                    }
                }

                if (ext == "cplug") {
                    val manifestEntry = zip.getEntry("plugin-manifest.json")
                        ?: return Result.failure(Exception("Invalid declarative plugin: Missing plugin-manifest.json"))
                    val signatureEntry = zip.getEntry("signature.json")
                        ?: return Result.failure(Exception("Invalid declarative plugin: Missing signature.json"))

                    val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                    val manifestObj = org.json.JSONObject(manifestJson)
                    val manifest = DeclarativeJson.parseManifest(manifestObj)
                    val validation = DeclarativePluginValidator.validateManifest(manifest)
                    if (!validation.ok) {
                        return Result.failure(Exception("Invalid declarative manifest: ${validation.errors.joinToString("; ")}"))
                    }

                    // Ensure referenced entrypoints exist
                    manifest.entrypoints.screens.forEach { path ->
                        if (zip.getEntry(path) == null) {
                            return Result.failure(Exception("Missing referenced UI screen: $path"))
                        }
                    }
                    manifest.entrypoints.flows.forEach { path ->
                        if (zip.getEntry(path) == null) {
                            return Result.failure(Exception("Missing referenced flow: $path"))
                        }
                    }

                    // Signature verification + truststore (default deny for unknown keys)
                    val verifier = DeclarativePluginSignatureVerifier(DeclarativeDeveloperTrustStore(context))
                    when (val sigResult = verifier.verifyPackage(file)) {
                        is DeclarativePluginSignatureVerifier.Result.Ok -> {
                            Timber.i("Declarative plugin signature OK: ${manifest.pluginId} (dev=${sigResult.developerId})")
                        }
                        is DeclarativePluginSignatureVerifier.Result.Suspicious -> {
                            Timber.w("Declarative plugin signature suspicious: ${sigResult.warnings.joinToString()}")
                        }
                        is DeclarativePluginSignatureVerifier.Result.Failed -> {
                            return Result.failure(SecurityException("Declarative signature verification failed: ${sigResult.reason}"))
                        }
                    }

                    Timber.d("Declarative plugin validated: ${manifest.pluginName} v${manifest.versionName} (${manifest.pluginId})")
                    return Result.success(manifest.pluginId)
                }

                // APK/JAR plugins: Check for manifest - try both locations: root (APK) and assets/ (AAR)
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

                // fragmentClassName is optional - required for legacy plugins, optional for new Three-Process UI plugins
                // New plugins use onRenderUI() API and don't need fragmentClassName
                @Suppress("UNUSED_VARIABLE")
                val fragmentClassName = json.optString("fragmentClassName", null)

                // Extract and validate permissions
                val permissionResult = manifestParser.extractPermissions(file)
                if (permissionResult.isFailure) {
                    Timber.w("Failed to extract permissions, continuing anyway")
                } else {
                    val permissionInfo = permissionResult.getOrNull()
                    if (permissionInfo != null) {
                        // Block plugins with critical permissions
                        if (permissionInfo.hasCriticalPermissions()) {
                            Timber.e("Plugin $pluginName requests critical permissions: ${permissionInfo.critical}")
                            return Result.failure(
                                SecurityException(
                                    "Plugin requests critical permissions that are not allowed: " +
                                        permissionInfo.critical.joinToString()
                                )
                            )
                        }

                        // Warn about missing permissions
                        if (permissionInfo.missing.isNotEmpty()) {
                            Timber.w("Plugin $pluginName requests permissions not available in host app: ${permissionInfo.missing}")
                        }

                        // Log dangerous permissions (will require user consent later)
                        if (permissionInfo.hasDangerousPermissions()) {
                            Timber.i("Plugin $pluginName requests dangerous permissions (will require user consent): ${permissionInfo.dangerous}")
                        }
                    }
                }

                Timber.d("Plugin validated: $pluginName v$version ($pluginId)")
                Result.success(pluginId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Plugin validation failed")
            Result.failure(Exception("Invalid plugin file: ${e.message}"))
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Import plugin from file path
     */
    suspend fun importPluginFromPath(filePath: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File not found: $filePath"))
                }

                // Direct file import: avoids Uri metadata loss (e.g. .cplug treated as .apk)
                val validationResult = validatePluginFile(file)
                if (validationResult.isFailure) {
                    return@withContext validationResult
                }

                val pluginId = validationResult.getOrNull()
                    ?: return@withContext Result.failure(Exception("Failed to extract plugin ID"))

                val ext = file.extension.lowercase()
                val targetExt = when (ext) {
                    "cplug" -> "cplug"
                    "jar" -> "jar"
                    else -> "apk"
                }

                val targetFile = File(pluginDirectory, "$pluginId.$targetExt")
                if (targetFile.exists()) {
                    return@withContext Result.failure(Exception("Plugin already installed: $pluginId"))
                }

                file.copyTo(targetFile, overwrite = false)
                targetFile.setReadOnly()

                Timber.i("Plugin imported successfully from path: $pluginId")
                Result.success(pluginId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to import plugin from path")
                Result.failure(e)
            }
        }
    }
}
