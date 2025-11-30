package com.ble1st.connectias.feature.deviceinfo.storage

import android.content.Context
import android.os.StatFs
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for storage analysis.
 */
@Singleton
class StorageAnalyzerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Gets storage information.
     */
    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val internalStorage = getStorageStats(Environment.getDataDirectory())
            val externalStorage = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                // Safe way to get external storage volume stats without dangerous permissions
                context.getExternalFilesDir(null)?.let { getStorageStats(it) }
            } else {
                null
            }

            StorageInfo(
                internalStorage = internalStorage,
                externalStorage = externalStorage,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get storage info")
            StorageInfo.default()
        }
    }

    /**
     * Gets storage statistics for a directory.
     */
    private fun getStorageStats(directory: File): StorageStats {
        val statFs = StatFs(directory.absolutePath)
        val blockSize = statFs.blockSizeLong
        val totalBlocks = statFs.blockCountLong
        val availableBlocks = statFs.availableBlocksLong
        val usedBlocks = totalBlocks - availableBlocks

        return StorageStats(
            totalBytes = totalBlocks * blockSize,
            freeBytes = availableBlocks * blockSize,
            usedBytes = usedBlocks * blockSize,
            path = directory.absolutePath
        )
    }

    /**
     * Finds large files in a directory.
     * 
     * @param directory Directory to scan (defaults to external files dir)
     * @param minSizeBytes Minimum file size in bytes
     * @param maxResults Maximum number of results
     */
    suspend fun findLargeFiles(
        directory: File? = null,
        minSizeBytes: Long = 10 * 1024 * 1024, // 10 MB
        maxResults: Int = 50
    ): List<LargeFile> = withContext(Dispatchers.IO) {
        val largeFiles = mutableListOf<LargeFile>()
        val targetDir = directory ?: context.getExternalFilesDir(null)
        
        if (targetDir != null) {
            try {
                scanDirectory(targetDir, minSizeBytes, largeFiles, maxResults)
            } catch (e: Exception) {
                Timber.e(e, "Failed to scan directory: ${targetDir.absolutePath}")
            }
        }
        
        largeFiles.sortedByDescending { it.size }.take(maxResults)
    }

    /**
     * Recursively scans directory for large files.
     */
    private fun scanDirectory(
        directory: File,
        minSizeBytes: Long,
        largeFiles: MutableList<LargeFile>,
        maxResults: Int
    ) {
        if (largeFiles.size >= maxResults) return
        
        try {
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                if (largeFiles.size >= maxResults) break
                
                if (file.isDirectory) {
                    // Skip hidden and system directories
                    if (!file.name.startsWith(".") && file.canRead()) {
                        scanDirectory(file, minSizeBytes, largeFiles, maxResults)
                    }
                } else if (file.isFile && file.length() >= minSizeBytes) {
                    largeFiles.add(
                        LargeFile(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.v(e, "Failed to scan directory: ${directory.absolutePath}")
        }
    }

    /**
     * Finds duplicate files by content hash (simplified - would need actual hashing in production).
     */
    suspend fun findDuplicateFiles(
        directory: File? = null,
        maxResults: Int = 100
    ): List<DuplicateFileGroup> = withContext(Dispatchers.Default) {
        // Simplified implementation - in production would hash file contents
        emptyList()
    }
}

/**
 * Storage information.
 */
data class StorageInfo(
    val internalStorage: StorageStats,
    val externalStorage: StorageStats?,
    val timestamp: Long
) {
    companion object {
        fun default() = StorageInfo(
            internalStorage = StorageStats(0, 0, 0, ""),
            externalStorage = null,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Storage statistics.
 */
data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val path: String
)

/**
 * Large file information.
 */
data class LargeFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long
)

/**
 * Duplicate file group.
 */
data class DuplicateFileGroup(
    val files: List<LargeFile>,
    val hash: String
)

