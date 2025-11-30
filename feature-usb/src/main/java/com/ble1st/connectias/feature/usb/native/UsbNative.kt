package com.ble1st.connectias.feature.usb.native

import timber.log.Timber

/**
 * Native interface for USB operations via libusb.
 */
object UsbNative {
    
    init {
        try {
            Timber.d("Loading USB native library...")
            System.loadLibrary("usb_jni")
            Timber.d("USB native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load USB native library")
            throw IllegalStateException("USB native library not available", e)
        }
    }
    
    /**
     * Enumerates all connected USB devices.
     */
    external fun enumerateDevices(): Array<UsbDeviceNative>
    
    /**
     * Opens a USB device by vendor and product ID.
     * @return Handle to the opened device, or -1 on error
     */
    external fun openDevice(vendorId: Int, productId: Int): Long
    
    /**
     * Closes a USB device.
     */
    external fun closeDevice(handle: Long)
    
    /**
     * Performs a bulk transfer.
     * @return Number of bytes transferred, or negative on error
     */
    external fun bulkTransfer(handle: Long, endpoint: Int, data: ByteArray): Int
    
    /**
     * Performs an interrupt transfer.
     * @return Number of bytes transferred, or negative on error
     */
    external fun interruptTransfer(handle: Long, endpoint: Int, data: ByteArray): Int
    
    /**
     * Performs a control transfer.
     * @return Number of bytes transferred, or negative on error
     */
    external fun controlTransfer(
        handle: Long,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?
    ): Int
}

/**
 * Native USB device structure.
 */
data class UsbDeviceNative(
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val serialNumber: String?,
    val manufacturer: String?,
    val product: String?
)
