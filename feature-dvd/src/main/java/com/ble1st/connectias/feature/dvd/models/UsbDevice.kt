package com.ble1st.connectias.feature.dvd.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USB device information.
 * Copied from feature-usb to break dependency.
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
    val uniqueId: String = "${vendorId}_${productId}"
) : Parcelable {

    companion object
}
