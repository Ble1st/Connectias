package com.ble1st.connectias.feature.usb.provider

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.models.UsbTransfer
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
    
    /**
     * Enumerates all connected USB devices.
     * @return Result containing list of devices or error
     */
    suspend fun enumerateDevices(): UsbResult<List<UsbDevice>> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting USB device enumeration via UsbManager...")
            val androidDevices = usbManager.deviceList.values.toList()
            Timber.d("UsbManager returned ${androidDevices.size} devices")
            
            val devices = androidDevices.map { androidDevice ->
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
                
                val uniqueId = if (!serialNumber.isNullOrBlank()) {
                    serialNumber
                } else {
                    "${androidDevice.vendorId}_${androidDevice.productId}_${androidDevice.deviceId}"
                }
                
                val isDeviceClassMassStorage = androidDevice.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE
                
                var hasMassStorageInterface = false
                val interfaceCount = androidDevice.interfaceCount
                for (i in 0 until interfaceCount) {
                    try {
                        val iface = androidDevice.getInterface(i)
                        if (iface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                            hasMassStorageInterface = true
                            break
                        }
                    } catch (e: Exception) {
                        // Ignore errors reading interfaces
                    }
                }
                
                val isMassStorage = isDeviceClassMassStorage || hasMassStorageInterface
                
                val effectiveDeviceClass = if (isMassStorage && !isDeviceClassMassStorage) {
                     UsbConstants.USB_CLASS_MASS_STORAGE
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
            
            UsbResult.Success(devices)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enumerate USB devices")
            UsbResult.Failure(e)
        }
    }
    
    /**
     * Opens a USB device.
     */
    suspend fun openDevice(device: UsbDevice): Long = withContext(Dispatchers.IO) {
        // Native implementation removed
        -1L
    }
    
    /**
     * Closes a USB device.
     */
    suspend fun closeDevice(handle: Long) = withContext(Dispatchers.IO) {
        // Native implementation removed
    }
    
    /**
     * Performs a bulk transfer.
     */
    suspend fun bulkTransfer(handle: Long, endpoint: Int, data: ByteArray): UsbTransfer = withContext(Dispatchers.IO) {
        UsbTransfer(
            type = UsbTransfer.TransferType.BULK,
            endpoint = endpoint,
            data = data,
            bytesTransferred = -1,
            status = UsbTransfer.TransferStatus.ERROR
        )
    }
    
    /**
     * Performs an interrupt transfer.
     */
    suspend fun interruptTransfer(
        handle: Long, 
        endpoint: Int, 
        data: ByteArray,
        timeoutMs: Int = 5000
    ): UsbTransfer = withContext(Dispatchers.IO) {
        UsbTransfer(
            type = UsbTransfer.TransferType.INTERRUPT,
            endpoint = endpoint,
            data = data,
            bytesTransferred = -1,
            status = UsbTransfer.TransferStatus.ERROR
        )
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
        data: ByteArray?,
        timeoutMs: Int = 5000
    ): UsbTransfer = withContext(Dispatchers.IO) {
        UsbTransfer(
            type = UsbTransfer.TransferType.CONTROL,
            endpoint = 0,
            data = data ?: ByteArray(0),
            bytesTransferred = -1,
            status = UsbTransfer.TransferStatus.ERROR
        )
    }
}
