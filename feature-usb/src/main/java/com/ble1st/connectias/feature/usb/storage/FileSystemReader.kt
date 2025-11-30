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
 * Uses OS-level file system queries via /proc/mounts for reliable detection.
 */
@Singleton
class FileSystemReader @Inject constructor() {
    
    /**
     * Detects the file system type of a mounted drive.
     * Uses /proc/mounts to query the actual file system type from the OS.
     */
    suspend fun detectFileSystem(mountPoint: String): FileSystem = withContext(Dispatchers.IO) {
        try {
            Timber.d("Detecting file system at mount point: $mountPoint")
            
            val rootDir = File(mountPoint)
            if (!rootDir.exists() || !rootDir.isDirectory) {
                Timber.w("Mount point does not exist or is not a directory: $mountPoint")
                return@withContext FileSystem.UNKNOWN
            }
            
            // Query file system type from /proc/mounts
            val fileSystemType = getFileSystemFromMounts(mountPoint)
            if (fileSystemType != FileSystem.UNKNOWN) {
                Timber.d("File system detected as $fileSystemType from /proc/mounts")
                return@withContext fileSystemType
            }
            
            Timber.d("File system type could not be determined from /proc/mounts, defaulting to UNKNOWN")
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
    
    /**
     * Reads file system type from /proc/mounts.
     * Format: device mount_point file_system_type options dump pass
     * Example: /dev/sr0 /media/cdrom iso9660 ro,noexec,nosuid,nodev 0 0
     * 
     * @param mountPoint The mount point path to look up
     * @return The detected FileSystem type, or UNKNOWN if not found or not recognized
     */
    private fun getFileSystemFromMounts(mountPoint: String): FileSystem {
        return try {
            val mountsFile = File("/proc/mounts")
            if (!mountsFile.exists() || !mountsFile.canRead()) {
                Timber.w("Cannot read /proc/mounts")
                return FileSystem.UNKNOWN
            }
            
            // Normalize mount point path (handle trailing slashes)
            val normalizedMountPoint = mountPoint.trimEnd('/')
            
            mountsFile.readLines().forEach { line ->
                val parts = line.split("\\s+".toRegex())
                // Format: device mount_point file_system_type options dump pass
                // Need at least 3 fields: device, mount_point, file_system_type
                if (parts.size >= 3) {
                    val lineMountPoint = parts[1]
                    val fileSystemType = parts[2]
                    
                    // Check if mount point matches (handle both with and without trailing slash)
                    if (lineMountPoint == normalizedMountPoint || 
                        lineMountPoint == "$normalizedMountPoint/" ||
                        "$lineMountPoint/" == normalizedMountPoint) {
                        Timber.d("Found mount entry: device=${parts[0]}, mount=$lineMountPoint, fs=$fileSystemType")
                        return when (fileSystemType.lowercase()) {
                            "iso9660" -> FileSystem.ISO9660
                            "udf" -> FileSystem.UDF
                            else -> {
                                Timber.d("Unrecognized file system type: $fileSystemType")
                                FileSystem.UNKNOWN
                            }
                        }
                    }
                }
            }
            
            Timber.d("Mount point not found in /proc/mounts: $mountPoint")
            FileSystem.UNKNOWN
        } catch (e: Exception) {
            Timber.e(e, "Error reading /proc/mounts")
            FileSystem.UNKNOWN
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
