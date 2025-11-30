package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USB descriptor information.
 * Uses immutable List<Byte> for data to ensure structural equality and immutability.
 */
@Parcelize
data class UsbDescriptor(
    val type: DescriptorType,
    val length: Int,
    val data: List<Byte>
) : Parcelable {
    enum class DescriptorType {
        DEVICE,
        CONFIGURATION,
        INTERFACE,
        ENDPOINT,
        STRING
    }
    
    /**
     * Converts the data list to a ByteArray.
     * Creates a defensive copy to prevent external modification.
     */
    fun toByteArray(): ByteArray = data.toByteArray()
    
    companion object {
        /**
         * Creates a UsbDescriptor from a ByteArray.
         * Makes a defensive copy to ensure immutability.
         */
        fun fromByteArray(type: DescriptorType, length: Int, data: ByteArray): UsbDescriptor {
            return UsbDescriptor(
                type = type,
                length = length,
                data = data.toList() // Defensive copy
            )
        }
    }
}
