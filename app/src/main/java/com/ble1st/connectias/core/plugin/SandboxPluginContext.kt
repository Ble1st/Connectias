package com.ble1st.connectias.core.plugin

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.sdk.BluetoothDeviceInfo
import com.ble1st.connectias.plugin.sdk.CameraPreviewInfo
import com.ble1st.connectias.plugin.NativeLibraryManager
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.SecureContextWrapper
import com.ble1st.connectias.hardware.IHardwareBridge
import com.ble1st.connectias.plugin.IFileSystemBridge
import com.ble1st.connectias.plugin.security.SecureHardwareBridgeWrapper
import com.ble1st.connectias.plugin.security.SecureFileSystemBridgeWrapper
import com.ble1st.connectias.plugin.messaging.IPluginMessaging
import com.ble1st.connectias.plugin.messaging.PluginMessage
import com.ble1st.connectias.plugin.messaging.MessageResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Minimal PluginContext implementation for sandbox process
 * Limited functionality to prevent sandbox escape
 * Uses SecureContextWrapper to enforce permission checks
 */
class SandboxPluginContext(
    private val appContext: Context,
    private val pluginDir: File?, // Nullable for isolated process
    private val pluginId: String,
    private val sessionToken: Long,
    private val permissionManager: PluginPermissionManager,
    private val hardwareBridge: IHardwareBridge? = null, // Now accepts secure wrappers
    private val fileSystemBridge: IFileSystemBridge? = null, // Now accepts secure wrappers
    private val messagingBridge: IPluginMessaging? = null, // Messaging bridge for inter-plugin communication (via AIDL)
    private val uiController: com.ble1st.connectias.plugin.ui.IPluginUIController? = null // Sandbox UI controller (Three-Process UI)
) : PluginContext {
    
    private val serviceRegistry = mutableMapOf<String, Any>()
    private val nativeLibManager = pluginDir?.let { NativeLibraryManager(it) }
    
    // Message handlers registered by plugin
    private val messageHandlers = ConcurrentHashMap<String, suspend (PluginMessage) -> MessageResponse>()
    
    // Background coroutine scope for message processing
    private val messageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Lazy-initialized secure context wrapper
    private val secureContext: Context by lazy {
        SecureContextWrapper(appContext, pluginId, permissionManager)
    }
    
    init {
        pluginDir?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }
    
    override fun getApplicationContext(): Context {
        // Return wrapped context instead of direct context
        return secureContext
    }
    
    override fun getPluginDirectory(): File {
    return pluginDir ?: throw UnsupportedOperationException("Plugin directory not available in isolated process")
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
            Timber.tag("[SANDBOX:$pluginId]").e(throwable, message)
        } else {
            Timber.tag("[SANDBOX:$pluginId]").e(message)
        }
    }

    override fun getUIController(): com.ble1st.connectias.plugin.sdk.PluginUIController? {
        val controller = uiController ?: return null
        return object : com.ble1st.connectias.plugin.sdk.PluginUIController {
            override fun updateUIState(state: com.ble1st.connectias.plugin.ui.UIStateParcel) {
                controller.updateUIState(pluginId, state)
            }

            override fun showDialog(title: String, message: String, dialogType: Int) {
                controller.showDialog(pluginId, title, message, dialogType)
            }

            override fun showToast(message: String, duration: Int) {
                controller.showToast(pluginId, message, duration)
            }

            override fun navigateToScreen(screenId: String, args: android.os.Bundle) {
                controller.navigateToScreen(pluginId, screenId, args)
            }

            override fun navigateBack() {
                controller.navigateBack(pluginId)
            }

            override fun setLoading(loading: Boolean, message: String) {
                controller.setLoading(pluginId, loading, message)
            }

            override fun sendUIEvent(event: com.ble1st.connectias.plugin.ui.UIEventParcel) {
                controller.sendUIEvent(pluginId, event)
            }
        }
    }
    
    // ========================================
    // Hardware Bridge APIs Implementation
    // ========================================
    
    override suspend fun startCameraPreview(): Result<CameraPreviewInfo> = suspendCancellableCoroutine { continuation ->
        try {
            if (hardwareBridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not available")))
                return@suspendCancellableCoroutine
            }
            
            val response = hardwareBridge.startCameraPreview(pluginId)
            if (response.success && response.fileDescriptor != null) {
                val metadata = response.metadata ?: emptyMap()
                val previewInfo = CameraPreviewInfo(
                    fileDescriptor = response.fileDescriptor.fd,
                    width = metadata["width"]?.toIntOrNull() ?: 640,
                    height = metadata["height"]?.toIntOrNull() ?: 480,
                    format = metadata["format"] ?: "YUV_420_888",
                    frameSize = metadata["frameSize"]?.toIntOrNull() ?: 460800,
                    bufferSize = metadata["bufferSize"]?.toIntOrNull() ?: 921600
                )
                continuation.resume(Result.success(previewInfo))
            } else {
                continuation.resume(Result.failure(Exception(response.errorMessage ?: "Preview failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] startCameraPreview failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun stopCameraPreview(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            if (hardwareBridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not available")))
                return@suspendCancellableCoroutine
            }
            
            hardwareBridge.stopCameraPreview(pluginId)
            continuation.resume(Result.success(Unit))
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] stopCameraPreview failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun captureImage(): Result<ByteArray> = suspendCancellableCoroutine { continuation ->
        try {
            if (hardwareBridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not available")))
                return@suspendCancellableCoroutine
            }
            
            val response = hardwareBridge.captureImage(pluginId)
            if (response.success) {
                val bytes = when {
                    response.fileDescriptor != null -> {
                        // Read image data from file descriptor
                        try {
                            ParcelFileDescriptor.AutoCloseInputStream(response.fileDescriptor).use { 
                                it.readBytes() 
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "[SANDBOX:$pluginId] Failed to read image from file descriptor")
                            byteArrayOf()
                        }
                    }
                    response.data != null -> response.data
                    else -> {
                        Timber.w("[SANDBOX:$pluginId] captureImage returned success but no data or fileDescriptor")
                        byteArrayOf()
                    }
                }
                continuation.resume(Result.success(bytes))
            } else {
                continuation.resume(Result.failure(Exception(response.errorMessage ?: "Camera capture failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] captureImage failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun httpRequest(
        url: String,
        method: String,
        headers: Map<String, String>?,
        body: String?
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            if (hardwareBridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not available")))
                return@suspendCancellableCoroutine
            }
            
            // Only GET is fully supported for now
            if (method.uppercase() != "GET") {
                continuation.resume(Result.failure(UnsupportedOperationException("Only GET is supported currently")))
                return@suspendCancellableCoroutine
            }
            
            val response = hardwareBridge.httpGet(pluginId, url)
            
            if (response.success) {
                val responseBody = String(response.data ?: byteArrayOf())
                continuation.resume(Result.success(responseBody))
            } else {
                continuation.resume(Result.failure(Exception(response.errorMessage ?: "HTTP request failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] httpRequest failed")
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Host-only helper for declarative runtime: HTTP GET with metadata.
     *
     * This bypasses the simplified SDK method shape (String-only) and keeps
     * status/contentType available for low-code nodes.
     */
    internal fun httpGetWithInfo(url: String): Result<HttpResult> {
        // Default cap to keep low-code surface safe (avoid massive strings in state).
        return httpGetWithInfo(url = url, maxBytes = 512_000)
    }

    /**
     * Host-only helper for declarative runtime: HTTP GET with metadata and size cap.
     *
     * SECURITY:
     * - Applies a maxBytes cap before converting to String to reduce memory risk.
     */
    internal fun httpGetWithInfo(url: String, maxBytes: Int): Result<HttpResult> {
        return try {
            val bridge = hardwareBridge
                ?: return Result.failure(IllegalStateException("Hardware Bridge not available"))

            val response = bridge.httpGet(pluginId, url)
            if (!response.success) {
                return Result.failure(Exception(response.errorMessage ?: "HTTP GET failed"))
            }

            val meta = response.metadata ?: emptyMap()
            val status = meta["status"]?.toIntOrNull() ?: 200
            val contentType = meta["contentType"]
            val bytes = response.data ?: byteArrayOf()
            val cap = maxBytes.coerceIn(1_024, 5_000_000)
            val limited = if (bytes.size > cap) bytes.copyOf(cap) else bytes
            val bodyString = String(limited)

            Result.success(HttpResult(statusCode = status, contentType = contentType, body = bodyString))
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] httpGetWithInfo failed")
            Result.failure(e)
        }
    }

    /**
     * Host-only helper for declarative runtime: TCP ping (connect latency).
     */
    internal fun tcpPing(host: String, port: Int, timeoutMs: Int): Result<Long> {
        return try {
            val bridge = hardwareBridge
                ?: return Result.failure(IllegalStateException("Hardware Bridge not available"))

            val response = bridge.tcpPing(pluginId, host, port, timeoutMs)
            if (!response.success) {
                return Result.failure(Exception(response.errorMessage ?: "TCP ping failed"))
            }

            val meta = response.metadata ?: emptyMap()
            val latencyMs = meta["latencyMs"]?.toLongOrNull()
                ?: return Result.failure(Exception("Missing latencyMs"))

            Result.success(latencyMs)
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] tcpPing failed")
            Result.failure(e)
        }
    }

    internal data class HttpResult(
        val statusCode: Int,
        val contentType: String?,
        val body: String,
    )
    
    override suspend fun printDocument(
        data: ByteArray,
        mimeType: String,
        printerName: String?
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            if (hardwareBridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not available")))
                return@suspendCancellableCoroutine
            }
            
            // Create temp file for document data
            val tempFile = java.io.File.createTempFile("print_", ".pdf", appContext.cacheDir)
            tempFile.writeBytes(data)
            
            // Create ParcelFileDescriptor
            val documentFd = android.os.ParcelFileDescriptor.open(
                tempFile,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            
            val printerId = printerName ?: "default"
            val response = hardwareBridge.printDocument(pluginId, printerId, documentFd)
            
            // Cleanup
            tempFile.delete()
            
            if (response.success) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(Exception(response.errorMessage ?: "Print failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] printDocument failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun getBluetoothDevices(): Result<List<BluetoothDeviceInfo>> = suspendCancellableCoroutine { continuation ->
        try {
            if (hardwareBridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not available")))
                return@suspendCancellableCoroutine
            }
            
            // AIDL signature: getPairedBluetoothDevices returns List<String>
            val addresses = hardwareBridge.getPairedBluetoothDevices(pluginId)
            val devices = addresses.map { address ->
                BluetoothDeviceInfo(
                    name = address, // Name not available from this API
                    address = address,
                    bondState = 12 // BOND_BONDED
                )
            }
            continuation.resume(Result.success(devices))
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] getBluetoothDevices failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override fun getHardwareBridge(): Any? {
        // SECURITY: Raw bridge access removed to prevent pluginId spoofing
        // Plugins must use the provided bridge API methods instead
        Timber.w("[SANDBOX:$pluginId] getHardwareBridge() called - raw bridge access denied for security")
        return null
    }
    
    // File System Methods - Uses FileSystemBridge for secure access

    /**
     * Host-runtime-only local filesystem access.
     *
     * Declarative plugins are executed by host code (no arbitrary Kotlin/Java from third parties),
     * so we can safely persist host-managed runtime state in the sandbox process without IPC.
     *
     * This also avoids identity ambiguity when multiple plugins share the same isolated process UID.
     */
    internal fun openLocalFile(path: String): FileInputStream? {
        return try {
            // In an isolated process the app-private filesystem dir may be unavailable.
            // Only use local storage when pluginDir is explicitly provided.
            val root = pluginDir ?: return null
            val pluginRoot = File(root, "local/$pluginId")
            val file = File(pluginRoot, path)

            // Security check: Ensure file is within plugin directory
            if (!file.canonicalPath.startsWith(pluginRoot.canonicalPath)) {
                Timber.e("[SANDBOX:$pluginId] Path traversal attempt (local): $path")
                return null
            }

            if (!file.exists()) return null
            FileInputStream(file)
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to open local file: $path")
            null
        }
    }

    internal fun openLocalFileForWrite(path: String): FileOutputStream? {
        return try {
            // In an isolated process the app-private filesystem dir may be unavailable.
            // Only use local storage when pluginDir is explicitly provided.
            val root = pluginDir ?: return null
            val pluginRoot = File(root, "local/$pluginId")
            if (!pluginRoot.exists()) pluginRoot.mkdirs()

            val file = File(pluginRoot, path)

            // Security check: Ensure file is within plugin directory
            if (!file.canonicalPath.startsWith(pluginRoot.canonicalPath)) {
                Timber.e("[SANDBOX:$pluginId] Path traversal attempt (local): $path")
                return null
            }

            file.parentFile?.mkdirs()
            FileOutputStream(file)
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to open local file for write: $path")
            null
        }
    }
    
    fun createFile(path: String, mode: Int = 384): File? {
        return try {
            if (fileSystemBridge == null) {
                throw UnsupportedOperationException("File system bridge not available")
            }
            
            val pfd = fileSystemBridge.createFile(pluginId, sessionToken, path, mode)
            if (pfd != null) {
                // Convert ParcelFileDescriptor to File object
                val fd = pfd.fd
                val file = File("/proc/self/fd/$fd")
                pfd.close() // Close the descriptor as we have the file reference
                file
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to create file: $path")
            null
        }
    }
    
    fun openFile(path: String, mode: Int = ParcelFileDescriptor.MODE_READ_ONLY): FileInputStream? {
        return try {
            if (fileSystemBridge == null) {
                throw UnsupportedOperationException("File system bridge not available")
            }
            
            val pfd = fileSystemBridge.openFile(pluginId, sessionToken, path, mode)
            if (pfd != null) {
                FileInputStream(pfd.fileDescriptor)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to open file: $path")
            null
        }
    }
    
    fun openFileForWrite(
        path: String,
        mode: Int = ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE or
            ParcelFileDescriptor.MODE_WRITE_ONLY
    ): FileOutputStream? {
        return try {
            if (fileSystemBridge == null) {
                throw UnsupportedOperationException("File system bridge not available")
            }
            
            val pfd = fileSystemBridge.openFile(pluginId, sessionToken, path, mode)
            if (pfd != null) {
                FileOutputStream(pfd.fileDescriptor)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to open file for write: $path")
            null
        }
    }
    
    fun deleteFile(path: String): Boolean {
        return try {
            if (fileSystemBridge == null) {
                throw UnsupportedOperationException("File system bridge not available")
            }
            
            fileSystemBridge.deleteFile(pluginId, sessionToken, path)
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to delete file: $path")
            false
        }
    }
    
    fun fileExists(path: String): Boolean {
        return try {
            if (fileSystemBridge == null) {
                throw UnsupportedOperationException("File system bridge not available")
            }
            
            fileSystemBridge.fileExists(pluginId, sessionToken, path)
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to check file existence: $path")
            false
        }
    }
    
    fun listFiles(path: String = ""): Array<String> {
        return try {
            if (fileSystemBridge == null) {
                throw UnsupportedOperationException("File system bridge not available")
            }
            
            fileSystemBridge.listFiles(pluginId, sessionToken, path)
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to list files: $path")
            emptyArray()
        }
    }
    
    fun getFileSize(path: String): Long {
        return try {
            if (fileSystemBridge == null) {
                throw UnsupportedOperationException("File system bridge not available")
            }
            
            fileSystemBridge.getFileSize(pluginId, sessionToken, path)
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX:$pluginId] Failed to get file size: $path")
            -1
        }
    }
    
    // ========================================
    // Plugin Messaging APIs Implementation
    // ========================================
    
    override suspend fun sendMessageToPlugin(
        receiverId: String,
        messageType: String,
        payload: ByteArray
    ): Result<MessageResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (messagingBridge == null) {
                    return@withContext Result.failure(IllegalStateException("Messaging bridge not available"))
                }

                val message = PluginMessage(
                    senderId = pluginId,
                    receiverId = receiverId,
                    messageType = messageType,
                    payload = payload,
                    requestId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis()
                )

                val response = messagingBridge.sendMessage(message)
                Result.success(response)
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX:$pluginId] Failed to send message to plugin: $receiverId")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun receiveMessages(): Flow<PluginMessage> {
        return flow {
            if (messagingBridge == null) {
                Timber.w("[SANDBOX:$pluginId] Messaging bridge not available, cannot receive messages")
                return@flow
            }

            while (currentCoroutineContext().isActive) {
                try {
                    val messages = withContext(Dispatchers.IO) {
                        messagingBridge.receiveMessages(pluginId)
                    }

                    messages?.forEach { message ->
                        emit(message)
                    }

                    // Poll interval: check for new messages every 100ms
                    delay(100)
                } catch (e: CancellationException) {
                    // Expected during plugin unload / scope cancellation.
                    Timber.d("[SANDBOX:$pluginId] receiveMessages flow cancelled")
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX:$pluginId] Error in receiveMessages flow")
                    delay(1000) // Wait longer on error
                }
            }
        }
    }
    
    override fun registerMessageHandler(
        messageType: String,
        handler: suspend (PluginMessage) -> MessageResponse
    ) {
        messageHandlers[messageType] = handler
        
        // Start background coroutine to process messages
        messageScope.launch {
            try {
                receiveMessages().collect { message ->
                    if (message.messageType == messageType) {
                        val registeredHandler = messageHandlers[messageType]
                        if (registeredHandler != null) {
                            try {
                                val response = registeredHandler(message)

                                // Send response back to sender
                                try {
                                    withContext(Dispatchers.IO) {
                                        messagingBridge?.sendResponse(response)
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "[SANDBOX:$pluginId] Failed to send response for message: ${message.requestId}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "[SANDBOX:$pluginId] Error in message handler for type: $messageType")
                                
                                // Send error response
                                val errorResponse = MessageResponse.error(
                                    message.requestId,
                                    "Handler error: ${e.message}"
                                )
                                try {
                                    withContext(Dispatchers.IO) {
                                        messagingBridge?.sendResponse(errorResponse)
                                    }
                                } catch (ex: Exception) {
                                    Timber.e(ex, "[SANDBOX:$pluginId] Failed to send error response")
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Expected during plugin unload / scope cancellation.
                Timber.d("[SANDBOX:$pluginId] Message handler coroutine cancelled")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX:$pluginId] Error in message handler coroutine")
            }
        }
        
        Timber.d("[SANDBOX:$pluginId] Registered message handler for type: $messageType")
    }
    
    /**
     * Cleanup messaging resources
     */
    fun cleanup() {
        messageScope.cancel()
        messageHandlers.clear()
    }
}
