package com.ble1st.connectias.feature.usb.storage

import android.content.Context
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.driver.scsi.ScsiDriver
import com.ble1st.connectias.feature.usb.models.DiscType
import com.ble1st.connectias.feature.usb.models.FileSystem
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for optical drive (DVD/CD) detection and access.
 */
@Singleton
class OpticalDriveProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceDetector: UsbDeviceDetector
) {
    
    private companion object {
        const val DEVICE_DETECT_TIMEOUT_MS = 5_000L
        const val SCSI_TYPE_CDROM = 0x05
    }

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    /**
     * Detects an optical drive via SCSI INQUIRY.
     */
    suspend fun detectOpticalDrive(): OpticalDrive? = withContext(Dispatchers.IO) {
        Timber.d("Starting optical drive detection via SCSI...")
        
        try {
            // 1. Get USB devices
            val devices = withTimeout(DEVICE_DETECT_TIMEOUT_MS) {
                deviceDetector.detectedDevices.first()
            }
            
            // 2. Iterate and check for Optical Drive type (SCSI Type 0x05)
            for (device in devices) {
                // Filter candidates: Mass Storage (Class 8)
                if (!device.isMassStorage && device.deviceClass != 0) { // 0 = per-interface
                    continue
                }

                // We need to open the device to send SCSI INQUIRY
                // Find Android UsbDevice
                val androidDevice = usbManager.deviceList.values.find { 
                    it.vendorId == device.vendorId && it.productId == device.productId 
                } ?: continue

                if (!usbManager.hasPermission(androidDevice)) {
                    Timber.w("No permission for device: ${device.product}")
                    continue
                }

                val connection = usbManager.openDevice(androidDevice) ?: continue
                
                try {
                    // Find Mass Storage Interface
                    var interfaceIndex = -1
                    for (i in 0 until androidDevice.interfaceCount) {
                        if (androidDevice.getInterface(i).interfaceClass == 8) {
                            interfaceIndex = i
                            break
                        }
                    }
                    if (interfaceIndex == -1) {
                        connection.close()
                        continue
                    }
                    
                    val usbInterface = androidDevice.getInterface(interfaceIndex)
                    val driver = ScsiDriver(connection, usbInterface)
                    
                    try {
                        val inquiryData = driver.inquiry()
                        if (inquiryData.isNotEmpty()) {
                            val periphType = inquiryData[0].toInt() and 0x1F
                            Timber.d("Device ${device.product} SCSI Type: 0x%02X", periphType)
                            
                            if (periphType == SCSI_TYPE_CDROM) {
                                Timber.i("Found Optical Drive: ${device.product}")
                                return@withContext OpticalDrive(
                                    device = device,
                                    mountPoint = null, // No longer needed/available
                                    devicePath = null, // Legacy
                                    fileSystem = FileSystem.UNKNOWN,
                                    type = DiscType.UNKNOWN // Will be determined on access
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w("SCSI INQUIRY failed for ${device.product}: ${e.message}")
                    } finally {
                        driver.close()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error checking device ${device.product}")
                    connection.close()
                }
            }
            
            Timber.w("No Optical Drive found")
            null
        } catch (e: TimeoutCancellationException) {
            Timber.w("Timeout waiting for USB devices")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error during optical drive detection")
            null
        }
    }
    
    /**
     * Ejects the optical drive.
     */
    suspend fun ejectDrive(drive: OpticalDrive): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("Ejecting optical drive: ${drive.device.product}")
            
            val androidDevice = usbManager.deviceList.values.find { 
                it.vendorId == drive.device.vendorId && it.productId == drive.device.productId 
            } ?: return@withContext false

            if (!usbManager.hasPermission(androidDevice)) return@withContext false

            val connection = usbManager.openDevice(androidDevice) ?: return@withContext false
            
            try {
                // Find Mass Storage Interface
                var interfaceIndex = 0
                for (i in 0 until androidDevice.interfaceCount) {
                    if (androidDevice.getInterface(i).interfaceClass == 8) {
                        interfaceIndex = i
                        break
                    }
                }
                val usbInterface = androidDevice.getInterface(interfaceIndex)
                val driver = ScsiDriver(connection, usbInterface)
                
                try {
                    // Wait for drive to be ready before eject
                    if (!driver.waitForReady(maxAttempts = 5, delayMs = 300)) {
                        Timber.w("Drive not ready for eject, attempting anyway...")
                    }
                    
                    driver.eject()
                    Timber.i("Eject command sent successfully via SCSI")
                    return@withContext true
                } catch (e: Exception) {
                    Timber.e(e, "SCSI Eject failed: ${e.message}")
                    false
                } finally {
                    driver.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting up SCSI driver for eject")
                try { connection.close() } catch (_: Exception) {}
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error ejecting optical drive")
            false
        }
    }

    /**
     * Lists files on the optical drive.
     * Currently returns empty list as file system mounting is not supported.
     */
    suspend fun listFiles(drive: OpticalDrive): List<FileInfo> {
        Timber.w("Listing files not supported without mount point")
        return emptyList()
    }
}