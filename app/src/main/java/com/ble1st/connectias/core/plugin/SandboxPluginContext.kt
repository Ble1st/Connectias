package com.ble1st.connectias.core.plugin

import android.content.Context
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.sdk.BluetoothDeviceInfo
import com.ble1st.connectias.plugin.sdk.CameraPreviewInfo
import com.ble1st.connectias.plugin.NativeLibraryManager
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.SecureContextWrapper
import com.ble1st.connectias.hardware.IHardwareBridge
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume

/**
 * Minimal PluginContext implementation for sandbox process
 * Limited functionality to prevent sandbox escape
 * Uses SecureContextWrapper to enforce permission checks
 */
class SandboxPluginContext(
    private val appContext: Context,
    private val pluginDir: File,
    private val pluginId: String,
    private val permissionManager: PluginPermissionManager,
    private val hardwareBridge: IHardwareBridge? = null
) : PluginContext {
    
    private val serviceRegistry = mutableMapOf<String, Any>()
    private val nativeLibManager = NativeLibraryManager(pluginDir)
    
    // Lazy-initialized secure context wrapper
    private val secureContext: Context by lazy {
        SecureContextWrapper(appContext, pluginId, permissionManager)
    }
    
    init {
        if (!pluginDir.exists()) {
            pluginDir.mkdirs()
        }
    }
    
    override fun getApplicationContext(): Context {
        // Return wrapped context instead of direct context
        return secureContext
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
            Timber.tag("[SANDBOX:$pluginId]").e(throwable, message)
        } else {
            Timber.tag("[SANDBOX:$pluginId]").e(message)
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
                val imageData = response.data ?: byteArrayOf()
                continuation.resume(Result.success(imageData))
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
        return hardwareBridge
    }
}
