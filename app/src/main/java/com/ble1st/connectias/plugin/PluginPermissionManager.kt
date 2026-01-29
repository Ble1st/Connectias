package com.ble1st.connectias.plugin

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages plugin permissions with user consent and runtime enforcement
 */
class PluginPermissionManager(
    private val context: Context
) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("plugin_permissions", Context.MODE_PRIVATE)
    }
    
    companion object {
        // Dangerous permissions requiring user consent
        private val DANGEROUS_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED", // Android 14+ Selected Photos Access
            "android.permission.ACCESS_MEDIA_LOCATION",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.UWB_RANGING"
        )
        
        // Critical permissions that are always blocked
        private val CRITICAL_PERMISSIONS = setOf(
            "android.permission.INSTALL_PACKAGES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.CHANGE_CONFIGURATION",
            "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
            "android.permission.REBOOT",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.SYSTEM_ALERT_WINDOW"
        )
        
        // Hardware permission categories for isolated process bridge
        enum class HardwareCategory {
            CAMERA,
            NETWORK,
            PRINTER,
            BLUETOOTH,
            STORAGE,
            LOCATION,
            AUDIO,
            SENSORS
        }
        
        // Map permissions to hardware categories
        internal val HARDWARE_PERMISSION_MAP = mapOf(
            "android.permission.CAMERA" to HardwareCategory.CAMERA,
            "android.permission.INTERNET" to HardwareCategory.NETWORK,
            "android.permission.ACCESS_NETWORK_STATE" to HardwareCategory.NETWORK,
            "android.permission.BLUETOOTH_CONNECT" to HardwareCategory.BLUETOOTH,
            "android.permission.BLUETOOTH_SCAN" to HardwareCategory.BLUETOOTH,
            "android.permission.BLUETOOTH_ADVERTISE" to HardwareCategory.BLUETOOTH,
            "android.permission.WRITE_EXTERNAL_STORAGE" to HardwareCategory.STORAGE,
            "android.permission.READ_EXTERNAL_STORAGE" to HardwareCategory.STORAGE,
            "android.permission.ACCESS_FINE_LOCATION" to HardwareCategory.LOCATION,
            "android.permission.ACCESS_COARSE_LOCATION" to HardwareCategory.LOCATION,
            "android.permission.RECORD_AUDIO" to HardwareCategory.AUDIO,
            "android.permission.BODY_SENSORS" to HardwareCategory.SENSORS
        )
    }
    
    /**
     * Validates permissions for a plugin
     * Checks if ALL permissions have user consent and if critical permissions are blocked
     * 
     * SECURITY POLICY: All permissions require explicit user consent
     */
    suspend fun validatePermissions(metadata: PluginMetadata): Result<PermissionValidationResult> = 
        withContext(Dispatchers.Default) {
            try {
                val dangerous = getDangerousPermissions(metadata.permissions)
                val critical = getCriticalPermissions(metadata.permissions)
                
                // Critical permissions are never allowed
                if (critical.isNotEmpty()) {
                    Timber.e("Plugin ${metadata.pluginId} requests CRITICAL permissions: $critical")
                    return@withContext Result.success(
                        PermissionValidationResult(
                            isValid = false,
                            dangerousPermissions = dangerous,
                            criticalPermissions = critical,
                            allRequestedPermissions = emptyList(),
                            requiresUserConsent = false,
                            reason = "Critical permissions are not allowed: ${critical.joinToString()}"
                        )
                    )
                }
                
                // Check if host app has the requested permissions
                // NOTE: Custom Permissions (plugin-specific) are excluded from this check
                // because they cannot be declared in the host app's manifest
                val unavailablePermissions = metadata.permissions.filter { permission ->
                    !isCustomPermission(metadata.pluginId, permission) && // Skip custom permissions
                    context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
                }
                
                if (unavailablePermissions.isNotEmpty()) {
                    Timber.w("Plugin ${metadata.pluginId} requests permissions not available in host app: $unavailablePermissions")
                }
                
                // Get all available permissions (excluding critical and unavailable)
                // Custom permissions are always considered "available" (they don't need host app declaration)
                val availablePermissions = metadata.permissions.filter { permission ->
                    permission !in critical && 
                    (permission !in unavailablePermissions || isCustomPermission(metadata.pluginId, permission))
                }
                
                // ALL permissions require user consent (security-first approach)
                if (availablePermissions.isNotEmpty()) {
                    val hasConsent = hasUserConsent(metadata.pluginId, availablePermissions)
                    Timber.d("Plugin ${metadata.pluginId} requests permissions: $availablePermissions (consent: $hasConsent)")
                    
                    return@withContext Result.success(
                        PermissionValidationResult(
                            isValid = hasConsent,
                            dangerousPermissions = dangerous,
                            criticalPermissions = emptyList(),
                            allRequestedPermissions = availablePermissions, // All permissions need consent
                            requiresUserConsent = !hasConsent,
                            reason = if (hasConsent) "User consent granted for all permissions" 
                                    else "User consent required for: ${availablePermissions.joinToString()}"
                        )
                    )
                }
                
                // No permissions requested
                Result.success(
                    PermissionValidationResult(
                        isValid = true,
                        dangerousPermissions = emptyList(),
                        criticalPermissions = emptyList(),
                        allRequestedPermissions = emptyList(),
                        requiresUserConsent = false,
                        reason = "No permissions requested"
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to validate permissions for plugin: ${metadata.pluginId}")
                Result.failure(e)
            }
        }
    
    /**
     * Filters dangerous permissions from a list
     */
    fun getDangerousPermissions(permissions: List<String>): List<String> {
        return permissions.filter { it in DANGEROUS_PERMISSIONS }
    }
    
    /**
     * Filters critical permissions from a list
     */
    fun getCriticalPermissions(permissions: List<String>): List<String> {
        return permissions.filter { it in CRITICAL_PERMISSIONS }
    }
    
    /**
     * Grants user consent for specific permissions
     * This method should be called from the MAIN PROCESS only
     * It will broadcast the change to sandbox processes
     */
    fun grantUserConsent(pluginId: String, permissions: List<String>) {
        prefs.edit().apply {
            permissions.forEach { permission ->
                putBoolean(getPermissionKey(pluginId, permission), true)
            }
            apply()
        }
        Timber.i("User consent granted for plugin $pluginId: $permissions")
        
        // Broadcast permission changes to sandbox processes
        PluginPermissionBroadcast.sendPermissionsChanged(context, pluginId, permissions, granted = true)
    }
    
    /**
     * Revokes user consent for permissions
     * If permissions is null, revokes all permissions for the plugin
     * This method should be called from the MAIN PROCESS only
     * It will broadcast the change to sandbox processes
     */
    fun revokeUserConsent(pluginId: String, permissions: List<String>? = null) {
        val revokedPermissions = if (permissions == null) {
            // Get all permissions for this plugin before removing them
            prefs.all.keys.filter { it.startsWith("$pluginId:") }
                .map { it.substringAfter(":") }
        } else {
            permissions
        }
        
        prefs.edit().apply {
            if (permissions == null) {
                // Remove all permissions for this plugin
                val allKeys = prefs.all.keys.filter { it.startsWith("$pluginId:") }
                allKeys.forEach { remove(it) }
            } else {
                permissions.forEach { permission ->
                    remove(getPermissionKey(pluginId, permission))
                }
            }
            apply()
        }
        Timber.i("User consent revoked for plugin $pluginId" + 
            if (permissions != null) ": $permissions" else " (all permissions)")
        
        // Broadcast permission changes to sandbox processes
        PluginPermissionBroadcast.sendPermissionsChanged(context, pluginId, revokedPermissions, granted = false)
    }
    
    /**
     * Grant permission for a plugin (called from SANDBOX PROCESS when receiving broadcast)
     * This does NOT send broadcasts (to avoid loops)
     */
    fun grantPermissionForPlugin(pluginId: String, permissions: List<String>) {
        prefs.edit().apply {
            permissions.forEach { permission ->
                putBoolean(getPermissionKey(pluginId, permission), true)
            }
            apply()
        }
        Timber.i("[SANDBOX] Permission granted for plugin $pluginId: $permissions")
    }
    
    /**
     * Revoke permission for a plugin (called from SANDBOX PROCESS when receiving broadcast)
     * This does NOT send broadcasts (to avoid loops)
     */
    fun revokePermissionForPlugin(pluginId: String, permissions: List<String>) {
        prefs.edit().apply {
            permissions.forEach { permission ->
                remove(getPermissionKey(pluginId, permission))
            }
            apply()
        }
        Timber.i("[SANDBOX] Permission revoked for plugin $pluginId: $permissions")
    }
    
    /**
     * Checks if user has granted consent for all specified permissions
     */
    fun hasUserConsent(pluginId: String, permissions: List<String>): Boolean {
        return permissions.all { permission ->
            prefs.getBoolean(getPermissionKey(pluginId, permission), false)
        }
    }
    
    /**
     * Checks if user has granted consent for a single permission
     * This ONLY checks user consent, not host app permissions
     */
    fun hasUserConsentForPermission(pluginId: String, permission: String): Boolean {
        return prefs.getBoolean(getPermissionKey(pluginId, permission), false)
    }
    
    /**
     * Checks if a specific permission is allowed for a plugin at runtime
     * This is used by SecureContextWrapper
     * 
     * SECURITY POLICY: All permissions are BLOCKED by default until explicitly granted by user
     * 
     * NOTE: Custom Permissions (plugin-specific permissions) are handled differently:
     * - They cannot be declared in the host app's manifest
     * - They only require user consent (not host app permission check)
     */
    fun isPermissionAllowed(pluginId: String, permission: String): Boolean {
        // Critical permissions are never allowed
        if (permission in CRITICAL_PERMISSIONS) {
            Timber.w("Plugin $pluginId attempted to use critical permission: $permission")
            return false
        }
        
        // Check if this is a custom (plugin-specific) permission
        val isCustomPermission = isCustomPermission(pluginId, permission)
        
        if (!isCustomPermission) {
            // For standard Android permissions, check if host app has this permission
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Timber.w("Plugin $pluginId requested permission not available in host app: $permission")
                return false
            }
        } else {
            // Custom permissions don't need to be in host app manifest
            Timber.d("Plugin $pluginId requested custom permission: $permission (no host app check needed)")
        }
        
        // ALL permissions (including normal ones and custom ones) require explicit user consent
        // This is a security-first approach: deny by default, allow only when explicitly granted
        val hasConsent = prefs.getBoolean(getPermissionKey(pluginId, permission), false)
        if (!hasConsent) {
            val permissionType = when {
                isCustomPermission -> "custom"
                permission in DANGEROUS_PERMISSIONS -> "dangerous"
                else -> "normal"
            }
            Timber.w("Plugin $pluginId attempted to use $permissionType permission without user consent: $permission")
        }
        return hasConsent
    }
    
    /**
     * Check if a permission is a custom (plugin-specific) permission
     * Custom permissions typically start with the plugin's package name
     * and cannot be declared in the host app's manifest
     */
    private fun isCustomPermission(pluginId: String, permission: String): Boolean {
        // Custom permissions are those that start with the plugin's package name
        // or contain the plugin ID in their name
        // Examples:
        // - com.ble1st.connectias.networkinfoplugin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
        // - com.ble1st.connectias.testplugin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
        return permission.contains(pluginId) || 
               (permission.startsWith("com.ble1st.connectias.") && 
                !permission.startsWith("android.permission."))
    }
    
    /**
     * Clears all permission consents (for testing or reset)
     */
    fun clearAllConsents() {
        prefs.edit().clear().apply()
        Timber.i("All plugin permission consents cleared")
    }
    
    private fun getPermissionKey(pluginId: String, permission: String): String {
        return "$pluginId:$permission"
    }
    
    // ════════════════════════════════════════════════════════
    // HARDWARE PERMISSION HELPERS (for isolated process bridge)
    // ════════════════════════════════════════════════════════
    
    /**
     * Get hardware category for a permission
     * Used by Hardware Bridge to determine which hardware to access
     */
    fun getHardwareCategory(permission: String): HardwareCategory? {
        return HARDWARE_PERMISSION_MAP[permission]
    }
    
    /**
     * Get all hardware categories required by permissions
     * Useful for showing user what hardware a plugin will access
     */
    fun getHardwareCategories(permissions: List<String>): Set<HardwareCategory> {
        return permissions.mapNotNull { getHardwareCategory(it) }.toSet()
    }
    
    /**
     * Check if plugin has permission for hardware category
     * Used by Hardware Bridge before granting access
     */
    fun hasHardwareAccess(pluginId: String, category: HardwareCategory): Boolean {
        // Find all permissions for this category
        val categoryPermissions = HARDWARE_PERMISSION_MAP
            .filter { it.value == category }
            .keys
        
        // Check if plugin has at least one permission for this category
        return categoryPermissions.any { permission ->
            isPermissionAllowed(pluginId, permission)
        }
    }
    
    /**
     * Log hardware access for audit trail
     * Stores timestamp and category for security monitoring
     */
    fun logHardwareAccess(pluginId: String, category: HardwareCategory) {
        val timestamp = System.currentTimeMillis()
        val key = "hardware_log:$pluginId:${category.name}:$timestamp"
        prefs.edit().putBoolean(key, true).apply()
        
        Timber.d("[HARDWARE ACCESS] Plugin $pluginId accessed ${category.name}")
        
        // Keep only last 100 logs per plugin to avoid unbounded growth
        cleanupOldLogs(pluginId)
    }
    
    /**
     * Get hardware access log for plugin
     * Returns list of (category, timestamp) pairs
     */
    fun getHardwareAccessLog(pluginId: String): List<Pair<HardwareCategory, Long>> {
        val prefix = "hardware_log:$pluginId:"
        return prefs.all.keys
            .filter { it.startsWith(prefix) }
            .mapNotNull { key ->
                try {
                    val parts = key.removePrefix(prefix).split(":")
                    val category = HardwareCategory.valueOf(parts[0])
                    val timestamp = parts[1].toLong()
                    category to timestamp
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.second }
    }
    
    /**
     * Cleanup old hardware access logs
     * Keeps only last 100 entries per plugin
     */
    private fun cleanupOldLogs(pluginId: String) {
        val logs = getHardwareAccessLog(pluginId)
        if (logs.size > 100) {
            val toDelete = logs.drop(100)
            prefs.edit().apply {
                toDelete.forEach { (category, timestamp) ->
                    remove("hardware_log:$pluginId:${category.name}:$timestamp")
                }
                apply()
            }
        }
    }
    
    /**
     * Get human-readable description of hardware category
     */
    fun getHardwareCategoryDescription(category: HardwareCategory): String {
        return when (category) {
            HardwareCategory.CAMERA -> "Kamera (Fotos und Videos aufnehmen)"
            HardwareCategory.NETWORK -> "Netzwerk (Internet und lokale Verbindungen)"
            HardwareCategory.PRINTER -> "Drucker (Dokumente drucken)"
            HardwareCategory.BLUETOOTH -> "Bluetooth (Geräteverbindungen)"
            HardwareCategory.STORAGE -> "Speicher (Dateien lesen und schreiben)"
            HardwareCategory.LOCATION -> "Standort (GPS und Netzwerk-Standort)"
            HardwareCategory.AUDIO -> "Audio (Mikrofon und Audioaufnahme)"
            HardwareCategory.SENSORS -> "Sensoren (Bewegung, Temperatur, etc.)"
        }
    }
}

/**
 * Result of permission validation
 */
data class PermissionValidationResult(
    val isValid: Boolean,
    val dangerousPermissions: List<String>,
    val criticalPermissions: List<String>,
    val allRequestedPermissions: List<String>, // All permissions that need consent
    val requiresUserConsent: Boolean,
    val reason: String
)
