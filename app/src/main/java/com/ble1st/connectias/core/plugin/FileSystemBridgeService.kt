package com.ble1st.connectias.core.plugin

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.plugin.IFileSystemBridge
import com.ble1st.connectias.plugin.security.PluginIdentitySession
import android.system.ErrnoException
import android.system.Os
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * File System Bridge Service
 * Provides secure file system access for plugins running in isolated process
 */
class FileSystemBridgeService : Service() {
    
    private val binder = object : IFileSystemBridge.Stub() {
        
        override fun createFile(pluginId: String, path: String, mode: Int): ParcelFileDescriptor? {
            return try {
                // Validate plugin ID
                if (!isValidPluginId(pluginId)) {
                    Timber.e("[FS_BRIDGE] Invalid plugin ID: $pluginId")
                    return null
                }
                
                // Construct full path within plugin's sandbox directory
                val pluginDir = File(filesDir, "plugin_files/$pluginId")
                if (!pluginDir.exists()) {
                    pluginDir.mkdirs()
                }
                
                val file = File(pluginDir, path)
                
                // Security check: Ensure file is within plugin directory
                if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    Timber.e("[FS_BRIDGE] Path traversal attempt: $path")
                    return null
                }
                
                // Create file with specified mode
                val fd = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
                )
                
                Timber.d("[FS_BRIDGE] Created file for plugin $pluginId: $path")
                fd
            } catch (e: Exception) {
                Timber.e(e, "[FS_BRIDGE] Failed to create file for plugin $pluginId: $path")
                null
            }
        }
        
        override fun openFile(pluginId: String, path: String, mode: Int): ParcelFileDescriptor? {
            return try {
                // Validate plugin ID
                if (!isValidPluginId(pluginId)) {
                    Timber.e("[FS_BRIDGE] Invalid plugin ID: $pluginId")
                    return null
                }
                
                // Construct full path within plugin's sandbox directory
                val pluginDir = File(filesDir, "plugin_files/$pluginId")
                val file = File(pluginDir, path)
                
                // Security check: Ensure file is within plugin directory
                if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    Timber.e("[FS_BRIDGE] Path traversal attempt: $path")
                    return null
                }
                
                // Check if file exists
                if (!file.exists()) {
                    return null
                }
                
                // Open file
                val fd = ParcelFileDescriptor.open(file, mode)
                
                Timber.d("[FS_BRIDGE] Opened file for plugin $pluginId: $path")
                fd
            } catch (e: FileNotFoundException) {
                Timber.w("[FS_BRIDGE] File not found for plugin $pluginId: $path")
                null
            } catch (e: Exception) {
                Timber.e(e, "[FS_BRIDGE] Failed to open file for plugin $pluginId: $path")
                null
            }
        }
        
        override fun deleteFile(pluginId: String, path: String): Boolean {
            return try {
                // Validate plugin ID
                if (!isValidPluginId(pluginId)) {
                    Timber.e("[FS_BRIDGE] Invalid plugin ID: $pluginId")
                    return false
                }
                
                // Construct full path within plugin's sandbox directory
                val pluginDir = File(filesDir, "plugin_files/$pluginId")
                val file = File(pluginDir, path)
                
                // Security check: Ensure file is within plugin directory
                if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    Timber.e("[FS_BRIDGE] Path traversal attempt: $path")
                    return false
                }
                
                // Delete file
                val deleted = file.delete()
                
                if (deleted) {
                    Timber.d("[FS_BRIDGE] Deleted file for plugin $pluginId: $path")
                } else {
                    Timber.w("[FS_BRIDGE] Failed to delete file for plugin $pluginId: $path")
                }
                
                deleted
            } catch (e: Exception) {
                Timber.e(e, "[FS_BRIDGE] Failed to delete file for plugin $pluginId: $path")
                false
            }
        }
        
        override fun fileExists(pluginId: String, path: String): Boolean {
            return try {
                // Validate plugin ID
                if (!isValidPluginId(pluginId)) {
                    Timber.e("[FS_BRIDGE] Invalid plugin ID: $pluginId")
                    return false
                }
                
                // Construct full path within plugin's sandbox directory
                val pluginDir = File(filesDir, "plugin_files/$pluginId")
                val file = File(pluginDir, path)
                
                // Security check: Ensure file is within plugin directory
                if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    Timber.e("[FS_BRIDGE] Path traversal attempt: $path")
                    return false
                }
                
                file.exists()
            } catch (e: Exception) {
                Timber.e(e, "[FS_BRIDGE] Failed to check file existence for plugin $pluginId: $path")
                false
            }
        }
        
        override fun listFiles(pluginId: String, path: String): Array<String> {
            return try {
                // Validate plugin ID
                if (!isValidPluginId(pluginId)) {
                    Timber.e("[FS_BRIDGE] Invalid plugin ID: $pluginId")
                    return emptyArray()
                }
                
                // Construct full path within plugin's sandbox directory
                val pluginDir = File(filesDir, "plugin_files/$pluginId")
                val dir = File(pluginDir, path)
                
                // Security check: Ensure directory is within plugin directory
                if (!dir.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    Timber.e("[FS_BRIDGE] Path traversal attempt: $path")
                    return emptyArray()
                }
                
                // List files
                if (dir.exists() && dir.isDirectory) {
                    dir.list() ?: emptyArray()
                } else {
                    emptyArray()
                }
            } catch (e: Exception) {
                Timber.e(e, "[FS_BRIDGE] Failed to list files for plugin $pluginId: $path")
                emptyArray()
            }
        }
        
        override fun getFileSize(pluginId: String, path: String): Long {
            return try {
                // Validate plugin ID
                if (!isValidPluginId(pluginId)) {
                    Timber.e("[FS_BRIDGE] Invalid plugin ID: $pluginId")
                    return -1
                }
                
                // Construct full path within plugin's sandbox directory
                val pluginDir = File(filesDir, "plugin_files/$pluginId")
                val file = File(pluginDir, path)
                
                // Security check: Ensure file is within plugin directory
                if (!file.canonicalPath.startsWith(pluginDir.canonicalPath)) {
                    Timber.e("[FS_BRIDGE] Path traversal attempt: $path")
                    return -1
                }
                
                if (file.exists()) {
                    file.length()
                } else {
                    -1
                }
            } catch (e: Exception) {
                Timber.e(e, "[FS_BRIDGE] Failed to get file size for plugin $pluginId: $path")
                -1
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * Validate plugin ID against known loaded plugins
     * SECURITY: Verifies caller identity using PluginIdentitySession
     */
    private fun isValidPluginId(pluginId: String): Boolean {
        // 1. Format validation
        if (!pluginId.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
            Timber.e("[FS_BRIDGE] Invalid plugin ID format: $pluginId")
            return false
        }
        
        // 2. Verify against PluginIdentitySession to prevent spoofing
        val verifiedPluginId = PluginIdentitySession.verifyPluginIdentity()
        if (verifiedPluginId == null) {
            Timber.e("[FS_BRIDGE] No verified plugin identity for caller")
            return false
        }
        
        if (verifiedPluginId != pluginId) {
            Timber.e("[FS_BRIDGE] SPOOFING ATTEMPT: claimed='$pluginId' verified='$verifiedPluginId'")
            return false
        }
        
        return true
    }
}
