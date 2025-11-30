package com.ble1st.connectias.feature.usb.storage

import com.ble1st.connectias.feature.usb.models.FileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads file system information from mounted optical drives.
 */
@Singleton
class FileSystemReader @Inject constructor() {
    
    /**
     * Detects the file system type of a mounted drive.
     */
    suspend fun detectFileSystem(mountPoint: String): FileSystem = withContext(Dispatchers.IO) {
        try {
            Timber.d("Detecting file system at mount point: $mountPoint")
            
            val rootDir = File(mountPoint)
            if (!rootDir.exists() || !rootDir.isDirectory) {
                Timber.w("Mount point does not exist or is not a directory: $mountPoint")
                return@withContext FileSystem.UNKNOWN
            }
            
            // Check for ISO9660 indicators
            if (hasIso9660Indicators(rootDir)) {
                Timber.d("File system detected as ISO9660")
                return@withContext FileSystem.ISO9660
            }
            
            // Check for UDF indicators
            if (hasUdfIndicators(rootDir)) {
                Timber.d("File system detected as UDF")
                return@withContext FileSystem.UDF
            }
            
            Timber.d("File system type could not be determined, defaulting to UNKNOWN")
            FileSystem.UNKNOWN
        } catch (e: Exception) {
            Timber.e(e, "Error detecting file system")
            FileSystem.UNKNOWN
        }
    }
    
    /**
     * Lists files in a directory.
     */
    suspend fun listFiles(path: String): List<FileInfo> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Listing files in directory: $path")
            val dir = File(path)
            
            if (!dir.exists() || !dir.isDirectory) {
                Timber.w("Path does not exist or is not a directory: $path")
                return@withContext emptyList()
            }
            
            val files = dir.listFiles()?.map { file ->
                FileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified()
                )
            } ?: emptyList()
            
            Timber.d("Found ${files.size} items in directory")
            files
        } catch (e: Exception) {
            Timber.e(e, "Error listing files")
            emptyList()
        }
    }
    
    private fun hasIso9660Indicators(dir: File): Boolean {
        // ISO9660 typically has specific directory structure
        // Check for common ISO9660 characteristics
        return try {
            val files = dir.listFiles()
            files != null && files.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    private fun hasUdfIndicators(dir: File): Boolean {
        // UDF (Universal Disk Format) used for DVDs
        // Check for VIDEO_TS or AUDIO_TS directories (DVD indicators)
        return try {
            val videoTs = File(dir, "VIDEO_TS")
            val audioTs = File(dir, "AUDIO_TS")
            videoTs.exists() || audioTs.exists()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * File information.
 */
data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)
