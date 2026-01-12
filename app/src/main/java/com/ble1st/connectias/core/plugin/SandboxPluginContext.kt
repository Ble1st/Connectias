package com.ble1st.connectias.core.plugin

import android.content.Context
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.NativeLibraryManager
import timber.log.Timber
import java.io.File

/**
 * Minimal PluginContext implementation for sandbox process
 * Limited functionality to prevent sandbox escape
 */
class SandboxPluginContext(
    private val appContext: Context,
    private val pluginDir: File,
    private val pluginId: String
) : PluginContext {
    
    private val serviceRegistry = mutableMapOf<String, Any>()
    private val nativeLibManager = NativeLibraryManager(pluginDir)
    
    init {
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
    }
    
    override fun getApplicationContext(): Context {
        return appContext
    }
    
    override fun getPluginDirectory(): File {
        return pluginDir
    }
    
    override fun registerService(name: String, service: Any) {
        serviceRegistry[name] = service
        logDebug("Service registered: $name")
    }
    
    override fun getService(name: String): Any? {
        return serviceRegistry[name]
    }
    
    override fun logVerbose(message: String) {
        Timber.v("[SANDBOX:$pluginId] $message")
    }
    
    override fun logDebug(message: String) {
        Timber.d("[SANDBOX:$pluginId] $message")
    }
    
    override fun logInfo(message: String) {
        Timber.i("[SANDBOX:$pluginId] $message")
    }
    
    override fun logWarning(message: String) {
        Timber.w("[SANDBOX:$pluginId] $message")
    }
    
    override fun logError(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Timber.e(throwable, "[SANDBOX:$pluginId] $message")
        } else {
            Timber.e("[SANDBOX:$pluginId] $message")
        }
    }
}
