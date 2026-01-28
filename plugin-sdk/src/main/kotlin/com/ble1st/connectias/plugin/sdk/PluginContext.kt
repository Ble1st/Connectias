package com.ble1st.connectias.plugin.sdk

import android.content.Context
import com.ble1st.connectias.plugin.messaging.MessageResponse
import com.ble1st.connectias.plugin.messaging.PluginMessage
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Context provided to plugins for accessing app resources.
 */
interface PluginContext {

    /**
     * Get application context.
     */
    fun getApplicationContext(): Context

    /**
     * Get plugin-specific directory for data storage.
     */
    fun getPluginDirectory(): File

    /**
     * Register a service that can be accessed by other components.
     */
    fun registerService(name: String, service: Any)

    /**
     * Get a registered service.
     */
    fun getService(name: String): Any?

    /**
     * Log verbose message.
     */
    fun logVerbose(message: String)

    /**
     * Log debug message.
     */
    fun logDebug(message: String)

    /**
     * Log info message.
     */
    fun logInfo(message: String)

    /**
     * Log warning message.
     */
    fun logWarning(message: String)

    /**
     * Log error message.
     */
    fun logError(message: String, throwable: Throwable? = null)

    /**
     * Returns a UI controller for the Three-Process UI Architecture, if available.
     *
     * In the sandbox process, this controller can be used to push UI state updates
     * to the UI process.
     *
     * Default: null (no UI controller available).
     */
    fun getUIController(): PluginUIController? = null

    // ========================================
    // Hardware Bridge APIs (v2.0)
    // ========================================

    /**
     * Capture an image using the device camera via Hardware Bridge.
     * Requires CAMERA permission to be granted by user.
     *
     * @return Result with image data as ByteArray on success, or error
     */
    suspend fun captureImage(): Result<ByteArray>

    /**
     * Start camera preview via Hardware Bridge.
     * Returns SharedMemory file descriptor for reading preview frames.
     *
     * Frame format: YUV_420_888 (640x480)
     * Buffer contains 2 frames for double buffering.
     *
     * Requires CAMERA permission.
     *
     * @return Result with preview metadata on success, or error
     */
    suspend fun startCameraPreview(): Result<CameraPreviewInfo>

    /**
     * Stop camera preview.
     *
     * @return Result with Unit on success, or error
     */
    suspend fun stopCameraPreview(): Result<Unit>

    /**
     * Perform HTTP request via Hardware Bridge.
     * Requires INTERNET permission to be granted by user.
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
     * Print a document via Hardware Bridge.
     * Requires appropriate permissions.
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
     * Get list of paired Bluetooth devices via Hardware Bridge.
     * Requires BLUETOOTH permissions.
     *
     * @return Result with list of device info on success, or error
     */
    suspend fun getBluetoothDevices(): Result<List<BluetoothDeviceInfo>>

    /**
     * Get direct access to Hardware Bridge for advanced use cases.
     * Most plugins should use the convenience methods above instead.
     *
     * @return Hardware Bridge interface, or null if not available
     */
    fun getHardwareBridge(): Any? // Returns IHardwareBridge, using Any to avoid SDK dependency

    // ========================================
    // Plugin Messaging APIs (v2.0)
    // ========================================

    /**
     * Send a message to another plugin and wait for response.
     *
     * @param receiverId Target plugin ID
     * @param messageType Message type identifier (e.g., "DATA_REQUEST", "EVENT_NOTIFICATION")
     * @param payload Message payload as ByteArray
     * @return Response from receiver plugin, or error
     */
    suspend fun sendMessageToPlugin(
        receiverId: String,
        messageType: String,
        payload: ByteArray
    ): Result<MessageResponse>

    /**
     * Receive pending messages for this plugin.
     * Returns a Flow that emits messages as they arrive.
     *
     * @return Flow of incoming messages
     */
    suspend fun receiveMessages(): Flow<PluginMessage>

    /**
     * Register a message handler for specific message types.
     * When a message of the specified type is received, the handler is called
     * and its response is sent back to the sender.
     *
     * @param messageType Message type to handle
     * @param handler Handler function that processes message and returns response
     */
    fun registerMessageHandler(
        messageType: String,
        handler: suspend (PluginMessage) -> MessageResponse
    )

    // ========================================
    // File System SAF APIs (v2.1)
    // ========================================

    /**
     * Create a file via Storage Access Framework (SAF)
     * Opens Android file picker for user to select save location
     * 
     * Requires FILE_WRITE permission to be granted by user.
     *
     * @param fileName Suggested file name (e.g., "test.txt")
     * @param mimeType MIME type (e.g., "text/plain")
     * @param content File content as ByteArray
     * @return Result with Uri of created file on success, or error
     */
    suspend fun createFileViaSAF(
        fileName: String,
        mimeType: String,
        content: ByteArray
    ): Result<android.net.Uri>
    
    /**
     * Open a file via Storage Access Framework (SAF)
     * Opens Android file picker for user to select a file to read
     * 
     * Requires FILE_READ permission to be granted by user.
     *
     * @param mimeType MIME type filter (e.g., "text/plain" or "all files")
     * @return Result with Triple<Uri, ByteArray, String> (file URI, content, and display name) on success, or error
     */
    suspend fun openFileViaSAF(
        mimeType: String = "*/*"
    ): Result<Triple<android.net.Uri, ByteArray, String>>
}

/**
 * Camera preview information.
 */
data class CameraPreviewInfo(
    val fileDescriptor: Int,
    val width: Int,
    val height: Int,
    val format: String,
    val frameSize: Int,
    val bufferSize: Int
)

/**
 * Bluetooth device information.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val bondState: Int
)

