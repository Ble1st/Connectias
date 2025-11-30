package com.ble1st.connectias.feature.usb.provider

import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.models.UsbTransfer
import com.ble1st.connectias.feature.usb.native.UsbNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for USB device operations.
 */
@Singleton
class UsbProvider @Inject constructor() {
    
    /**
     * Enumerates all connected USB devices.
     */
    suspend fun enumerateDevices(): List<UsbDevice> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting USB device enumeration...")
            val nativeDevices = UsbNative.enumerateDevices().toList()
            Timber.d("Native enumeration returned ${nativeDevices.size} devices")
            
            val devices = nativeDevices.map { native ->
                UsbDevice(
                    vendorId = native.vendorId,
                    productId = native.productId,
                    deviceClass = native.deviceClass,
                    deviceSubclass = 0,
                    deviceProtocol = 0,
                    serialNumber = native.serialNumber,
                    manufacturer = native.manufacturer,
                    product = native.product,
                    version = null,
                    isMassStorage = native.deviceClass == UsbDevice.USB_CLASS_MASS_STORAGE
                )
            }
            
            Timber.i("USB device enumeration complete: ${devices.size} devices found")
            devices.forEach { device ->
                Timber.d("Device: Vendor=0x%04X, Product=0x%04X, Class=%d, MassStorage=%b",
                    device.vendorId, device.productId, device.deviceClass, device.isMassStorage)
            }
            
            devices
        } catch (e: Exception) {
            Timber.e(e, "Failed to enumerate USB devices")
            emptyList()
        }
    }
    
    /**
     * Opens a USB device.
     */
    suspend fun openDevice(device: UsbDevice): Long = withContext(Dispatchers.IO) {
        try {
            Timber.d("Opening USB device: Vendor=0x%04X, Product=0x%04X", device.vendorId, device.productId)
            val handle = UsbNative.openDevice(device.vendorId, device.productId)
            Timber.i("USB device opened successfully, handle: $handle")
            handle
        } catch (e: Exception) {
            Timber.e(e, "Failed to open USB device")
            throw e
        }
    }
    
    /**
     * Closes a USB device.
     */
    suspend fun closeDevice(handle: Long) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Closing USB device, handle: $handle")
            UsbNative.closeDevice(handle)
            Timber.d("USB device closed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to close USB device")
        }
    }
    
    /**
     * Performs a bulk transfer.
     */
    suspend fun bulkTransfer(handle: Long, endpoint: Int, data: ByteArray): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            Timber.d("Bulk transfer: handle=$handle, endpoint=0x%02X, length=${data.size}")
            val bytesTransferred = UsbNative.bulkTransfer(handle, endpoint, data)
            Timber.d("Bulk transfer complete: $bytesTransferred bytes")
            
            UsbTransfer(
                type = UsbTransfer.TransferType.BULK,
                endpoint = endpoint,
                data = data,
                bytesTransferred = bytesTransferred,
                status = if (bytesTransferred >= 0) UsbTransfer.TransferStatus.SUCCESS else UsbTransfer.TransferStatus.ERROR
            )
        } catch (e: Exception) {
            Timber.e(e, "Bulk transfer failed")
            UsbTransfer(
                type = UsbTransfer.TransferType.BULK,
                endpoint = endpoint,
                data = data,
                bytesTransferred = 0,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
    
    /**
     * Performs an interrupt transfer.
     */
    suspend fun interruptTransfer(handle: Long, endpoint: Int, data: ByteArray): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            Timber.d("Interrupt transfer: handle=$handle, endpoint=0x%02X, length=${data.size}")
            val bytesTransferred = UsbNative.interruptTransfer(handle, endpoint, data)
            Timber.d("Interrupt transfer complete: $bytesTransferred bytes")
            
            UsbTransfer(
                type = UsbTransfer.TransferType.INTERRUPT,
                endpoint = endpoint,
                data = data,
                bytesTransferred = bytesTransferred,
                status = if (bytesTransferred >= 0) UsbTransfer.TransferStatus.SUCCESS else UsbTransfer.TransferStatus.ERROR
            )
        } catch (e: Exception) {
            Timber.e(e, "Interrupt transfer failed")
            UsbTransfer(
                type = UsbTransfer.TransferType.INTERRUPT,
                endpoint = endpoint,
                data = data,
                bytesTransferred = 0,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
    
    /**
     * Performs a control transfer.
     */
    suspend fun controlTransfer(
        handle: Long,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?
    ): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            Timber.d("Control transfer: handle=$handle, requestType=0x%02X, request=0x%02X", requestType, request)
            val bytesTransferred = UsbNative.controlTransfer(handle, requestType, request, value, index, data)
            Timber.d("Control transfer complete: $bytesTransferred bytes")
            
            UsbTransfer(
                type = UsbTransfer.TransferType.CONTROL,
                endpoint = 0,
                data = data ?: ByteArray(0),
                bytesTransferred = bytesTransferred,
                status = if (bytesTransferred >= 0) UsbTransfer.TransferStatus.SUCCESS else UsbTransfer.TransferStatus.ERROR
            )
        } catch (e: Exception) {
            Timber.e(e, "Control transfer failed")
            UsbTransfer(
                type = UsbTransfer.TransferType.CONTROL,
                endpoint = 0,
                data = data ?: ByteArray(0),
                bytesTransferred = 0,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
}
