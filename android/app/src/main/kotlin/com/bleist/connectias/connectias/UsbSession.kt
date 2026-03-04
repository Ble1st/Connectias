package com.bleist.connectias.connectias

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/**
 * Holds an open USB Mass Storage session with bulk IN/OUT endpoints.
 */
internal data class UsbSession(
    val connection: UsbDeviceConnection,
    val usbInterface: UsbInterface,
    val endpointIn: UsbEndpoint,
    val endpointOut: UsbEndpoint,
) {
    fun close() {
        connection.releaseInterface(usbInterface)
        connection.close()
    }
}
