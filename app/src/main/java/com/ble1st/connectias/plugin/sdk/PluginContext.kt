package com.ble1st.connectias.plugin.sdk

import android.content.Context
import java.io.File

/**
 * Context provided to plugins for accessing app resources
 */
interface PluginContext {
    
    /**
     * Get application context
     */
    fun getApplicationContext(): Context
    
    /**
     * Get plugin-specific directory for data storage
     */
    fun getPluginDirectory(): File
    
    /**
     * Register a service that can be accessed by other components
     */
    fun registerService(name: String, service: Any)
    
    /**
     * Get a registered service
     */
    fun getService(name: String): Any?
    
    /**
     * Log debug message
     */
    fun logDebug(message: String)
    
    /**
     * Log error message
     */
    fun logError(message: String, throwable: Throwable? = null)
}
