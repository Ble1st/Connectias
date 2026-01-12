package com.ble1st.connectias.hardware

import android.os.Parcel
import android.os.Parcelable
import android.os.ParcelFileDescriptor

/**
 * Parcelable response wrapper for hardware bridge IPC communication
 * 
 * This class encapsulates responses from hardware bridge operations,
 * supporting multiple data transport mechanisms:
 * - Small data: Direct byte array
 * - Large data/streams: ParcelFileDescriptor
 * - Metadata: Key-value pairs for additional info
 * 
 * USAGE EXAMPLES:
 * 
 * Success with data:
 * ```kotlin
 * HardwareResponseParcel.success(
 *     data = imageBytes,
 *     metadata = mapOf("format" to "jpeg", "size" to "1920x1080")
 * )
 * ```
 * 
 * Success with file descriptor:
 * ```kotlin
 * val socketFd = ParcelFileDescriptor.fromSocket(socket)
 * HardwareResponseParcel.success(fileDescriptor = socketFd)
 * ```
 * 
 * Failure:
 * ```kotlin
 * HardwareResponseParcel.failure("Permission denied: CAMERA")
 * ```
 * 
 * @property success True if operation succeeded, false otherwise
 * @property errorMessage Error message if success is false, null otherwise
 * @property data Small response data (< 1MB recommended), null if using fileDescriptor
 * @property fileDescriptor File descriptor for large data, sockets, or shared memory
 * @property metadata Additional information about the response (format, size, etc.)
 * 
 * @since 2.0.0 (isolated process support)
 */
data class HardwareResponseParcel(
    val success: Boolean,
    val errorMessage: String?,
    val data: ByteArray?,
    val fileDescriptor: ParcelFileDescriptor?,
    val metadata: Map<String, String>?
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        success = parcel.readByte() != 0.toByte(),
        errorMessage = parcel.readString(),
        data = parcel.createByteArray(),
        fileDescriptor = parcel.readParcelable(ParcelFileDescriptor::class.java.classLoader),
        metadata = parcel.readHashMap(String::class.java.classLoader) as? Map<String, String>
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (success) 1 else 0)
        parcel.writeString(errorMessage)
        parcel.writeByteArray(data)
        parcel.writeParcelable(fileDescriptor, flags)
        parcel.writeMap(metadata)
    }
    
    override fun describeContents(): Int {
        return if (fileDescriptor != null) {
            Parcelable.CONTENTS_FILE_DESCRIPTOR
        } else {
            0
        }
    }
    
    /**
     * Helper to get data size for logging/debugging
     */
    fun getDataSize(): Long {
        return when {
            data != null -> data.size.toLong()
            fileDescriptor != null -> {
                try {
                    fileDescriptor.statSize
                } catch (e: Exception) {
                    -1L
                }
            }
            else -> 0L
        }
    }
    
    /**
     * Check if response contains a file descriptor that needs closing
     */
    fun hasFileDescriptor(): Boolean = fileDescriptor != null
    
    /**
     * Close file descriptor if present (should be called by consumer)
     */
    fun closeFileDescriptor() {
        try {
            fileDescriptor?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HardwareResponseParcel

        if (success != other.success) return false
        if (errorMessage != other.errorMessage) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false
        if (metadata != other.metadata) return false
        // Don't compare fileDescriptor as it's not reliably comparable

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + (metadata?.hashCode() ?: 0)
        return result
    }
    
    companion object CREATOR : Parcelable.Creator<HardwareResponseParcel> {
        override fun createFromParcel(parcel: Parcel): HardwareResponseParcel {
            return HardwareResponseParcel(parcel)
        }
        
        override fun newArray(size: Int): Array<HardwareResponseParcel?> {
            return arrayOfNulls(size)
        }
        
        /**
         * Create success response with optional data
         * 
         * @param data Small response data (< 1MB recommended)
         * @param fileDescriptor File descriptor for large data/streams
         * @param metadata Additional key-value information
         */
        fun success(
            data: ByteArray? = null,
            fileDescriptor: ParcelFileDescriptor? = null,
            metadata: Map<String, String>? = null
        ): HardwareResponseParcel {
            return HardwareResponseParcel(
                success = true,
                errorMessage = null,
                data = data,
                fileDescriptor = fileDescriptor,
                metadata = metadata
            )
        }
        
        /**
         * Create failure response with error message
         * 
         * @param errorMessage Description of what went wrong
         */
        fun failure(errorMessage: String): HardwareResponseParcel {
            return HardwareResponseParcel(
                success = false,
                errorMessage = errorMessage,
                data = null,
                fileDescriptor = null,
                metadata = null
            )
        }
        
        /**
         * Create failure response from exception
         * 
         * @param e Exception that caused the failure
         */
        fun failure(e: Exception): HardwareResponseParcel {
            return failure(e.message ?: e.javaClass.simpleName)
        }
    }
}
