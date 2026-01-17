package com.ble1st.connectias.plugin

import android.content.Context
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.sdk.BluetoothDeviceInfo
import com.ble1st.connectias.plugin.sdk.CameraPreviewInfo
import com.ble1st.connectias.hardware.IHardwareBridge
import com.ble1st.connectias.hardware.HardwareBridgeService
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    
    // Hardware Bridge connection (lazy)
    private var hardwareBridge: IHardwareBridge? = null
    private var hardwareBridgeConnection: ServiceConnection? = null
    
    init {
        // Connect to HardwareBridge service
        connectToHardwareBridge()
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
    
    fun cleanup() {
        hardwareBridgeConnection?.let {
            try {
                appContext.unbindService(it)
            } catch (e: Exception) {
                Timber.e(e, "[$pluginId] Failed to unbind Hardware Bridge")
            }
        }
        hardwareBridge = null
        hardwareBridgeConnection = null
    }
}
