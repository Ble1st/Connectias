package com.ble1st.connectias.feature.usb.browser.models

import kotlinx.serialization.Serializable

/**
 * Represents a file or directory on a USB storage device.
 */
@Serializable
data class UsbFileEntry(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isHidden: Boolean = false,
    val extension: String? = null,
    val mimeType: String? = null
) {
    val formattedSize: String
        get() = formatFileSize(size)

    val isImage: Boolean
        get() = extension?.lowercase() in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    val isVideo: Boolean
        get() = extension?.lowercase() in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv")

    val isAudio: Boolean
        get() = extension?.lowercase() in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a")

    val isDocument: Boolean
        get() = extension?.lowercase() in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")

    val isArchive: Boolean
        get() = extension?.lowercase() in listOf("zip", "rar", "7z", "tar", "gz")

    companion object {
        fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(
                "%.1f %s",
                size / Math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }
}

/**
 * Represents a USB storage device.
 */
@Serializable
data class UsbStorageDevice(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
    val capacity: Long,
    val usedSpace: Long,
    val fileSystem: String?,
    val isConnected: Boolean = true,
    val isMounted: Boolean = false
) {
    val freeSpace: Long
        get() = capacity - usedSpace

    val usagePercentage: Float
        get() = if (capacity > 0) (usedSpace.toFloat() / capacity) * 100 else 0f

    val formattedCapacity: String
        get() = UsbFileEntry.formatFileSize(capacity)

    val formattedFreeSpace: String
        get() = UsbFileEntry.formatFileSize(freeSpace)
}

/**
 * Result of a file operation.
 */
sealed class FileOperationResult {
    data class Success(val message: String) : FileOperationResult()
    data class Error(val message: String, val exception: Throwable? = null) : FileOperationResult()
    data class Progress(val bytesTransferred: Long, val totalBytes: Long) : FileOperationResult() {
        val percentage: Float
            get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes) * 100 else 0f
    }
}

/**
 * File preview data.
 */
sealed class FilePreview {
    data class Image(val data: ByteArray) : FilePreview() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }
    data class Text(val content: String, val encoding: String = "UTF-8") : FilePreview()
    data class Audio(val duration: Long, val bitrate: Int?, val sampleRate: Int?) : FilePreview()
    data class Video(val duration: Long, val width: Int, val height: Int, val codec: String?) : FilePreview()
    data class Unsupported(val reason: String) : FilePreview()
}

/**
 * Navigation state for the file browser.
 */
data class BrowserNavigationState(
    val currentPath: String = "/",
    val pathStack: List<String> = listOf("/"),
    val selectedFiles: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val showHiddenFiles: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST
)

/**
 * Sort order for files.
 */
enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    SIZE_ASC,
    SIZE_DESC,
    DATE_ASC,
    DATE_DESC,
    TYPE_ASC,
    TYPE_DESC
}

/**
 * View mode for the file browser.
 */
enum class ViewMode {
    LIST,
    GRID
}
