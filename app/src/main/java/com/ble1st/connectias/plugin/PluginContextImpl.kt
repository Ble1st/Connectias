package com.ble1st.connectias.plugin

import android.content.Context
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.sdk.BluetoothDeviceInfo
import com.ble1st.connectias.plugin.sdk.CameraPreviewInfo
import com.ble1st.connectias.plugin.messaging.PluginMessagingProxy
import com.ble1st.connectias.plugin.messaging.PluginMessage
import com.ble1st.connectias.plugin.messaging.MessageResponse
import com.ble1st.connectias.hardware.IHardwareBridge
import com.ble1st.connectias.hardware.HardwareBridgeService
import com.ble1st.connectias.core.plugin.ui.IsolatedPluginContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Plugin context implementation providing access to app resources
 * Uses SecureContextWrapper to enforce permission checks
 */
open class PluginContextImpl(
    private val appContext: Context,
    private val pluginId: String,
    private val pluginDataDir: File,
    private val nativeLibraryManager: NativeLibraryManager,
    private val permissionManager: PluginPermissionManager,
    private val messagingProxy: PluginMessagingProxy? = null // Optional messaging proxy
) : PluginContext {
    
    // Hardware Bridge connection (lazy)
    private var hardwareBridge: IHardwareBridge? = null
    private var hardwareBridgeConnection: ServiceConnection? = null
    
    // Messaging
    private val messageHandlers = ConcurrentHashMap<String, suspend (PluginMessage) -> MessageResponse>()
    private val messagingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        // Connect to HardwareBridge service
        connectToHardwareBridge()
        
        // Connect to messaging service if proxy provided
        messagingProxy?.let { proxy ->
            messagingScope.launch {
                val connectResult = proxy.connect()
                if (connectResult.isSuccess) {
                    // Register plugin for messaging
                    proxy.registerPlugin(pluginId)
                    Timber.d("[$pluginId] Connected to messaging service")
                } else {
                    Timber.w(connectResult.exceptionOrNull(), "[$pluginId] Failed to connect to messaging service")
                }
            }
        }
    }
    
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
            Timber.tag("[PLUGIN:$pluginId]").e(throwable, message)
        } else {
            Timber.tag("[PLUGIN:$pluginId]").e(message)
        }
    }
    
    private fun connectToHardwareBridge() {
        try {
            val intent = android.content.Intent(appContext, HardwareBridgeService::class.java)
            hardwareBridgeConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    hardwareBridge = IHardwareBridge.Stub.asInterface(service)
                    Timber.d("[$pluginId] Connected to Hardware Bridge")
                }
                
                override fun onServiceDisconnected(name: ComponentName?) {
                    hardwareBridge = null
                    Timber.w("[$pluginId] Disconnected from Hardware Bridge")
                }
            }
            appContext.bindService(intent, hardwareBridgeConnection!!, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Timber.e(e, "[$pluginId] Failed to connect to Hardware Bridge")
        }
    }
    
    // ========================================
    // Hardware Bridge APIs (Main Process via IPC)
    // ========================================
    
    override suspend fun startCameraPreview(): Result<CameraPreviewInfo> = suspendCancellableCoroutine { continuation ->
        try {
            val bridge = hardwareBridge
            if (bridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not connected")))
                return@suspendCancellableCoroutine
            }
            
            val response = bridge.startCameraPreview(pluginId)
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
            Timber.e(e, "[$pluginId] startCameraPreview failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun stopCameraPreview(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            val bridge = hardwareBridge
            if (bridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not connected")))
                return@suspendCancellableCoroutine
            }
            
            bridge.stopCameraPreview(pluginId)
            continuation.resume(Result.success(Unit))
        } catch (e: Exception) {
            Timber.e(e, "[$pluginId] stopCameraPreview failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun captureImage(): Result<ByteArray> = suspendCancellableCoroutine { continuation ->
        try {
            val bridge = hardwareBridge
            if (bridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not connected")))
                return@suspendCancellableCoroutine
            }
            
            val response = bridge.captureImage(pluginId)
            if (response.success) {
                val bytes = when {
                    response.fileDescriptor != null -> {
                        ParcelFileDescriptor.AutoCloseInputStream(response.fileDescriptor).use { it.readBytes() }
                    }
                    response.data != null -> response.data
                    else -> ByteArray(0)
                }
                continuation.resume(Result.success(bytes))
            } else {
                continuation.resume(Result.failure(Exception(response.errorMessage ?: "Camera capture failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "[$pluginId] captureImage failed")
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
            val bridge = hardwareBridge
            if (bridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not connected")))
                return@suspendCancellableCoroutine
            }
            
            // Use httpGet (httpPost not implemented yet)
            if (method.uppercase() != "GET") {
                continuation.resume(Result.failure(UnsupportedOperationException("Only GET supported currently")))
                return@suspendCancellableCoroutine
            }
            
            val response = bridge.httpGet(pluginId, url)
            if (response.success) {
                val result = when {
                    response.fileDescriptor != null -> {
                        ParcelFileDescriptor.AutoCloseInputStream(response.fileDescriptor).use { 
                            it.readBytes().toString(Charsets.UTF_8)
                        }
                    }
                    response.data != null -> response.data.toString(Charsets.UTF_8)
                    else -> ""
                }
                continuation.resume(Result.success(result))
            } else {
                continuation.resume(Result.failure(Exception(response.errorMessage ?: "HTTP request failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "[$pluginId] httpRequest failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun printDocument(
        data: ByteArray,
        mimeType: String,
        printerName: String?
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            val bridge = hardwareBridge
            if (bridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not connected")))
                return@suspendCancellableCoroutine
            }
            
            // Create temp file for document
            val tempFile = File.createTempFile("print_", ".pdf", appContext.cacheDir)
            tempFile.writeBytes(data)
            
            val documentFd = ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            
            val printerId = printerName ?: "default"
            val response = bridge.printDocument(pluginId, printerId, documentFd)
            
            tempFile.delete()
            
            if (response.success) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(Exception(response.errorMessage ?: "Print failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "[$pluginId] printDocument failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override suspend fun getBluetoothDevices(): Result<List<BluetoothDeviceInfo>> = suspendCancellableCoroutine { continuation ->
        try {
            val bridge = hardwareBridge
            if (bridge == null) {
                continuation.resume(Result.failure(IllegalStateException("Hardware Bridge not connected")))
                return@suspendCancellableCoroutine
            }
            
            val devices = bridge.getPairedBluetoothDevices(pluginId)
            val deviceList = devices.map { address ->
                BluetoothDeviceInfo(
                    name = address,
                    address = address,
                    bondState = 12 // BOND_BONDED
                )
            }
            continuation.resume(Result.success(deviceList))
        } catch (e: Exception) {
            Timber.e(e, "[$pluginId] getBluetoothDevices failed")
            continuation.resume(Result.failure(e))
        }
    }
    
    override fun getHardwareBridge(): Any? {
        // SECURITY: Raw bridge access removed to prevent pluginId spoofing
        // Plugins must use the provided bridge API methods instead
        Timber.w("[$pluginId] getHardwareBridge() called - raw bridge access denied for security")
        return null
    }
    
    /**
     * Load native library for this plugin
     */
    fun loadNativeLibrary(libraryName: String): Result<Unit> {
        val libraryPath = File(pluginDataDir, "native/$libraryName").absolutePath
        return nativeLibraryManager.loadLibrary(libraryPath)
    }
    
    // ========================================
    // Plugin Messaging APIs Implementation
    // ========================================
    
    override suspend fun sendMessageToPlugin(
        receiverId: String,
        messageType: String,
        payload: ByteArray
    ): Result<MessageResponse> {
        return try {
            if (messagingProxy == null) {
                return Result.failure(IllegalStateException("Messaging proxy not available"))
            }
            
            val message = PluginMessage(
                senderId = pluginId,
                receiverId = receiverId,
                messageType = messageType,
                payload = payload,
                requestId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis()
            )
            
            messagingProxy.sendMessage(message)
        } catch (e: Exception) {
            Timber.e(e, "[$pluginId] Failed to send message to plugin: $receiverId")
            Result.failure(e)
        }
    }
    
    override suspend fun receiveMessages(): Flow<PluginMessage> {
        return flow {
            if (messagingProxy == null) {
                Timber.w("[$pluginId] Messaging proxy not available, cannot receive messages")
                return@flow
            }
            
            while (currentCoroutineContext().isActive) {
                try {
                    val result = messagingProxy.receiveMessages(pluginId)
                    result.onSuccess { messages ->
                        messages.forEach { message ->
                            emit(message)
                        }
                    }.onFailure { error ->
                        Timber.e(error, "[$pluginId] Error receiving messages")
                    }
                    
                    // Poll interval: check for new messages every 100ms
                    delay(100)
                } catch (e: CancellationException) {
                    // Expected during plugin unload / scope cancellation.
                    Timber.d("[$pluginId] receiveMessages flow cancelled")
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "[$pluginId] Error in receiveMessages flow")
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
        messagingScope.launch {
            try {
                receiveMessages().collect { message ->
                    if (message.messageType == messageType) {
                        val registeredHandler = messageHandlers[messageType]
                        if (registeredHandler != null) {
                            try {
                                val response = registeredHandler(message)
                                
                                // Send response back to sender
                                messagingProxy?.sendResponse(response)?.onFailure { error ->
                                    Timber.e(error, "[$pluginId] Failed to send response for message: ${message.requestId}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "[$pluginId] Error in message handler for type: $messageType")
                                
                                // Send error response
                                val errorResponse = MessageResponse.error(
                                    message.requestId,
                                    "Handler error: ${e.message}"
                                )
                                messagingProxy?.sendResponse(errorResponse)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Expected during plugin unload / scope cancellation.
                Timber.d("[$pluginId] Message handler coroutine cancelled")
            } catch (e: Exception) {
                Timber.e(e, "[$pluginId] Error in message handler coroutine")
            }
        }
        
        Timber.d("[$pluginId] Registered message handler for type: $messageType")
    }
    
    fun cleanup() {
        // Clear services first to prevent further usage
        services.clear()
        
        // Cleanup messaging
        messagingScope.cancel()
        messageHandlers.clear()
        messagingProxy?.let { proxy ->
            messagingScope.launch {
                proxy.unregisterPlugin(pluginId)
                proxy.disconnect()
            }
        }
        
        hardwareBridgeConnection?.let { connection ->
            try {
                appContext.unbindService(connection)
                Timber.d("[$pluginId] Hardware Bridge unbound successfully")
            } catch (e: IllegalArgumentException) {
                // Service was not registered or already unbound - this is expected in some cases
                Timber.d("[$pluginId] Hardware Bridge was already unbound")
            } catch (e: Exception) {
                Timber.e(e, "[$pluginId] Failed to unbind Hardware Bridge")
            }
        }
        hardwareBridge = null
        hardwareBridgeConnection = null
        
        Timber.i("[$pluginId] PluginContext cleanup completed")
    }
}

/**
 * Isolated PluginContext implementation that uses IsolatedPluginContext for enhanced security.
 * 
 * This class extends PluginContextImpl and overrides getApplicationContext() to return
 * an IsolatedPluginContext instead of the regular secure context wrapper.
 * 
 * Usage:
 * ```kotlin
 * val pluginContext = IsolatedPluginContextImpl(
 *     appContext = context,
 *     pluginId = pluginId,
 *     pluginDataDir = pluginDir,
 *     nativeLibraryManager = nativeLibraryManager,
 *     permissionManager = permissionManager
 * )
 * ```
 */
class IsolatedPluginContextImpl(
    appContext: Context,
    pluginId: String,
    pluginDataDir: File,
    nativeLibraryManager: NativeLibraryManager,
    permissionManager: PluginPermissionManager,
    messagingProxy: PluginMessagingProxy? = null
) : PluginContextImpl(
    appContext,
    pluginId,
    pluginDataDir,
    nativeLibraryManager,
    permissionManager,
    messagingProxy
) {
    
    private val isolatedContext = IsolatedPluginContext(appContext, pluginId)
    
    override fun getApplicationContext(): Context {
        // Return isolated context instead of secure context wrapper
        return isolatedContext
    }
}
