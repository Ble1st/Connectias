package com.ble1st.connectias.feature.usb.browser

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.usb.browser.models.FileOperationResult
import com.ble1st.connectias.feature.usb.browser.models.FilePreview
import com.ble1st.connectias.feature.usb.browser.models.SortOrder
import com.ble1st.connectias.feature.usb.browser.models.UsbFileEntry
import com.ble1st.connectias.feature.usb.browser.models.UsbStorageDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.fs.FileSystem
import me.jahnen.libaums.core.fs.UsbFile
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for USB Mass Storage file browsing functionality.
 *
 * Features:
 * - Device detection and mounting
 * - Directory listing
 * - File copying to internal storage
 * - File preview
 * - Search functionality
 */
@Singleton
class UsbStorageBrowserProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDevices = MutableStateFlow<List<UsbStorageDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbStorageDevice>> = _connectedDevices.asStateFlow()

    private val _currentDevice = MutableStateFlow<UsbStorageDevice?>(null)
    val currentDevice: StateFlow<UsbStorageDevice?> = _currentDevice.asStateFlow()

    private var massStorageDevice: UsbMassStorageDevice? = null
    private var fileSystem: FileSystem? = null
    private var rootDirectory: UsbFile? = null

    /**
     * Scans for connected USB mass storage devices.
     */
    suspend fun scanDevices(): List<UsbStorageDevice> = withContext(Dispatchers.IO) {
        try {
            val devices = UsbMassStorageDevice.getMassStorageDevices(context)
            val storageDevices = devices.mapNotNull { device ->
                try {
                    val usbDevice = device.usbDevice
                    UsbStorageDevice(
                        id = "${usbDevice.vendorId}-${usbDevice.productId}-${usbDevice.deviceId}",
                        name = usbDevice.productName ?: "USB Storage",
                        vendorId = usbDevice.vendorId,
                        productId = usbDevice.productId,
                        serialNumber = usbDevice.serialNumber,
                        capacity = 0L, // Will be updated after mounting
                        usedSpace = 0L,
                        fileSystem = null,
                        isConnected = true,
                        isMounted = false
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error getting device info")
                    null
                }
            }

            _connectedDevices.update { storageDevices }
            storageDevices
        } catch (e: Exception) {
            Timber.e(e, "Error scanning for USB devices")
            emptyList()
        }
    }

    /**
     * Mounts a USB storage device.
     */
    suspend fun mountDevice(device: UsbStorageDevice): Result<UsbStorageDevice> = withContext(Dispatchers.IO) {
        try {
            val devices = UsbMassStorageDevice.getMassStorageDevices(context)
            val targetDevice = devices.find { 
                it.usbDevice.vendorId == device.vendorId && 
                it.usbDevice.productId == device.productId 
            } ?: return@withContext Result.failure(Exception("Device not found"))

            // Check permission
            if (!usbManager.hasPermission(targetDevice.usbDevice)) {
                return@withContext Result.failure(Exception("USB permission not granted"))
            }

            targetDevice.init()
            massStorageDevice = targetDevice

            if (targetDevice.partitions.isEmpty()) {
                return@withContext Result.failure(Exception("No partitions found"))
            }

            val partition = targetDevice.partitions.first()
            fileSystem = partition.fileSystem
            rootDirectory = fileSystem?.rootDirectory

            val fs = fileSystem!!
            val updatedDevice = device.copy(
                capacity = fs.capacity,
                usedSpace = fs.capacity - fs.freeSpace,
                fileSystem = fs.volumeLabel ?: "Unknown",
                isMounted = true
            )

            _currentDevice.update { updatedDevice }
            _connectedDevices.update { devices -> 
                devices.map { if (it.id == device.id) updatedDevice else it }
            }

            Result.success(updatedDevice)
        } catch (e: Exception) {
            Timber.e(e, "Error mounting device")
            Result.failure(e)
        }
    }

    /**
     * Unmounts the current device.
     */
    fun unmount() {
        try {
            massStorageDevice?.close()
            massStorageDevice = null
            fileSystem = null
            rootDirectory = null
            
            _currentDevice.value?.let { device ->
                _connectedDevices.update { devices ->
                    devices.map { 
                        if (it.id == device.id) it.copy(isMounted = false) 
                        else it 
                    }
                }
            }
            _currentDevice.update { null }
        } catch (e: Exception) {
            Timber.e(e, "Error unmounting device")
        }
    }

    /**
     * Lists files in a directory.
     */
    suspend fun listDirectory(
        path: String,
        sortOrder: SortOrder = SortOrder.NAME_ASC,
        showHidden: Boolean = false
    ): Result<List<UsbFileEntry>> = withContext(Dispatchers.IO) {
        try {
            val root = rootDirectory ?: return@withContext Result.failure(Exception("No device mounted"))
            
            val targetDir = if (path == "/") {
                root
            } else {
                navigateToPath(root, path) ?: return@withContext Result.failure(Exception("Path not found"))
            }

            val entries = targetDir.listFiles().mapNotNull { file ->
                if (!showHidden && file.name.startsWith(".")) return@mapNotNull null
                
                UsbFileEntry(
                    name = file.name,
                    path = if (path == "/") "/${file.name}" else "$path/${file.name}",
                    size = if (file.isDirectory) 0L else file.length,
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory,
                    isHidden = file.name.startsWith("."),
                    extension = if (!file.isDirectory) file.name.substringAfterLast(".", "") else null,
                    mimeType = getMimeType(file.name)
                )
            }

            val sorted = sortFiles(entries, sortOrder)
            Result.success(sorted)
        } catch (e: Exception) {
            Timber.e(e, "Error listing directory: $path")
            Result.failure(e)
        }
    }

    /**
     * Copies a file from USB to internal storage.
     */
    fun copyToInternal(
        sourcePath: String,
        destinationDir: File
    ): Flow<FileOperationResult> = flow {
        try {
            val root = rootDirectory ?: throw Exception("No device mounted")
            val sourceFile = navigateToPath(root, sourcePath) 
                ?: throw Exception("Source file not found")

            if (sourceFile.isDirectory) {
                throw Exception("Cannot copy directories")
            }

            val destinationFile = File(destinationDir, sourceFile.name)
            val totalSize = sourceFile.length

            var bytesWritten = 0L
            val buffer = ByteArray(8192)
            val byteBuffer = java.nio.ByteBuffer.wrap(buffer)

            FileOutputStream(destinationFile).use { outputStream ->
                var offset = 0L
                while (offset < totalSize) {
                    val remaining = totalSize - offset
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    
                    byteBuffer.clear()
                    byteBuffer.limit(toRead)
                    
                    sourceFile.read(offset, byteBuffer)
                    
                    outputStream.write(buffer, 0, toRead)
                    offset += toRead
                    bytesWritten += toRead
                    
                    emit(FileOperationResult.Progress(bytesWritten, totalSize))
                }
            }

            emit(FileOperationResult.Success("File copied successfully"))
        } catch (e: Exception) {
            Timber.e(e, "Error copying file")
            emit(FileOperationResult.Error("Failed to copy file: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Gets a preview for a file.
     */
    suspend fun getFilePreview(path: String): Result<FilePreview> = withContext(Dispatchers.IO) {
        try {
            val root = rootDirectory ?: return@withContext Result.failure(Exception("No device mounted"))
            val file = navigateToPath(root, path) 
                ?: return@withContext Result.failure(Exception("File not found"))

            if (file.isDirectory) {
                return@withContext Result.failure(Exception("Cannot preview directories"))
            }

            val extension = file.name.substringAfterLast(".", "").lowercase()
            
            when {
                extension in listOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java") -> {
                    val buffer = ByteArray(minOf(file.length.toInt(), 10000))
                    file.read(0, java.nio.ByteBuffer.wrap(buffer))
                    val content = String(buffer, Charsets.UTF_8)
                    Result.success(FilePreview.Text(content))
                }
                extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> {
                    if (file.length > 10 * 1024 * 1024) { // 10 MB limit for preview
                        return@withContext Result.success(
                            FilePreview.Unsupported("Image too large for preview")
                        )
                    }
                    val buffer = ByteArray(file.length.toInt())
                    file.read(0, java.nio.ByteBuffer.wrap(buffer))
                    Result.success(FilePreview.Image(buffer))
                }
                else -> {
                    Result.success(FilePreview.Unsupported("Preview not available for this file type"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting file preview")
            Result.failure(e)
        }
    }

    /**
     * Searches for files matching a pattern.
     */
    suspend fun searchFiles(
        pattern: String,
        startPath: String = "/",
        recursive: Boolean = true
    ): Result<List<UsbFileEntry>> = withContext(Dispatchers.IO) {
        try {
            val root = rootDirectory ?: return@withContext Result.failure(Exception("No device mounted"))
            val startDir = if (startPath == "/") root else navigateToPath(root, startPath)
                ?: return@withContext Result.failure(Exception("Start path not found"))

            val results = mutableListOf<UsbFileEntry>()
            searchRecursive(startDir, startPath, pattern.lowercase(), recursive, results)
            
            Result.success(results)
        } catch (e: Exception) {
            Timber.e(e, "Error searching files")
            Result.failure(e)
        }
    }

    /**
     * Gets file/directory info.
     */
    suspend fun getFileInfo(path: String): Result<UsbFileEntry> = withContext(Dispatchers.IO) {
        try {
            val root = rootDirectory ?: return@withContext Result.failure(Exception("No device mounted"))
            val file = navigateToPath(root, path) 
                ?: return@withContext Result.failure(Exception("File not found"))

            val entry = UsbFileEntry(
                name = file.name,
                path = path,
                size = if (file.isDirectory) calculateDirSize(file) else file.length,
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                isHidden = file.name.startsWith("."),
                extension = if (!file.isDirectory) file.name.substringAfterLast(".", "") else null,
                mimeType = getMimeType(file.name)
            )

            Result.success(entry)
        } catch (e: Exception) {
            Timber.e(e, "Error getting file info")
            Result.failure(e)
        }
    }

    // Helper methods

    private fun navigateToPath(root: UsbFile, path: String): UsbFile? {
        if (path == "/" || path.isEmpty()) return root
        
        val parts = path.trim('/').split("/")
        var current = root
        
        for (part in parts) {
            if (part.isEmpty()) continue
            current = current.listFiles().find { it.name == part } ?: return null
        }
        
        return current
    }

    private fun sortFiles(files: List<UsbFileEntry>, sortOrder: SortOrder): List<UsbFileEntry> {
        // Directories first, then sort within each group
        val directories = files.filter { it.isDirectory }
        val regularFiles = files.filter { !it.isDirectory }

        val sortedDirs = when (sortOrder) {
            SortOrder.NAME_ASC -> directories.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> directories.sortedByDescending { it.name.lowercase() }
            SortOrder.DATE_ASC -> directories.sortedBy { it.lastModified }
            SortOrder.DATE_DESC -> directories.sortedByDescending { it.lastModified }
            else -> directories.sortedBy { it.name.lowercase() }
        }

        val sortedFiles = when (sortOrder) {
            SortOrder.NAME_ASC -> regularFiles.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> regularFiles.sortedByDescending { it.name.lowercase() }
            SortOrder.SIZE_ASC -> regularFiles.sortedBy { it.size }
            SortOrder.SIZE_DESC -> regularFiles.sortedByDescending { it.size }
            SortOrder.DATE_ASC -> regularFiles.sortedBy { it.lastModified }
            SortOrder.DATE_DESC -> regularFiles.sortedByDescending { it.lastModified }
            SortOrder.TYPE_ASC -> regularFiles.sortedBy { it.extension?.lowercase() ?: "" }
            SortOrder.TYPE_DESC -> regularFiles.sortedByDescending { it.extension?.lowercase() ?: "" }
        }

        return sortedDirs + sortedFiles
    }

    private fun searchRecursive(
        dir: UsbFile,
        currentPath: String,
        pattern: String,
        recursive: Boolean,
        results: MutableList<UsbFileEntry>
    ) {
        try {
            for (file in dir.listFiles()) {
                val filePath = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                
                if (file.name.lowercase().contains(pattern)) {
                    results.add(
                        UsbFileEntry(
                            name = file.name,
                            path = filePath,
                            size = if (file.isDirectory) 0L else file.length,
                            lastModified = file.lastModified(),
                            isDirectory = file.isDirectory,
                            isHidden = file.name.startsWith("."),
                            extension = if (!file.isDirectory) file.name.substringAfterLast(".", "") else null,
                            mimeType = getMimeType(file.name)
                        )
                    )
                }

                if (recursive && file.isDirectory) {
                    searchRecursive(file, filePath, pattern, true, results)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error searching in $currentPath")
        }
    }

    private fun calculateDirSize(dir: UsbFile): Long {
        var size = 0L
        try {
            for (file in dir.listFiles()) {
                size += if (file.isDirectory) calculateDirSize(file) else file.length
            }
        } catch (e: Exception) {
            Timber.w(e, "Error calculating directory size")
        }
        return size
    }

    private fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return when (extension) {
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            else -> null
        }
    }
}
