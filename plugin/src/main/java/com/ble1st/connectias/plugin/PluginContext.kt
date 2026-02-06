package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.plugin.native.INativeLibraryManager
import java.io.File

/**
 * Context provided to plugins during their lifecycle
 */
interface PluginContext {
    /**
     * Get the application context
     */
    fun getApplicationContext(): Context
    
    /**
     * Get the plugin's directory for storing files
     */
    fun getPluginDirectory(): File
    
    /**
     * Get the native library manager for loading .so files
     */
    fun getNativeLibraryManager(): INativeLibraryManager
    
    /**
     * Register a service that can be accessed by other plugins
     */
    fun registerService(name: String, service: Any)
    
    /**
     * Get a registered service by name
     */
    fun getService(name: String): Any?
    
    /**
     * Log a debug message
     */
    fun logDebug(message: String)
    
    /**
     * Log an error message
     */
    fun logError(message: String, throwable: Throwable? = null)
}
