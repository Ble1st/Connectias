package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.plugin.sdk.PluginContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Plugin context implementation providing access to app resources
 */
class PluginContextImpl(
    private val appContext: Context,
    private val pluginId: String,
    private val pluginDataDir: File,
    private val nativeLibraryManager: NativeLibraryManager
) : PluginContext {
    
    private val services = ConcurrentHashMap<String, Any>()
    
    override fun getApplicationContext(): Context {
        return appContext
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
    
    override fun logDebug(message: String) {
        Timber.d("[$pluginId] $message")
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
