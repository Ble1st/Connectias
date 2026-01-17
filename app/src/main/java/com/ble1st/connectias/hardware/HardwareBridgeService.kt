package com.ble1st.connectias.hardware

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.security.PluginNetworkTracker
import com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy
import com.ble1st.connectias.plugin.security.NetworkUsageAggregator
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Hardware Bridge Service running in Main Process
 * 
 * Provides hardware access to isolated sandbox process via IPC.
 * All hardware operations go through this service to maintain security.
 * 
 * ARCHITECTURE:
 * - Main Process: Runs this service with all app permissions
 * - Sandbox Process: Isolated process without permissions, calls via IPC
 * - Permission checks happen here before hardware access
 * 
 * SECURITY:
 * - Each method checks plugin permissions via PluginPermissionManager
 * - Default: All hardware access DISABLED until user grants permission
 * - All errors are logged and returned as HardwareResponseParcel
 * 
 * @since 2.0.0 (isolated process support)
 */
class HardwareBridgeService : Service() {
    
    private lateinit var permissionManager: PluginPermissionManager
    private lateinit var cameraBridge: CameraBridge
    private lateinit var networkBridge: NetworkBridge
    private lateinit var printerBridge: PrinterBridge
    private lateinit var bluetoothBridge: BluetoothBridge
    private lateinit var enhancedNetworkPolicy: EnhancedPluginNetworkPolicy
    
    private val permissionRequestManager = PermissionRequestManager.getInstance()
    
    private val binder = object : IHardwareBridge.Stub() {
        
        override fun requestPermission(pluginId: String, permission: String): HardwareResponseParcel {
            return try {
                val activity = permissionRequestManager.currentActivity
                if (activity == null) {
                    return HardwareResponseParcel.failure("No active activity for permission request")
                }
                
                Timber.i("[HARDWARE BRIDGE] Permission request: $permission for $pluginId")
                val granted = permissionRequestManager.requestPermissionBlocking(activity, pluginId, permission)
                
                if (granted) {
                    // Also grant in PluginPermissionManager for future checks
                    permissionManager.grantPermissionForPlugin(pluginId, listOf(permission))
                    HardwareResponseParcel.success(metadata = mapOf("granted" to "true"))
                } else {
                    HardwareResponseParcel.failure("Permission denied by user")
                }
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Permission request failed")
                HardwareResponseParcel.failure(e)
            }
        }
        
        // ════════════════════════════════════════════════════════
        // PERMISSION ENFORCEMENT
        // ════════════════════════════════════════════════════════
        
        /**
         * Check if plugin has required permission
         * Logs denial for audit trail
         */
        private fun checkPermission(pluginId: String, permission: String): Boolean {
            val allowed = permissionManager.isPermissionAllowed(pluginId, permission)
            if (!allowed) {
                Timber.w("[HARDWARE BRIDGE] Plugin $pluginId denied: $permission")
            } else {
                Timber.d("[HARDWARE BRIDGE] Plugin $pluginId granted: $permission")
            }
            return allowed
        }
        
        // ════════════════════════════════════════════════════════
        // CAMERA BRIDGE
        // ════════════════════════════════════════════════════════
        
        override fun captureImage(pluginId: String): HardwareResponseParcel {
            return try {
                // Auto-request permission if not granted
                if (!checkPermission(pluginId, android.Manifest.permission.CAMERA)) {
                    val activity = permissionRequestManager.currentActivity
                    if (activity != null) {
                        Timber.i("[HARDWARE BRIDGE] Auto-requesting CAMERA permission for $pluginId")
                        val granted = permissionRequestManager.requestPermissionBlocking(
                            activity, pluginId, android.Manifest.permission.CAMERA
                        )
                        if (granted) {
                            permissionManager.grantPermissionForPlugin(pluginId, listOf(android.Manifest.permission.CAMERA))
                        } else {
                            return HardwareResponseParcel.failure("Camera permission denied by user")
                        }
                    } else {
                        return HardwareResponseParcel.failure("No activity available for permission request")
                    }
                }
                
                Timber.i("[HARDWARE BRIDGE] Camera capture requested by $pluginId")
                cameraBridge.captureImage(pluginId)
                
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Camera capture failed for $pluginId")
                HardwareResponseParcel.failure(e)
            }
        }
        
        override fun startCameraPreview(pluginId: String): HardwareResponseParcel {
            return try {
                // Auto-request permission if not granted
                if (!checkPermission(pluginId, android.Manifest.permission.CAMERA)) {
                    val activity = permissionRequestManager.currentActivity
                    if (activity != null) {
                        Timber.i("[HARDWARE BRIDGE] Auto-requesting CAMERA permission for $pluginId")
                        val granted = permissionRequestManager.requestPermissionBlocking(
                            activity, pluginId, android.Manifest.permission.CAMERA
                        )
                        if (granted) {
                            permissionManager.grantPermissionForPlugin(pluginId, listOf(android.Manifest.permission.CAMERA))
                        } else {
                            return HardwareResponseParcel.failure("Camera permission denied by user")
                        }
                    } else {
                        return HardwareResponseParcel.failure("No activity available for permission request")
                    }
                }
                
                Timber.i("[HARDWARE BRIDGE] Camera preview start by $pluginId")
                cameraBridge.startPreview(pluginId)
                
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Camera preview failed for $pluginId")
                HardwareResponseParcel.failure(e)
            }
        }
        
        override fun stopCameraPreview(pluginId: String) {
            try {
                Timber.i("[HARDWARE BRIDGE] Camera preview stop by $pluginId")
                cameraBridge.stopPreview(pluginId)
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Camera stop failed for $pluginId")
            }
        }
        
        // ════════════════════════════════════════════════════════
        // NETWORK BRIDGE
        // ════════════════════════════════════════════════════════
        
        override fun httpGet(pluginId: String, url: String): HardwareResponseParcel {
            return try {
                if (!checkPermission(pluginId, android.Manifest.permission.INTERNET)) {
                    return HardwareResponseParcel.failure("Internet permission required")
                }
                
                // Enhanced network policy check
                val policyResult = enhancedNetworkPolicy.isRequestAllowed(pluginId, url, isTelemetry = false)
                if (!policyResult.allowed) {
                    Timber.w("[HARDWARE BRIDGE] HTTP GET blocked by policy: $pluginId -> $url (${policyResult.reason})")
                    return HardwareResponseParcel.failure("Request blocked by network policy: ${policyResult.reason}")
                }
                
                // Track network request
                PluginNetworkTracker.trackNetworkRequest(pluginId, url, "GET")
                
                Timber.i("[HARDWARE BRIDGE] HTTP GET requested by $pluginId: $url")
                val response = networkBridge.httpGet(url)
                
                // Track data transfer if successful
                if (response.success) {
                    val responseSize = response.data?.size?.toLong() ?: 0L
                    PluginNetworkTracker.trackDataTransfer(pluginId, bytesReceived = responseSize)
                    NetworkUsageAggregator.recordExplicitUsage(pluginId, bytesReceived = responseSize, domain = java.net.URL(url).host)
                }
                
                response
            } catch (e: Exception) {
                PluginNetworkTracker.trackConnectionFailure(pluginId, url, e.message ?: "Unknown error")
                Timber.e(e, "[HARDWARE BRIDGE] HTTP GET failed for $pluginId")
                HardwareResponseParcel.failure(e)
            }
        }
        
        override fun httpPost(
            pluginId: String, 
            url: String, 
            dataFd: ParcelFileDescriptor
        ): HardwareResponseParcel {
            return try {
                if (!checkPermission(pluginId, android.Manifest.permission.INTERNET)) {
                    dataFd.close()
                    return HardwareResponseParcel.failure("Permission denied: INTERNET")
                }
                
                // Enhanced network policy check for POST (more sensitive)
                val policyResult = enhancedNetworkPolicy.isRequestAllowed(pluginId, url, isTelemetry = false)
                if (!policyResult.allowed) {
                    Timber.w("[HARDWARE BRIDGE] HTTP POST blocked by policy: $pluginId -> $url (${policyResult.reason})")
                    dataFd.close()
                    return HardwareResponseParcel.failure("Request blocked by network policy: ${policyResult.reason}")
                }
                
                // Estimate data size for tracking
                val dataSize = try {
                    dataFd.statSize
                } catch (e: Exception) {
                    0L
                }
                
                // Track network request
                PluginNetworkTracker.trackNetworkRequest(pluginId, url, "POST")
                
                Timber.i("[HARDWARE BRIDGE] HTTP POST $url by $pluginId (${dataSize} bytes)")
                val response = networkBridge.httpPost(url, dataFd)
                
                // Track data transfer
                if (response.success) {
                    val responseSize = response.data?.size?.toLong() ?: 0L
                    PluginNetworkTracker.trackDataTransfer(pluginId, bytesReceived = responseSize, bytesSent = dataSize)
                    NetworkUsageAggregator.recordExplicitUsage(
                        pluginId, 
                        bytesReceived = responseSize, 
                        bytesSent = dataSize, 
                        domain = java.net.URL(url).host
                    )
                }
                
                response
            } catch (e: Exception) {
                PluginNetworkTracker.trackConnectionFailure(pluginId, url, e.message ?: "Unknown error")
                Timber.e(e, "[HARDWARE BRIDGE] HTTP POST failed for $pluginId")
                HardwareResponseParcel.failure(e)
            } finally {
                try {
                    dataFd.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        }
        
        override fun openSocket(
            pluginId: String,
            host: String,
            port: Int
        ): HardwareResponseParcel {
            return try {
                if (!checkPermission(pluginId, android.Manifest.permission.INTERNET)) {
                    return HardwareResponseParcel.failure("Permission denied: INTERNET")
                }
                
                // Build URL for policy check
                val socketUrl = "tcp://$host:$port"
                
                // Enhanced network policy check for socket (high risk)
                val policyResult = enhancedNetworkPolicy.isRequestAllowed(pluginId, socketUrl, isTelemetry = false)
                if (!policyResult.allowed) {
                    Timber.w("[HARDWARE BRIDGE] Socket blocked by policy: $pluginId -> $host:$port (${policyResult.reason})")
                    return HardwareResponseParcel.failure("Socket blocked by network policy: ${policyResult.reason}")
                }
                
                // Track network request
                PluginNetworkTracker.trackNetworkRequest(pluginId, socketUrl, "SOCKET")
                
                Timber.i("[HARDWARE BRIDGE] Socket $host:$port by $pluginId")
                val response = networkBridge.openSocket(host, port)
                
                // Register for ongoing tracking if successful
                if (response.success) {
                    NetworkUsageAggregator.recordExplicitUsage(pluginId, domain = host)
                }
                
                response
            } catch (e: Exception) {
                PluginNetworkTracker.trackConnectionFailure(pluginId, "$host:$port", e.message ?: "Unknown error")
                Timber.e(e, "[HARDWARE BRIDGE] Socket failed for $pluginId")
                HardwareResponseParcel.failure(e)
            }
        }
        
        // ════════════════════════════════════════════════════════
        // PRINTER BRIDGE
        // ════════════════════════════════════════════════════════
        
        override fun getAvailablePrinters(pluginId: String): List<String> {
            return try {
                Timber.d("[HARDWARE BRIDGE] Get printers by $pluginId")
                printerBridge.getAvailablePrinters()
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Get printers failed for $pluginId")
                emptyList()
            }
        }
        
        override fun printDocument(
            pluginId: String,
            printerId: String,
            documentFd: ParcelFileDescriptor
        ): HardwareResponseParcel {
            return try {
                Timber.i("[HARDWARE BRIDGE] Print document by $pluginId to $printerId")
                printerBridge.printDocument(printerId, documentFd)
                
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Print failed for $pluginId")
                HardwareResponseParcel.failure(e)
            } finally {
                try {
                    documentFd.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        }
        
        // ════════════════════════════════════════════════════════
        // BLUETOOTH BRIDGE
        // ════════════════════════════════════════════════════════
        
        override fun getPairedBluetoothDevices(pluginId: String): List<String> {
            return try {
                if (!checkPermission(pluginId, android.Manifest.permission.BLUETOOTH_CONNECT)) {
                    Timber.w("[HARDWARE BRIDGE] BT devices denied for $pluginId")
                    return emptyList()
                }
                
                Timber.d("[HARDWARE BRIDGE] Get BT devices by $pluginId")
                bluetoothBridge.getPairedDevices()
                
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Get BT devices failed for $pluginId")
                emptyList()
            }
        }
        
        override fun connectBluetoothDevice(
            pluginId: String,
            deviceAddress: String
        ): HardwareResponseParcel {
            return try {
                if (!checkPermission(pluginId, android.Manifest.permission.BLUETOOTH_CONNECT)) {
                    return HardwareResponseParcel.failure("Permission denied: BLUETOOTH_CONNECT")
                }
                
                Timber.i("[HARDWARE BRIDGE] BT connect $deviceAddress by $pluginId")
                bluetoothBridge.connect(deviceAddress)
                
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] BT connect failed for $pluginId")
                HardwareResponseParcel.failure(e)
            }
        }
        
        override fun disconnectBluetoothDevice(pluginId: String, deviceAddress: String) {
            try {
                Timber.i("[HARDWARE BRIDGE] BT disconnect $deviceAddress by $pluginId")
                bluetoothBridge.disconnect(deviceAddress)
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] BT disconnect failed for $pluginId")
            }
        }
        
        // ════════════════════════════════════════════════════════
        // FILE BRIDGE
        // ════════════════════════════════════════════════════════
        
        override fun getPluginFile(pluginPath: String): ParcelFileDescriptor? {
            return try {
                // Security: Validate plugin path
                val pluginFile = File(pluginPath)
                if (!pluginFile.exists()) {
                    Timber.w("[HARDWARE BRIDGE] Plugin file not found: $pluginPath")
                    return null
                }
                
                // Security: Only allow files in plugin directory
                val pluginDir = File(applicationContext.filesDir, "plugins")
                if (!pluginFile.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    Timber.e("[HARDWARE BRIDGE] SECURITY: Plugin path outside plugin dir: $pluginPath")
                    return null
                }
                
                Timber.d("[HARDWARE BRIDGE] Opening plugin file: $pluginPath")
                ParcelFileDescriptor.open(
                    pluginFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Failed to open plugin file: $pluginPath")
                null
            }
        }
        
        override fun writeTempFile(
            pluginId: String,
            dataFd: ParcelFileDescriptor
        ): String? {
            return try {
                // Create temp directory for plugin
                val tempDir = File(applicationContext.cacheDir, "plugin_temp/$pluginId")
                tempDir.mkdirs()
                
                // Create temp file
                val tempFile = File.createTempFile("data_", ".tmp", tempDir)
                
                // Copy data from FD to temp file
                FileInputStream(dataFd.fileDescriptor).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                Timber.d("[HARDWARE BRIDGE] Temp file created for $pluginId: ${tempFile.absolutePath}")
                tempFile.absolutePath
                
            } catch (e: Exception) {
                Timber.e(e, "[HARDWARE BRIDGE] Failed to write temp file for $pluginId")
                null
            } finally {
                try {
                    dataFd.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        }
        
        // ════════════════════════════════════════════════════════
        // UTILITY
        // ════════════════════════════════════════════════════════
        
        override fun ping(): Boolean {
            return true
        }
        
        // ════════════════════════════════════════════════════════
        // NETWORK POLICY MANAGEMENT
        // ════════════════════════════════════════════════════════
        
        override fun registerPluginNetworking(pluginId: String, telemetryOnly: Boolean) {
            this@HardwareBridgeService.registerPluginNetworking(pluginId, telemetryOnly)
        }
        
        override fun unregisterPluginNetworking(pluginId: String) {
            this@HardwareBridgeService.unregisterPluginNetworking(pluginId)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("[HARDWARE BRIDGE] Service created in process: ${android.os.Process.myPid()}")
        
        // Initialize permission manager
        permissionManager = PluginPermissionManager(applicationContext)
        
        // Initialize hardware bridges
        initializeBridges()
        
        Timber.i("[HARDWARE BRIDGE] All bridges initialized with enhanced network tracking")
    }
    
    private fun initializeBridges() {
        cameraBridge = CameraBridge(this)
        networkBridge = NetworkBridge(this)
        printerBridge = PrinterBridge(this)
        bluetoothBridge = BluetoothBridge(this)
        
        // Initialize enhanced network tracking and policy
        PluginNetworkTracker.startTracking()
        NetworkUsageAggregator.startAggregation()
        enhancedNetworkPolicy = EnhancedPluginNetworkPolicy()
        
        Timber.i("[HARDWARE BRIDGE] Enhanced network security initialized")
    }
    
    override fun onBind(intent: Intent): IBinder {
        Timber.i("[HARDWARE BRIDGE] Service bound")
        return binder
    }
    
    /**
     * Register a plugin with network policy and tracking
     */
    fun registerPluginNetworking(pluginId: String, telemetryOnly: Boolean = false) {
        try {
            // Register with enhanced network policy
            enhancedNetworkPolicy.registerPlugin(pluginId, telemetryOnly)
            
            // Register with network tracker
            PluginNetworkTracker.registerPlugin(pluginId)
            
            // Register with usage aggregator
            NetworkUsageAggregator.registerPlugin(pluginId, android.os.Process.myUid())
            
            Timber.i("[HARDWARE BRIDGE] Plugin registered for networking: $pluginId (telemetry-only: $telemetryOnly)")
        } catch (e: Exception) {
            Timber.e(e, "[HARDWARE BRIDGE] Failed to register plugin networking: $pluginId")
        }
    }
    
    /**
     * Unregister a plugin from network policy and tracking
     */
    fun unregisterPluginNetworking(pluginId: String) {
        try {
            // Unregister from enhanced network policy
            enhancedNetworkPolicy.unregisterPlugin(pluginId)
            
            // Unregister from network tracker
            PluginNetworkTracker.unregisterPlugin(pluginId)
            
            // Unregister from usage aggregator
            NetworkUsageAggregator.unregisterPlugin(pluginId)
            
            Timber.i("[HARDWARE BRIDGE] Plugin unregistered from networking: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "[HARDWARE BRIDGE] Failed to unregister plugin networking: $pluginId")
        }
    }
    
    override fun onDestroy() {
        // Cleanup network tracking
        try {
            PluginNetworkTracker.stopTracking()
            NetworkUsageAggregator.stopAggregation()
            Timber.i("[HARDWARE BRIDGE] Network tracking cleaned up")
        } catch (e: Exception) {
            Timber.w(e, "[HARDWARE BRIDGE] Error during network tracking cleanup")
        }
        
        super.onDestroy()
        Timber.i("[HARDWARE BRIDGE] Service destroyed")
        
        // Cleanup bridges
        try {
            cameraBridge.cleanup()
            networkBridge.cleanup()
            bluetoothBridge.cleanup()
        } catch (e: Exception) {
            Timber.e(e, "[HARDWARE BRIDGE] Cleanup error")
        }
    }
}
