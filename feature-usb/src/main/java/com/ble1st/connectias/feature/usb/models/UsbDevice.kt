package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USB device information.
 * 
 * @property vendorId USB vendor ID (VID) - identifies the manufacturer (e.g., 0x046D for Logitech)
 * @property productId USB product ID (PID) - identifies the specific device model
 * @property deviceClass USB device class code - standard USB class (e.g., 08 = Mass Storage, 03 = HID)
 * @property deviceSubclass USB device subclass - further classification within the device class
 * @property deviceProtocol USB device protocol - protocol used within the device class/subclass
 * @property serialNumber Device serial number, if available (may be null)
 * @property manufacturer Manufacturer name string, if available (may be null)
 * @property product Product name string, if available (may be null)
 * @property version Device version string, if available (may be null)
 * @property uniqueId Unique identifier for the device - uses serialNumber if available, otherwise deviceName or vendorId_productId_deviceId
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
    val isMassStorage: Boolean
        get() = deviceClass == USB_CLASS_MASS_STORAGE

    companion object {
        const val USB_CLASS_MASS_STORAGE = 8
    }
}
