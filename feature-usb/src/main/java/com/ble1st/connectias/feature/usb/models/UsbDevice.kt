package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USB device information.
 */
@Parcelize
data class UsbDevice(
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val serialNumber: String?,
    val manufacturer: String?,
    val product: String?,
    val version: String?,
    val isMassStorage: Boolean = false
) : Parcelable {
    companion object {
        const val USB_CLASS_MASS_STORAGE = 8
    }
}
