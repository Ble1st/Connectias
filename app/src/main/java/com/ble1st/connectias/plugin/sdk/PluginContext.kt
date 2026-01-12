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
     * Log verbose message
     */
    fun logVerbose(message: String)
    
    /**
     * Log debug message
     */
    fun logDebug(message: String)
    
    /**
     * Log info message
     */
    fun logInfo(message: String)
    
    /**
     * Log warning message
     */
    fun logWarning(message: String)
    
    /**
     * Log error message
     */
    fun logError(message: String, throwable: Throwable? = null)
    
    // ========================================
    // Hardware Bridge APIs (v2.0)
    // ========================================
    
    /**
     * Capture an image using the device camera via Hardware Bridge
     * Requires CAMERA permission to be granted by user
     * 
     * @return Result with image data as ByteArray on success, or error
     */
    suspend fun captureImage(): Result<ByteArray>
    
    /**
     * Perform HTTP request via Hardware Bridge
     * Requires INTERNET permission to be granted by user
     * 
     * @param url Target URL
     * @param method HTTP method (GET, POST, etc.)
     * @param headers Optional HTTP headers
     * @param body Optional request body for POST/PUT
     * @return Result with response body as String on success, or error
     */
    suspend fun httpRequest(
        url: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        body: String? = null
    ): Result<String>
    
    /**
     * Print a document via Hardware Bridge
     * Requires appropriate permissions
     * 
     * @param data Document data
     * @param mimeType Document MIME type (e.g., "application/pdf", "image/png")
     * @param printerName Optional specific printer name
     * @return Result with Unit on success, or error
     */
    suspend fun printDocument(
        data: ByteArray,
        mimeType: String,
        printerName: String? = null
    ): Result<Unit>
    
    /**
     * Get list of paired Bluetooth devices via Hardware Bridge
     * Requires BLUETOOTH permissions
     * 
     * @return Result with list of device info on success, or error
     */
    suspend fun getBluetoothDevices(): Result<List<BluetoothDeviceInfo>>
    
    /**
     * Get direct access to Hardware Bridge for advanced use cases
     * Most plugins should use the convenience methods above instead
     * 
     * @return Hardware Bridge interface, or null if not available
     */
    fun getHardwareBridge(): Any? // Returns IHardwareBridge, using Any to avoid SDK dependency
}

/**
 * Bluetooth device information
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val bondState: Int
)
