package com.ble1st.connectias.plugin.security

import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.IFileSystemBridge
import com.ble1st.connectias.plugin.PluginPermissionManager
import timber.log.Timber

/**
 * Secure wrapper for File System Bridge that enforces identity verification
 * and permission pre-checks.
 *
 * SECURITY LAYERS:
 * 1. Identity verification - prevents pluginId spoofing
 * 2. Permission pre-check - verifies permissions BEFORE file access
 * 3. Audit logging - tracks all security violations
 *
 * SECURITY: All methods verify caller identity and permissions before delegating to actual bridge
 */
class SecureFileSystemBridgeWrapper(
    private val actualBridge: IFileSystemBridge,
    private val boundPluginId: String,
    private val permissionManager: PluginPermissionManager,
    private val auditManager: SecurityAuditManager? = null
) : IFileSystemBridge.Stub() {

    // Permission pre-checker for file API → Permission mapping
    private val permissionPreChecker = PermissionPreChecker(permissionManager)
    
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
    
    @RequiresPluginPermission("FILE_WRITE")
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

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "createFile")
        } catch (e: SecurityException) {
            Timber.e("[SECURE FS BRIDGE] Permission denied: ${e.message}")
            return null
        }

        return actualBridge.createFile(verifiedPluginId, path, mode)
    }

    @RequiresPluginPermission("FILE_READ")
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

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "openFile")
        } catch (e: SecurityException) {
            Timber.e("[SECURE FS BRIDGE] Permission denied: ${e.message}")
            return null
        }

        return actualBridge.openFile(verifiedPluginId, path, mode)
    }

    @RequiresPluginPermission("FILE_WRITE")
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

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "deleteFile")
        } catch (e: SecurityException) {
            Timber.e("[SECURE FS BRIDGE] Permission denied: ${e.message}")
            return false
        }

        return actualBridge.deleteFile(verifiedPluginId, path)
    }
    
    @RequiresPluginPermission("FILE_READ")
    override fun fileExists(pluginId: String, path: String): Boolean {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return false
        }

        // Phase 5: Permission pre-check (file existence check requires read permission)
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "readFile")
        } catch (e: SecurityException) {
            Timber.e("[SECURE FS BRIDGE] Permission denied: ${e.message}")
            return false
        }

        return actualBridge.fileExists(verifiedPluginId, path)
    }

    @RequiresPluginPermission("FILE_READ")
    override fun listFiles(pluginId: String, path: String): Array<String> {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return emptyArray()
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "listFiles")
        } catch (e: SecurityException) {
            Timber.e("[SECURE FS BRIDGE] Permission denied: ${e.message}")
            return emptyArray()
        }

        return actualBridge.listFiles(verifiedPluginId, path)
    }

    @RequiresPluginPermission("FILE_READ")
    override fun getFileSize(pluginId: String, path: String): Long {
        val verifiedPluginId = verifyCallerIdentity()

        if (pluginId != verifiedPluginId) {
            Timber.e("[SECURE FS BRIDGE] SPOOFING BLOCKED: claimed='$pluginId' verified='$verifiedPluginId'")
            return -1
        }

        // Phase 5: Permission pre-check
        try {
            permissionPreChecker.preCheck(verifiedPluginId, "getFileSize")
        } catch (e: SecurityException) {
            Timber.e("[SECURE FS BRIDGE] Permission denied: ${e.message}")
            return -1
        }

        return actualBridge.getFileSize(verifiedPluginId, path)
    }
}
