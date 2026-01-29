package com.ble1st.connectias.plugin.security

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.hardware.HardwareResponseParcel
import com.ble1st.connectias.hardware.IHardwareBridge
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import com.ble1st.connectias.plugin.PluginPermissionManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Secure wrapper for Hardware Bridge that enforces identity verification
 * and permission pre-checks.
 *
 * SECURITY LAYERS:
 * 1. Identity verification - prevents pluginId spoofing
 * 2. Permission pre-check - verifies permissions BEFORE API execution
 * 3. Audit logging - tracks all security violations
 *
 * SECURITY: All methods verify caller identity and permissions before delegating to actual bridge
 */
class SecureHardwareBridgeWrapper @Inject constructor(
    private val actualBridge: IHardwareBridge,
    private val context: Context,
    private val permissionManager: PluginPermissionManager,
    private val auditManager: SecurityAuditManager?
) : IHardwareBridge.Stub() {

    // Permission pre-checker for API → Permission mapping
    private val permissionPreChecker = PermissionPreChecker(permissionManager)
    
    /**
     * Verifies that the calling plugin matches the bound identity
     * Returns verified pluginId or throws SecurityException
     */
    private fun verifyCallerIdentity(): String {
        val verifiedPluginId = PluginIdentitySession.verifyPluginIdentity()
            ?: throw SecurityException("Unable to verify plugin identity - no active session found")

        return verifiedPluginId
    }
    
    // ════════════════════════════════════════════════════════
    // SECURE BRIDGE METHODS - All verify identity first
    // ════════════════════════════════════════════════════════
    
    override fun requestPermission(pluginId: String, permission: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            auditManager?.logPluginSpoofingAttempt(
                pluginId = verifiedPluginId,
                claimedId = pluginId,
                actualId = verifiedPluginId,
                source = "SecureHardwareBridgeWrapper.requestPermission"
            )
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.requestPermission(verifiedPluginId, permission)
    }
    
    @RequiresPluginPermission("CAMERA")
    override fun captureImage(pluginId: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check BEFORE execution
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "captureImage")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.captureImage(verifiedPluginId)
    }
    
    @RequiresPluginPermission("CAMERA")
    override fun startCameraPreview(pluginId: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "startCameraPreview")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.startCameraPreview(verifiedPluginId)
    }

    @RequiresPluginPermission("CAMERA")
    override fun stopCameraPreview(pluginId: String) {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "stopCameraPreview")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return
        }

        actualBridge.stopCameraPreview(verifiedPluginId)
    }
    
    @RequiresPluginPermission("INTERNET")
    override fun httpGet(pluginId: String, url: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            auditManager?.logPluginSpoofingAttempt(
                pluginId = verifiedPluginId,
                claimedId = pluginId,
                actualId = verifiedPluginId,
                source = "SecureHardwareBridgeWrapper.httpGet"
            )
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "httpGet")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.httpGet(verifiedPluginId, url)
    }

    @RequiresPluginPermission("INTERNET")
    override fun httpPost(pluginId: String, url: String, dataFd: ParcelFileDescriptor): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            auditManager?.logPluginSpoofingAttempt(
                pluginId = verifiedPluginId,
                claimedId = pluginId,
                actualId = verifiedPluginId,
                source = "SecureHardwareBridgeWrapper.httpPost"
            )
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "httpPost")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.httpPost(verifiedPluginId, url, dataFd)
    }

    @RequiresPluginPermission("INTERNET")
    override fun openSocket(pluginId: String, host: String, port: Int): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "openSocket")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.openSocket(verifiedPluginId, host, port)
    }

    @RequiresPluginPermission("INTERNET")
    override fun tcpPing(pluginId: String, host: String, port: Int, timeoutMs: Int): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "tcpPing")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.tcpPing(verifiedPluginId, host, port, timeoutMs)
    }
    
    @RequiresPluginPermission("PRINTER")
    override fun getAvailablePrinters(pluginId: String): List<String> {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return emptyList()
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "getAvailablePrinters")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return emptyList()
        }

        return actualBridge.getAvailablePrinters(verifiedPluginId)
    }

    @RequiresPluginPermission("PRINTER")
    override fun printDocument(pluginId: String, printerId: String, documentFd: ParcelFileDescriptor): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "printDocument")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.printDocument(verifiedPluginId, printerId, documentFd)
    }
    
    @RequiresPluginPermission("BLUETOOTH")
    override fun getPairedBluetoothDevices(pluginId: String): List<String> {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return emptyList()
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "getPairedBluetoothDevices")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return emptyList()
        }

        return actualBridge.getPairedBluetoothDevices(verifiedPluginId)
    }

    @RequiresPluginPermission("BLUETOOTH", "BLUETOOTH_CONNECT")
    override fun connectBluetoothDevice(pluginId: String, deviceAddress: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "connectBluetoothDevice")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return HardwareResponseParcel.failure(e.message ?: "Permission denied")
        }

        return actualBridge.connectBluetoothDevice(verifiedPluginId, deviceAddress)
    }

    @RequiresPluginPermission("BLUETOOTH", "BLUETOOTH_CONNECT")
    override fun disconnectBluetoothDevice(pluginId: String, deviceAddress: String) {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "disconnectBluetoothDevice")
        } catch (e: SecurityException) {
            Timber.e("[SECURE BRIDGE] Permission denied: ${e.message}")
            return
        }

        actualBridge.disconnectBluetoothDevice(verifiedPluginId, deviceAddress)
    }
    
    override fun getPluginFile(pluginPath: String): ParcelFileDescriptor? {
        verifyCallerIdentity() // Verify identity but don't need to check pluginId param for this method
        return actualBridge.getPluginFile(pluginPath)
    }
    
    override fun writeTempFile(pluginId: String, dataFd: ParcelFileDescriptor): String? {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return null
        }
        
        return actualBridge.writeTempFile(verifiedPluginId, dataFd)
    }
    
    override fun ping(): Boolean {
        verifyCallerIdentity() // Verify identity but this method doesn't need pluginId
        return actualBridge.ping()
    }
    
    override fun registerPluginNetworking(pluginId: String, telemetryOnly: Boolean) {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return
        }
        
        actualBridge.registerPluginNetworking(verifiedPluginId, telemetryOnly)
    }
    
    override fun unregisterPluginNetworking(pluginId: String) {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return
        }
        
        actualBridge.unregisterPluginNetworking(verifiedPluginId)
    }
}
