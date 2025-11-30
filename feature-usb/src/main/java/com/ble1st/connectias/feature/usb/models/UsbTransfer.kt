package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USB transfer result.
 */
@Parcelize
data class UsbTransfer(
    val type: TransferType,
    val endpoint: Int,
    val data: ByteArray,
    val bytesTransferred: Int,
    val status: TransferStatus
) : Parcelable {
    
    override fun toString(): String {
        return "UsbTransfer(type=$type, endpoint=0x%02X, bytesTransferred=$bytesTransferred, status=$status, data=${data.contentToString()})".format(endpoint)
    }
    enum class TransferType {
        BULK,
        INTERRUPT,
        CONTROL,
        ISOCHRONOUS
    }
    
    enum class TransferStatus {
        SUCCESS,
        ERROR,
        TIMEOUT,
        STALL,
        NO_DEVICE
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as UsbTransfer
        
        if (type != other.type) return false
        if (endpoint != other.endpoint) return false
        if (!data.contentEquals(other.data)) return false
        if (bytesTransferred != other.bytesTransferred) return false
        if (status != other.status) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + endpoint
        result = 31 * result + data.contentHashCode()
        result = 31 * result + bytesTransferred
        result = 31 * result + status.hashCode()
        return result
    }
}
