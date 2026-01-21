package com.ble1st.connectias.plugin.security

import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.IFileSystemBridge
import timber.log.Timber

/**
 * Secure wrapper for File System Bridge that enforces identity verification
 * 
 * Prevents pluginId spoofing by using PluginIdentitySession for verification
 * instead of trusting pluginId parameter from client.
 * 
 * SECURITY: All methods verify caller identity before delegating to actual bridge
 */
class SecureFileSystemBridgeWrapper(
    private val actualBridge: IFileSystemBridge,
    private val boundPluginId: String,
    private val auditManager: SecurityAuditManager? = null
) : IFileSystemBridge.Stub() {
    
    /**
     * Verifies that the calling plugin matches the bound identity
     * Returns verified pluginId or throws SecurityException
     */
    private fun verifyCallerIdentity(): String {
        val verifiedPluginId = PluginIdentitySession.verifyPluginIdentity()
        
        if (verifiedPluginId == null) {
            throw SecurityException("No verified plugin identity - access denied")
        }
        
        if (verifiedPluginId != boundPluginId) {
            throw SecurityException(
                "Identity mismatch: wrapper bound to '$boundPluginId' but caller verified as '$verifiedPluginId'"
            )
        }
        
        return verifiedPluginId
    }
    
    // ════════════════════════════════════════════════════════
    // SECURE FILE SYSTEM METHODS - All verify identity first
    // ════════════════════════════════════════════════════════
    
    override fun createFile(pluginId: String, path: String, mode: Int): ParcelFileDescriptor? {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            auditManager?.logPluginSpoofingAttempt(
                pluginId = verifiedPluginId,
                claimedId = pluginId,
                actualId = verifiedPluginId,
                source = "SecureFileSystemBridgeWrapper.createFile"
            )
            return null
        }
        
        return actualBridge.createFile(verifiedPluginId, path, mode)
    }
    
    override fun openFile(pluginId: String, path: String, mode: Int): ParcelFileDescriptor? {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            auditManager?.logPluginSpoofingAttempt(
                pluginId = verifiedPluginId,
                claimedId = pluginId,
                actualId = verifiedPluginId,
                source = "SecureFileSystemBridgeWrapper.openFile"
            )
            return null
        }
        
        return actualBridge.openFile(verifiedPluginId, path, mode)
    }
    
    override fun deleteFile(pluginId: String, path: String): Boolean {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            auditManager?.logPluginSpoofingAttempt(
                pluginId = verifiedPluginId,
                claimedId = pluginId,
                actualId = verifiedPluginId,
                source = "SecureFileSystemBridgeWrapper.deleteFile"
            )
            return false
        }
        
        return actualBridge.deleteFile(verifiedPluginId, path)
    }
    
    override fun fileExists(pluginId: String, path: String): Boolean {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return false
        }
        
        return actualBridge.fileExists(verifiedPluginId, path)
    }
    
    override fun listFiles(pluginId: String, path: String): Array<String> {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return emptyArray()
        }
        
        return actualBridge.listFiles(verifiedPluginId, path)
    }
    
    override fun getFileSize(pluginId: String, path: String): Long {
        val verifiedPluginId = verifyCallerIdentity()
        
        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return -1
        }
        
        return actualBridge.getFileSize(verifiedPluginId, path)
    }
}
