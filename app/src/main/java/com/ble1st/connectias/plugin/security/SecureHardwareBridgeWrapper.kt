package com.ble1st.connectias.plugin.security

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.hardware.HardwareResponseParcel
import com.ble1st.connectias.hardware.IHardwareBridge
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Secure wrapper for Hardware Bridge that enforces identity verification
 * 
 * Prevents pluginId spoofing by using PluginIdentitySession for verification
 * instead of trusting pluginId parameter from client.
 * 
 * SECURITY: All methods verify caller identity before delegating to actual bridge
 */
class SecureHardwareBridgeWrapper @Inject constructor(
    private val actualBridge: IHardwareBridge,
    private val context: Context,
    private val auditManager: SecurityAuditManager
) : IHardwareBridge.Stub() {
    
    /**
     * Verifies that the calling plugin matches the bound identity
     * Returns verified pluginId or throws SecurityException
     */
    private fun verifyCallerIdentity(): String {
        val verifiedPluginId = PluginIdentitySession.verifyPluginIdentity()
        
        if (verifiedPluginId == null) {
            throw SecurityException("Unable to verify plugin identity - no active session found")
        }
        
        return verifiedPluginId
    }
    
    // ════════════════════════════════════════════════════════
    // SECURE BRIDGE METHODS - All verify identity first
    // ════════════════════════════════════════════════════════
    
    override fun requestPermission(pluginId: String, permission: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.requestPermission(verifiedPluginId, permission)
    }
    
    override fun captureImage(pluginId: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.captureImage(verifiedPluginId)
    }
    
    override fun startCameraPreview(pluginId: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.startCameraPreview(verifiedPluginId)
    }
    
    override fun stopCameraPreview(pluginId: String) {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return
        }
        
        actualBridge.stopCameraPreview(verifiedPluginId)
    }
    
    override fun httpGet(pluginId: String, url: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.httpGet(verifiedPluginId, url)
    }
    
    override fun httpPost(pluginId: String, url: String, dataFd: ParcelFileDescriptor): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.httpPost(verifiedPluginId, url, dataFd)
    }
    
    override fun openSocket(pluginId: String, host: String, port: Int): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.openSocket(verifiedPluginId, host, port)
    }
    
    override fun getAvailablePrinters(pluginId: String): List<String> {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return emptyList()
        }
        
        return actualBridge.getAvailablePrinters(verifiedPluginId)
    }
    
    override fun printDocument(pluginId: String, printerId: String, documentFd: ParcelFileDescriptor): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.printDocument(verifiedPluginId, printerId, documentFd)
    }
    
    override fun getPairedBluetoothDevices(pluginId: String): List<String> {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return emptyList()
        }
        
        return actualBridge.getPairedBluetoothDevices(verifiedPluginId)
    }
    
    override fun connectBluetoothDevice(pluginId: String, deviceAddress: String): HardwareResponseParcel {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return HardwareResponseParcel.failure("Identity verification failed")
        }
        
        return actualBridge.connectBluetoothDevice(verifiedPluginId, deviceAddress)
    }
    
    override fun disconnectBluetoothDevice(pluginId: String, deviceAddress: String) {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
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
