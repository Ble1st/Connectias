package com.ble1st.connectias.plugin

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

/**
 * Parses plugin manifests and extracts permissions
 * Supports both APK AndroidManifest.xml and plugin-manifest.json
 */
class PluginManifestParser(
    private val context: Context,
    private val permissionManager: PluginPermissionManager
) {
    
    /**
     * Extracts permissions from a plugin file
     * Tries APK manifest first, falls back to plugin-manifest.json
     */
    suspend fun extractPermissions(pluginFile: File): Result<PluginPermissionInfo> = 
        withContext(Dispatchers.IO) {
            try {
                // 1. Try to extract from APK AndroidManifest.xml
                val apkPermissions = extractFromAPK(pluginFile)
                
                // 2. Try to extract from plugin-manifest.json
                val jsonPermissions = extractFromJSON(pluginFile)
                
                // 3. Merge permissions from both sources (deduplicate)
                val allPermissions = (apkPermissions + jsonPermissions).distinct()
                
                if (allPermissions.isEmpty()) {
                    Timber.w("No permissions found in plugin: ${pluginFile.name}")
                }
                
                // 4. Check which permissions the host app has
                val hostPermissions = getHostAppPermissions()
                
                // 5. Classify permissions
                val available = allPermissions.filter { it in hostPermissions }
                val missing = allPermissions.filterNot { it in hostPermissions }
                val dangerous = permissionManager.getDangerousPermissions(allPermissions)
                val critical = permissionManager.getCriticalPermissions(allPermissions)
                
                Timber.d("""
                    Plugin permissions extracted: ${pluginFile.name}
                    Total: ${allPermissions.size}
                    Available: ${available.size}
                    Missing: ${missing.size}
                    Dangerous: ${dangerous.size}
                    Critical: ${critical.size}
                """.trimIndent())
                
                Result.success(
                    PluginPermissionInfo(
                        allPermissions = allPermissions,
                        available = available,
                        missing = missing,
                        dangerous = dangerous,
                        critical = critical
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract permissions from plugin: ${pluginFile.name}")
                Result.failure(e)
            }
        }
    
    /**
     * Extracts permissions from APK AndroidManifest.xml
     */
    private fun extractFromAPK(file: File): List<String> {
        return try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_PERMISSIONS
            )
            
            val permissions = packageInfo?.requestedPermissions?.toList() ?: emptyList()
            
            if (permissions.isNotEmpty()) {
                Timber.d("Extracted ${permissions.size} permissions from APK manifest: ${file.name}")
            }
            
            permissions
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract permissions from APK manifest: ${file.name}")
            emptyList()
        }
    }
    
    /**
     * Extracts permissions from plugin-manifest.json
     */
    private fun extractFromJSON(file: File): List<String> {
        return try {
            ZipFile(file).use { zip ->
                // Try both locations: root and assets/
                val manifestEntry = zip.getEntry("plugin-manifest.json")
                    ?: zip.getEntry("assets/plugin-manifest.json")
                
                if (manifestEntry == null) {
                    Timber.d("No plugin-manifest.json found in: ${file.name}")
                    return emptyList()
                }
                
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val json = JSONObject(manifestJson)
                
                // Extract permissions array
                val permissionsArray = json.optJSONArray("permissions")
                val permissions = mutableListOf<String>()
                
                if (permissionsArray != null) {
                    for (i in 0 until permissionsArray.length()) {
                        permissions.add(permissionsArray.getString(i))
                    }
                }
                
                if (permissions.isNotEmpty()) {
                    Timber.d("Extracted ${permissions.size} permissions from plugin-manifest.json: ${file.name}")
                }
                
                permissions
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract permissions from plugin-manifest.json: ${file.name}")
            emptyList()
        }
    }
    
    /**
     * Gets all permissions that the host app has
     */
    private fun getHostAppPermissions(): Set<String> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            
            // Get all permissions that are granted
            val grantedPermissions = mutableSetOf<String>()
            packageInfo.requestedPermissions?.forEachIndexed { index, permission ->
                val flags = packageInfo.requestedPermissionsFlags?.getOrNull(index) ?: 0
                if ((flags and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    grantedPermissions.add(permission)
                }
            }
            
            Timber.d("Host app has ${grantedPermissions.size} granted permissions")
            grantedPermissions
        } catch (e: Exception) {
            Timber.e(e, "Failed to get host app permissions")
            emptySet()
        }
    }
}

/**
 * Information about plugin permissions
 */
data class PluginPermissionInfo(
    val allPermissions: List<String>,
    val available: List<String>,
    val missing: List<String>,
    val dangerous: List<String>,
    val critical: List<String>
) {
    /**
     * Returns true if plugin has any dangerous permissions
     */
    fun hasDangerousPermissions(): Boolean = dangerous.isNotEmpty()
    
    /**
     * Returns true if plugin has any critical permissions
     */
    fun hasCriticalPermissions(): Boolean = critical.isNotEmpty()
    
    /**
     * Returns true if all requested permissions are available in host app
     */
    fun allPermissionsAvailable(): Boolean = missing.isEmpty()
}
