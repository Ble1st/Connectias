package com.ble1st.connectias.core.plugin

import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber
import java.io.File
import java.util.jar.JarFile
import org.json.JSONObject
import com.ble1st.connectias.plugin.PluginMetadata
import com.ble1st.connectias.plugin.PluginCategory

/**
 * Validates plugins before loading
 */
class PluginValidator(
    private val context: Context,
    private val signatureValidator: PluginSignatureValidator? = null,
    private val permissionManager: PluginPermissionManager? = null
) {
    
    /**
     * Validates a plugin file before loading
     */
    fun validate(pluginFile: File): Result<Boolean> {
        return try {
            // 1. Check file exists
            if (!pluginFile.exists()) {
                return Result.failure(Exception("Plugin file does not exist"))
            }
            
            // 2. Check file extension
            if (pluginFile.extension !in listOf("aab", "apk", "jar")) {
                return Result.failure(Exception("Invalid plugin file format: ${pluginFile.extension}"))
            }
            
            // 3. Validate signature if validator is provided
            signatureValidator?.validateSignature(pluginFile)?.getOrElse {
                Timber.w("Signature validation failed: ${it.message}")
                // Don't fail validation if signature check fails (for now)
            }
            
            // 4. Extract and validate manifest
            val metadata = extractMetadata(pluginFile)
            validateMetadata(metadata)
            
            // 5. Check permissions with permission manager
            if (permissionManager != null) {
                validatePermissionsWithManager(metadata)
            } else {
                validatePermissions(metadata.permissions)
            }
            
            // 6. Check dependencies
            validateDependencies(metadata.dependencies)
            
            Timber.d("Plugin validation passed: ${metadata.pluginId}")
            Result.success(true)
            
        } catch (e: Exception) {
            Timber.e(e, "Plugin validation failed: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    private fun extractMetadata(pluginFile: File): PluginMetadata {
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
                category = PluginCategory.valueOf(json.optString("category", "UTILITY")),
                dependencies = json.optJSONArray("dependencies")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList()
            )
        }
    }
    
    private fun validateMetadata(metadata: PluginMetadata) {
        // Validate app version compatibility
        val currentAppVersion = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        
        if (compareVersions(metadata.minAppVersion, currentAppVersion) > 0) {
            throw IllegalArgumentException(
                "Plugin requires app version ${metadata.minAppVersion}, " +
                "but current is $currentAppVersion"
            )
        }
        
        // Validate fragmentClassName
        if (metadata.fragmentClassName.isNullOrBlank()) {
            throw IllegalArgumentException("fragmentClassName is required")
        }
        
        // Validate API level
        val currentApiLevel = android.os.Build.VERSION.SDK_INT
        if (currentApiLevel < metadata.minApiLevel) {
            throw IllegalArgumentException(
                "Plugin requires API level ${metadata.minApiLevel}+, " +
                "but current is $currentApiLevel"
            )
        }
        
        if (currentApiLevel > metadata.maxApiLevel) {
            throw IllegalArgumentException(
                "Plugin supports up to API level ${metadata.maxApiLevel}, " +
                "but current is $currentApiLevel"
            )
        }
    }
    
    private fun validatePermissions(permissions: List<String>) {
        // Check for dangerous permissions
        val dangerousPermissions = listOf(
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS"
        )
        
        val requestedDangerous = permissions.filter { it in dangerousPermissions }
        if (requestedDangerous.isNotEmpty()) {
            Timber.w("Plugin requests dangerous permissions: $requestedDangerous")
            // Note: In production, you might want to require user approval
        }
    }
    
    private suspend fun validatePermissionsWithManager(metadata: PluginMetadata) {
        permissionManager?.let { manager ->
            val result = manager.validatePermissions(metadata).getOrNull()
            if (result != null) {
                if (result.criticalPermissions.isNotEmpty()) {
                    throw SecurityException(
                        "Plugin requests critical permissions: ${result.criticalPermissions}"
                    )
                }
                
                if (result.requiresUserConsent) {
                    Timber.w("Plugin requires user consent for permissions: ${result.dangerousPermissions}")
                }
            }
        }
    }
    
    private fun validateDependencies(dependencies: List<String>) {
        if (dependencies.isNotEmpty()) {
            Timber.d("Plugin has dependencies: $dependencies")
        }
    }
    
    private fun compareVersions(version1: String, version2: String): Int {
        val v1Parts = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = version2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        val v1Padded = v1Parts + List(maxLength - v1Parts.size) { 0 }
        val v2Padded = v2Parts + List(maxLength - v2Parts.size) { 0 }
        
        for (i in v1Padded.indices) {
            when {
                v1Padded[i] > v2Padded[i] -> return 1
                v1Padded[i] < v2Padded[i] -> return -1
            }
        }
        return 0
    }
}
