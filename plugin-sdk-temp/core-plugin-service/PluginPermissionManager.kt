package com.ble1st.connectias.core.plugin

import android.content.Context
import android.content.SharedPreferences
import com.ble1st.connectias.plugin.PluginMetadata
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PluginPermissionManager(
    private val context: Context
) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("plugin_permissions", Context.MODE_PRIVATE)
    }
    
    companion object {
        private val DANGEROUS_PERMISSIONS = setOf(
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
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR"
        )
        
        private val CRITICAL_PERMISSIONS = setOf(
            "android.permission.INSTALL_PACKAGES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.CHANGE_CONFIGURATION",
            "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
            "android.permission.REBOOT"
        )
    }
    
    suspend fun validatePermissions(metadata: PluginMetadata): Result<PermissionValidationResult> = 
        withContext(Dispatchers.Default) {
            try {
                val dangerous = getDangerousPermissions(metadata.permissions)
                val critical = getCriticalPermissions(metadata.permissions)
                
                if (critical.isNotEmpty()) {
                    Timber.e("Plugin ${metadata.pluginId} requests CRITICAL permissions: $critical")
                    return@withContext Result.success(
                        PermissionValidationResult(
                            isValid = false,
                            dangerousPermissions = dangerous,
                            criticalPermissions = critical,
                            requiresUserConsent = true,
                            reason = "Critical permissions are not allowed"
                        )
                    )
                }
                
                if (dangerous.isNotEmpty()) {
                    val hasConsent = hasUserConsent(metadata.pluginId, dangerous)
                    Timber.w("Plugin ${metadata.pluginId} requests dangerous permissions: $dangerous (consent: $hasConsent)")
                    
                    return@withContext Result.success(
                        PermissionValidationResult(
                            isValid = hasConsent,
                            dangerousPermissions = dangerous,
                            criticalPermissions = emptyList(),
                            requiresUserConsent = !hasConsent,
                            reason = if (hasConsent) "User consent granted" else "User consent required"
                        )
                    )
                }
                
                Result.success(
                    PermissionValidationResult(
                        isValid = true,
                        dangerousPermissions = emptyList(),
                        criticalPermissions = emptyList(),
                        requiresUserConsent = false,
                        reason = "No dangerous permissions"
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to validate permissions for plugin: ${metadata.pluginId}")
                Result.failure(e)
            }
        }
    
    fun getDangerousPermissions(permissions: List<String>): List<String> {
        return permissions.filter { it in DANGEROUS_PERMISSIONS }
    }
    
    fun getCriticalPermissions(permissions: List<String>): List<String> {
        return permissions.filter { it in CRITICAL_PERMISSIONS }
    }
    
    fun grantUserConsent(pluginId: String, permissions: List<String>) {
        prefs.edit().apply {
            permissions.forEach { permission ->
                putBoolean(getPermissionKey(pluginId, permission), true)
            }
            apply()
        }
        Timber.i("User consent granted for plugin $pluginId: $permissions")
    }
    
    fun revokeUserConsent(pluginId: String, permissions: List<String>? = null) {
        prefs.edit().apply {
            if (permissions == null) {
                val allKeys = prefs.all.keys.filter { it.startsWith("$pluginId:") }
                allKeys.forEach { remove(it) }
            } else {
                permissions.forEach { permission ->
                    remove(getPermissionKey(pluginId, permission))
                }
            }
            apply()
        }
        Timber.i("User consent revoked for plugin $pluginId")
    }
    
    fun hasUserConsent(pluginId: String, permissions: List<String>): Boolean {
        return permissions.all { permission ->
            prefs.getBoolean(getPermissionKey(pluginId, permission), false)
        }
    }
    
    fun isPermissionAllowed(pluginId: String, permission: String): Boolean {
        if (permission in CRITICAL_PERMISSIONS) {
            return false
        }
        
        if (permission in DANGEROUS_PERMISSIONS) {
            return prefs.getBoolean(getPermissionKey(pluginId, permission), false)
        }
        
        return true
    }
    
    fun clearAllConsents() {
        prefs.edit().clear().apply()
        Timber.i("All plugin permission consents cleared")
    }
    
    private fun getPermissionKey(pluginId: String, permission: String): String {
        return "$pluginId:$permission"
    }
}

data class PermissionValidationResult(
    val isValid: Boolean,
    val dangerousPermissions: List<String>,
    val criticalPermissions: List<String>,
    val requiresUserConsent: Boolean,
    val reason: String
)
