package com.ble1st.connectias.hardware

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.PluginPermissionManager
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
    
    private val binder = object : IHardwareBridge.Stub() {
        
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
                if (!checkPermission(pluginId, android.Manifest.permission.CAMERA)) {
                    return HardwareResponseParcel.failure("Permission denied: CAMERA")
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
                if (!checkPermission(pluginId, android.Manifest.permission.CAMERA)) {
                    return HardwareResponseParcel.failure("Permission denied: CAMERA")
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
                    return HardwareResponseParcel.failure("Permission denied: INTERNET")
                }
                
                Timber.i("[HARDWARE BRIDGE] HTTP GET $url by $pluginId")
                networkBridge.httpGet(url)
                
            } catch (e: Exception) {
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
                
                Timber.i("[HARDWARE BRIDGE] HTTP POST $url by $pluginId")
                networkBridge.httpPost(url, dataFd)
                
            } catch (e: Exception) {
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
                
                Timber.i("[HARDWARE BRIDGE] Socket $host:$port by $pluginId")
                networkBridge.openSocket(host, port)
                
            } catch (e: Exception) {
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
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("[HARDWARE BRIDGE] Service created in process: ${android.os.Process.myPid()}")
        
        // Initialize permission manager
        permissionManager = PluginPermissionManager(applicationContext)
        
        // Initialize hardware bridges
        cameraBridge = CameraBridge(applicationContext)
        networkBridge = NetworkBridge(applicationContext)
        printerBridge = PrinterBridge(applicationContext)
        bluetoothBridge = BluetoothBridge(applicationContext)
    }
    
    override fun onBind(intent: Intent): IBinder {
        Timber.i("[HARDWARE BRIDGE] Service bound")
        return binder
    }
    
    override fun onDestroy() {
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
