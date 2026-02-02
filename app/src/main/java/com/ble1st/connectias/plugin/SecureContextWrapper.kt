package com.ble1st.connectias.plugin

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import timber.log.Timber
import java.io.File

/**
 * Secure context wrapper that enforces permission checks for plugin access
 * Wraps the application context and intercepts sensitive operations
 * 
 * SECURITY NOTE: This wrapper prevents direct context leaks by wrapping all
 * context-returning methods (getBaseContext, getApplicationContext) to ensure
 * plugins cannot bypass permission checks.
 */
class SecureContextWrapper(
    baseContext: Context,
    private val pluginId: String,
    private val permissionManager: PluginPermissionManager
) : ContextWrapper(baseContext) {
    
    companion object {
        // Mapping of system services to required permissions
        private val SERVICE_PERMISSIONS = mapOf(
            CONNECTIVITY_SERVICE to Manifest.permission.INTERNET,
            WIFI_SERVICE to Manifest.permission.ACCESS_WIFI_STATE,
            LOCATION_SERVICE to Manifest.permission.ACCESS_FINE_LOCATION,
            TELEPHONY_SERVICE to Manifest.permission.READ_PHONE_STATE,
            CAMERA_SERVICE to Manifest.permission.CAMERA,
            AUDIO_SERVICE to Manifest.permission.RECORD_AUDIO,
            SENSOR_SERVICE to Manifest.permission.BODY_SENSORS,
            BLUETOOTH_SERVICE to Manifest.permission.BLUETOOTH,
            NFC_SERVICE to Manifest.permission.NFC
        )
    }
    
    // Keep a reference to wrapped self to prevent context leaks
    private val wrappedSelf: Context by lazy { this }
    
    /**
     * SECURITY: Override getBaseContext to prevent plugins from accessing unwrapped context
     * Plugins could call context.baseContext to bypass SecureContextWrapper
     */
    override fun getBaseContext(): Context {
        Timber.d("Plugin $pluginId attempted to access baseContext - returning wrapped context")
        return wrappedSelf
    }
    
    /**
     * SECURITY: Override getApplicationContext to prevent plugins from accessing unwrapped context
     * This is already overridden in PluginContextImpl, but we add it here as defense-in-depth
     */
    override fun getApplicationContext(): Context {
        Timber.d("Plugin $pluginId attempted to access applicationContext - returning wrapped context")
        return wrappedSelf
    }
    
    override fun getSystemService(name: String): Any? {
        // Check if this service requires a permission
        val requiredPermission = SERVICE_PERMISSIONS[name]
        
        if (requiredPermission != null) {
            // Verify permission before granting access
            if (!permissionManager.isPermissionAllowed(pluginId, requiredPermission)) {
                val serviceName = name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                Timber.w("Plugin $pluginId denied access to $name - missing permission: $requiredPermission")
                throw SecurityException(
                    "Plugin '$pluginId' is not allowed to access $serviceName. " +
                    "Missing permission: $requiredPermission"
                )
            }
            Timber.d("Plugin $pluginId granted access to $name")
        }
        
        return super.getSystemService(name)
    }
    
    override fun checkSelfPermission(permission: String): Int {
        // Delegate to permission manager for plugin-specific check
        return if (permissionManager.isPermissionAllowed(pluginId, permission)) {
            PackageManager.PERMISSION_GRANTED
        } else {
            Timber.d("Plugin $pluginId permission check failed: $permission")
            PackageManager.PERMISSION_DENIED
        }
    }
    
    override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
        // Use plugin-specific permission check
        return checkSelfPermission(permission)
    }
    
    override fun getExternalFilesDir(type: String?): File? {
        // Check storage permission for external files access
        if (!permissionManager.isPermissionAllowed(pluginId, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Timber.w("Plugin $pluginId attempted to access external files without permission")
            throw SecurityException(
                "Plugin '$pluginId' is not allowed to access external storage. " +
                "Missing permission: ${Manifest.permission.READ_EXTERNAL_STORAGE}"
            )
        }
        return super.getExternalFilesDir(type)
    }
    
    override fun getExternalCacheDir(): File? {
        // Check storage permission for external cache access
        if (!permissionManager.isPermissionAllowed(pluginId, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Timber.w("Plugin $pluginId attempted to access external cache without permission")
            throw SecurityException(
                "Plugin '$pluginId' is not allowed to access external storage. " +
                "Missing permission: ${Manifest.permission.READ_EXTERNAL_STORAGE}"
            )
        }
        return super.getExternalCacheDir()
    }
    
    override fun getExternalFilesDirs(type: String?): Array<File> {
        // Check storage permission for external files access
        if (!permissionManager.isPermissionAllowed(pluginId, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Timber.w("Plugin $pluginId attempted to access external files without permission")
            throw SecurityException(
                "Plugin '$pluginId' is not allowed to access external storage. " +
                "Missing permission: ${Manifest.permission.READ_EXTERNAL_STORAGE}"
            )
        }
        return super.getExternalFilesDirs(type)
    }
    
    override fun getExternalCacheDirs(): Array<File> {
        // Check storage permission for external cache access
        if (!permissionManager.isPermissionAllowed(pluginId, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Timber.w("Plugin $pluginId attempted to access external cache without permission")
            throw SecurityException(
                "Plugin '$pluginId' is not allowed to access external storage. " +
                "Missing permission: ${Manifest.permission.READ_EXTERNAL_STORAGE}"
            )
        }
        return super.getExternalCacheDirs()
    }
    
    override fun getObbDir(): File? {
        // Check storage permission for OBB access
        if (!permissionManager.isPermissionAllowed(pluginId, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Timber.w("Plugin $pluginId attempted to access OBB directory without permission")
            throw SecurityException(
                "Plugin '$pluginId' is not allowed to access external storage. " +
                "Missing permission: ${Manifest.permission.READ_EXTERNAL_STORAGE}"
            )
        }
        return super.getObbDir()
    }
    
    override fun getObbDirs(): Array<File> {
        // Check storage permission for OBB access
        if (!permissionManager.isPermissionAllowed(pluginId, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Timber.w("Plugin $pluginId attempted to access OBB directories without permission")
            throw SecurityException(
                "Plugin '$pluginId' is not allowed to access external storage. " +
                "Missing permission: ${Manifest.permission.READ_EXTERNAL_STORAGE}"
            )
        }
        return super.getObbDirs()
    }
}
