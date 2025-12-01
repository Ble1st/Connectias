package com.ble1st.connectias.feature.usb.provider

import android.content.Context
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.models.UsbTransfer
import com.ble1st.connectias.feature.usb.native.UsbClass
import com.ble1st.connectias.feature.usb.native.UsbError
import com.ble1st.connectias.feature.usb.native.UsbNative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result type for USB operations that can fail.
 */
sealed class UsbResult<out T> {
    data class Success<T>(val data: T) : UsbResult<T>()
    data class Failure(val error: Throwable) : UsbResult<Nothing>()
}

/**
 * Provider for USB device operations.
 */
@Singleton
class UsbProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    // Persistent mapping for devices without stable identifiers - REMOVED as we now use UsbManager directly
    /*
    private val deviceMappingPrefs: SharedPreferences by lazy {
        // ... removed ...
    }
    */
    
    // Helper methods removed as they are no longer needed with UsbManager usage
    
    /**
     * Enumerates all connected USB devices.
     * @return Result containing list of devices or error
     */
    suspend fun enumerateDevices(): UsbResult<List<UsbDevice>> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting USB device enumeration via UsbManager...")
            // Use Android's UsbManager directly instead of native enumeration
            val androidDevices = usbManager.deviceList.values.toList()
            Timber.d("UsbManager returned ${androidDevices.size} devices")
            
            val devices = androidDevices.map { androidDevice ->
                // Logic similar to UsbDeviceDetector to ensure consistency
                
                // Check permission for potential extra details
                val hasPermission = usbManager.hasPermission(androidDevice)
                
                val serialNumber = if (hasPermission) {
                    try { androidDevice.serialNumber } catch (e: SecurityException) { null }
                } else null
                
                val manufacturer = if (hasPermission) {
                    try { androidDevice.manufacturerName } catch (e: SecurityException) { null }
                } else null
                
                val product = if (hasPermission) {
                    try { androidDevice.productName } catch (e: SecurityException) { null }
                } else null
                
                // Generate a unique ID (consistent with UsbDeviceDetector logic if possible, 
                // or use our stable ID logic adapted for Android device)
                val uniqueId = if (!serialNumber.isNullOrBlank()) {
                    serialNumber
                } else {
                    // Fallback unique ID
                    "${androidDevice.vendorId}_${androidDevice.productId}_${androidDevice.deviceId}"
                }
                
                // Check for Mass Storage / Optical Drive indicators
                val isDeviceClassMassStorage = androidDevice.deviceClass == UsbClass.MASS_STORAGE.value
                
                // Check interfaces
                var hasMassStorageInterface = false
                val interfaceCount = androidDevice.interfaceCount
                for (i in 0 until interfaceCount) {
                    try {
                        val iface = androidDevice.getInterface(i)
                        if (iface.interfaceClass == UsbClass.MASS_STORAGE.value) {
                            hasMassStorageInterface = true
                            break
                        }
                    } catch (e: Exception) {
                        // Ignore errors reading interfaces
                    }
                }
                
                val productNameLower = product?.lowercase() ?: ""
                val manufacturerNameLower = manufacturer?.lowercase() ?: ""
                val hasDvdCdIndicators = productNameLower.contains("dvd") || 
                                         productNameLower.contains("cd") || 
                                         productNameLower.contains("optical") ||
                                         productNameLower.contains("disc") ||
                                         manufacturerNameLower.contains("dvd") ||
                                         manufacturerNameLower.contains("cd") ||
                                         manufacturerNameLower.contains("optical")
                
                val isMassStorage = isDeviceClassMassStorage || hasMassStorageInterface || hasDvdCdIndicators
                
                val effectiveDeviceClass = if (isMassStorage && !isDeviceClassMassStorage) {
                     UsbClass.MASS_STORAGE.value
                } else {
                    androidDevice.deviceClass
                }
                
                UsbDevice(
                    vendorId = androidDevice.vendorId,
                    productId = androidDevice.productId,
                    deviceClass = effectiveDeviceClass,
                    deviceSubclass = androidDevice.deviceSubclass,
                    deviceProtocol = androidDevice.deviceProtocol,
                    serialNumber = serialNumber,
                    manufacturer = manufacturer,
                    product = product,
                    version = if (androidDevice.version != null) androidDevice.version else "1.0.0", 
                    uniqueId = uniqueId
                )
            }
            
            Timber.i("USB device enumeration complete: ${devices.size} devices found")
            devices.forEach { device ->
                Timber.d("Device: Vendor=0x%04X, Product=0x%04X, Class=%d, MassStorage=%b",
                    device.vendorId, device.productId, device.deviceClass, device.isMassStorage)
            }
            
            UsbResult.Success(devices)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enumerate USB devices")
            UsbResult.Failure(e)
        }
    }
    
    /**
     * Opens a USB device.
     * Note: This method uses only vendorId and productId, which may not uniquely identify
     * multiple identical devices. Consider using a unique device identifier (e.g., serialNumber
     * or bus/port combination) when available.
     * 
     * @param device USB device to open
     * @return Device handle, or -1 on error
     */
    suspend fun openDevice(device: UsbDevice): Long = withContext(Dispatchers.IO) {
        try {
            Timber.d("Opening USB device: Vendor=0x%04X, Product=0x%04X, Serial=${device.serialNumber}", 
                device.vendorId, device.productId)
            val handle = UsbNative.openDevice(device.vendorId, device.productId)
            if (handle >= 0) {
                Timber.i("USB device opened successfully, handle: $handle")
            } else {
                Timber.w("Failed to open USB device, handle: $handle")
            }
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
     * For IN (read) transfers, returns only the bytes actually read.
     * For OUT (write) transfers, returns the original data buffer.
     */
    suspend fun bulkTransfer(handle: Long, endpoint: Int, data: ByteArray): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            val isInEndpoint = (endpoint and 0x80) != 0
            Timber.d("Bulk transfer: handle=$handle, endpoint=0x%02X, length=${data.size}, direction=${if (isInEndpoint) "IN" else "OUT"}")
            
            val bytesTransferred = UsbNative.bulkTransfer(handle, endpoint, data)
            
            val resultData: ByteArray
            val status: UsbTransfer.TransferStatus
            
            when {
                bytesTransferred < 0 -> {
                    // Error occurred
                    val errorMsg = UsbError.errorCodeToString(bytesTransferred)
                    Timber.e("Bulk transfer failed: $errorMsg (code: $bytesTransferred)")
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = when (bytesTransferred) {
                        UsbError.ERROR_TIMEOUT -> UsbTransfer.TransferStatus.TIMEOUT
                        UsbError.ERROR_NO_DEVICE -> UsbTransfer.TransferStatus.NO_DEVICE
                        else -> UsbTransfer.TransferStatus.ERROR
                    }
                }
                bytesTransferred == 0 -> {
                    // Zero-length transfer (may be valid for some operations)
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Bulk transfer complete: 0 bytes (zero-length transfer)")
                }
                isInEndpoint -> {
                    // IN transfer: return only the bytes actually read
                    resultData = data.copyOf(bytesTransferred)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Bulk transfer complete: $bytesTransferred bytes read")
                }
                else -> {
                    // OUT transfer: return original buffer
                    resultData = data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Bulk transfer complete: $bytesTransferred bytes written")
                }
            }
            
            UsbTransfer(
                type = UsbTransfer.TransferType.BULK,
                endpoint = endpoint,
                data = resultData,
                bytesTransferred = bytesTransferred,
                status = status
            )
        } catch (e: Exception) {
            Timber.e(e, "Bulk transfer failed with exception")
            UsbTransfer(
                type = UsbTransfer.TransferType.BULK,
                endpoint = endpoint,
                data = data,
                bytesTransferred = -1,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
    
    /**
     * Performs an interrupt transfer.
     * For IN (read) transfers, returns only the bytes actually read.
     * For OUT (write) transfers, returns the original data buffer.
     * 
     * @param timeoutMs Timeout in milliseconds (default: 5000ms)
     */
    suspend fun interruptTransfer(
        handle: Long, 
        endpoint: Int, 
        data: ByteArray,
        timeoutMs: Int = 5000
    ): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            val isInEndpoint = (endpoint and 0x80) != 0
            Timber.d("Interrupt transfer: handle=$handle, endpoint=0x%02X, length=${data.size}, timeout=${timeoutMs}ms, direction=${if (isInEndpoint) "IN" else "OUT"}")
            
            val bytesTransferred = UsbNative.interruptTransfer(handle, endpoint, data, timeoutMs)
            
            val resultData: ByteArray
            val status: UsbTransfer.TransferStatus
            
            when {
                bytesTransferred < 0 -> {
                    // Error occurred
                    val errorMsg = UsbError.errorCodeToString(bytesTransferred)
                    Timber.e("Interrupt transfer failed: $errorMsg (code: $bytesTransferred)")
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = when (bytesTransferred) {
                        UsbError.ERROR_TIMEOUT -> UsbTransfer.TransferStatus.TIMEOUT
                        UsbError.ERROR_NO_DEVICE -> UsbTransfer.TransferStatus.NO_DEVICE
                        else -> UsbTransfer.TransferStatus.ERROR
                    }
                }
                bytesTransferred == 0 -> {
                    // Zero-length transfer
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Interrupt transfer complete: 0 bytes (zero-length transfer)")
                }
                isInEndpoint -> {
                    // IN transfer: return only the bytes actually read
                    resultData = data.copyOf(bytesTransferred)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Interrupt transfer complete: $bytesTransferred bytes read")
                }
                else -> {
                    // OUT transfer: return original buffer
                    resultData = data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Interrupt transfer complete: $bytesTransferred bytes written")
                }
            }
            
            UsbTransfer(
                type = UsbTransfer.TransferType.INTERRUPT,
                endpoint = endpoint,
                data = resultData,
                bytesTransferred = bytesTransferred,
                status = status
            )
        } catch (e: Exception) {
            Timber.e(e, "Interrupt transfer failed with exception")
            UsbTransfer(
                type = UsbTransfer.TransferType.INTERRUPT,
                endpoint = endpoint,
                data = data,
                bytesTransferred = -1,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
    
    /**
     * Performs a control transfer.
     * For IN (read) transfers, returns only the bytes actually read.
     * For OUT (write) transfers or no-data transfers, returns the original data buffer or empty array.
     */
    suspend fun controlTransfer(
        handle: Long,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
        timeoutMs: Int = 5000
    ): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            val isInRequest = (requestType and 0x80) != 0
            Timber.d("Control transfer: handle=$handle, requestType=0x%02X, request=0x%02X, direction=${if (isInRequest) "IN" else "OUT"}", requestType, request)
            
            val bytesTransferred = UsbNative.controlTransfer(handle, requestType, request, value, index, data, timeoutMs)
            
            val resultData: ByteArray
            val status: UsbTransfer.TransferStatus
            
            when {
                bytesTransferred < 0 -> {
                    // Error occurred
                    val errorMsg = UsbError.errorCodeToString(bytesTransferred)
                    Timber.e("Control transfer failed: $errorMsg (code: $bytesTransferred)")
                    // For OUT (write) operations, return original buffer; for IN (read) or no-data, return empty array
                    resultData = if (!isInRequest && data != null) {
                        data
                    } else {
                        ByteArray(0)
                    }
                    status = when (bytesTransferred) {
                        UsbError.ERROR_TIMEOUT -> UsbTransfer.TransferStatus.TIMEOUT
                        UsbError.ERROR_NO_DEVICE -> UsbTransfer.TransferStatus.NO_DEVICE
                        else -> UsbTransfer.TransferStatus.ERROR
                    }
                }
                bytesTransferred == 0 -> {
                    // Zero-length transfer (valid for no-data or zero-length data transfers)
                    resultData = data ?: ByteArray(0)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Control transfer complete: 0 bytes")
                }
                isInRequest && data != null -> {
                    // IN transfer: return only the bytes actually read
                    resultData = data.copyOf(bytesTransferred)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Control transfer complete: $bytesTransferred bytes read")
                }
                else -> {
                    // OUT transfer or no-data transfer: return original buffer
                    resultData = data ?: ByteArray(0)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Control transfer complete: $bytesTransferred bytes")
                }
            }
            
            UsbTransfer(
                type = UsbTransfer.TransferType.CONTROL,
                endpoint = 0,
                data = resultData,
                bytesTransferred = bytesTransferred,
                status = status
            )
        } catch (e: Exception) {
            Timber.e(e, "Control transfer failed with exception")
            UsbTransfer(
                type = UsbTransfer.TransferType.CONTROL,
                endpoint = 0,
                data = data ?: ByteArray(0),
                bytesTransferred = -1,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
}
