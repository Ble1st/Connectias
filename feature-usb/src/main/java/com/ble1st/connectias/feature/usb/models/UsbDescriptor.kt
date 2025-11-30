package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USB descriptor information.
 */
@Parcelize
data class UsbDescriptor(
    val type: DescriptorType,
    val length: Int,
    val data: ByteArray
) : Parcelable {
    enum class DescriptorType {
        DEVICE,
        CONFIGURATION,
        INTERFACE,
        ENDPOINT,
        STRING
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as UsbDescriptor
        
        if (type != other.type) return false
        if (length != other.length) return false
        if (!data.contentEquals(other.data)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + length
        result = 31 * result + data.contentHashCode()
        return result
    }
}
