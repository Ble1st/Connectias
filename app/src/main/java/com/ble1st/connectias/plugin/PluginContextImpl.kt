package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.plugin.sdk.PluginContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Plugin context implementation providing access to app resources
 * Uses SecureContextWrapper to enforce permission checks
 */
class PluginContextImpl(
    private val appContext: Context,
    private val pluginId: String,
    private val pluginDataDir: File,
    private val nativeLibraryManager: NativeLibraryManager,
    private val permissionManager: PluginPermissionManager
) : PluginContext {
    
    private val services = ConcurrentHashMap<String, Any>()
    
    // Lazy-initialized secure context wrapper
    private val secureContext: Context by lazy {
        SecureContextWrapper(appContext, pluginId, permissionManager)
    }
    
    override fun getApplicationContext(): Context {
        // Return wrapped context instead of direct context
        return secureContext
    }
    
    override fun getPluginDirectory(): File {
        return pluginDataDir
    }
    
    override fun registerService(name: String, service: Any) {
        services[name] = service
        Timber.d("[$pluginId] Registered service: $name")
    }
    
    override fun getService(name: String): Any? {
        return services[name]
    }
    
    override fun logVerbose(message: String) {
        Timber.v("[$pluginId] $message")
    }
    
    override fun logDebug(message: String) {
        Timber.d("[$pluginId] $message")
    }
    
    override fun logInfo(message: String) {
        Timber.i("[$pluginId] $message")
    }
    
    override fun logWarning(message: String) {
        Timber.w("[$pluginId] $message")
    }
    
    override fun logError(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Timber.e(throwable, "[$pluginId] $message")
        } else {
            Timber.e("[$pluginId] $message")
        }
    }
    
    /**
     * Load native library for this plugin
     */
    fun loadNativeLibrary(libraryName: String): Result<Unit> {
        val libraryPath = File(pluginDataDir, "native/$libraryName").absolutePath
        return nativeLibraryManager.loadLibrary(libraryPath)
    }
}
