package com.ble1st.connectias.feature.dvd.storage

import android.content.Context
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.dvd.driver.scsi.ScsiDriver
import com.ble1st.connectias.feature.dvd.models.DiscType
import com.ble1st.connectias.feature.dvd.models.FileInfo
import com.ble1st.connectias.feature.dvd.models.FileSystem
import com.ble1st.connectias.feature.dvd.models.OpticalDrive
import com.ble1st.connectias.feature.dvd.models.UsbDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for optical drive (DVD/CD) detection and access.
 */
@Singleton
class OpticalDriveProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private companion object {
        const val SCSI_TYPE_CDROM = 0x05
    }

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    // Mutex to serialize USB access and prevent concurrent claims
    private val usbMutex = Mutex()
    
    // Cache active driver session for playback
    private var activeSession: ScsiDriver? = null
    
    /**
     * Opens a persistent session (driver) for the given drive.
     * Closes any existing session.
     * This must be called from a coroutine.
     */
    suspend fun openSession(drive: OpticalDrive): ScsiDriver? = usbMutex.withLock {
        // Cleanup old session if it exists
        activeSession?.let {
            try { it.close() } catch (e: Exception) { Timber.w("Error closing old session: ${e.message}") }
        }
        activeSession = null
        
        try {
            val androidDevice = usbManager.deviceList.values.find { 
                it.vendorId == drive.device.vendorId && it.productId == drive.device.productId 
            } ?: return@withLock null

            if (!usbManager.hasPermission(androidDevice)) return@withLock null

            val connection = usbManager.openDevice(androidDevice) ?: return@withLock null
            
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
            
            activeSession = driver
            Timber.i("OpticalDriveProvider: Active session opened for ${drive.device.product}")
            return@withLock driver
        } catch (e: Exception) {
            Timber.e(e, "Failed to open session")
            return@withLock null
        }
    }
    
    /**
     * Gets the currently active driver session.
     * Note: Accessing this without lock is risky if session is closing.
     */
    fun getActiveSession(): ScsiDriver? {
        return activeSession
    }
    
    /**
     * Closes the active session.
     */
    suspend fun closeSession() = usbMutex.withLock {
        activeSession?.let {
            try {
                it.close()
                Timber.d("OpticalDriveProvider: Session closed")
            } catch (e: Exception) {
                Timber.w("Error closing session: ${e.message}")
            }
        }
        activeSession = null
    }
    
    /**
     * Returns a list of connected USB Mass Storage devices (potential optical drives).
     * Does not require permission to list them.
     */
    fun getConnectedUsbMassStorageDevices(): List<android.hardware.usb.UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            // Check device class
            if (device.deviceClass == 8) return@filter true
            
            // Check interfaces if device class is 0
            if (device.deviceClass == 0) {
                for (i in 0 until device.interfaceCount) {
                    if (device.getInterface(i).interfaceClass == 8) {
                        return@filter true
                    }
                }
            }
            false
        }
    }
    
    /**
     * Checks if the app has permission for the given device.
     */
    fun hasPermission(device: android.hardware.usb.UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * Detects an optical drive via SCSI INQUIRY.
     */
    suspend fun detectOpticalDrive(): OpticalDrive? = usbMutex.withLock {
        Timber.d("Starting optical drive detection via SCSI...")
        
        // Close active session if it exists to allow detection
        if (activeSession != null) {
            Timber.d("Closing active session for detection")
            try { activeSession?.close() } catch (_: Exception) {}
            activeSession = null
        }
        
        return@withLock withContext(Dispatchers.IO) {
            try {
                // 1. Get USB devices directly from UsbManager
                val devices = usbManager.deviceList.values.toList()
                
                // 2. Iterate and check for Optical Drive type (SCSI Type 0x05)
                for (androidDevice in devices) {
                    // Filter candidates: Mass Storage (Class 8)
                    // Check device class (0) means look at interfaces
                    var isMassStorage = androidDevice.deviceClass == 8
                    if (androidDevice.deviceClass == 0) {
                        for (i in 0 until androidDevice.interfaceCount) {
                             if (androidDevice.getInterface(i).interfaceClass == 8) {
                                 isMassStorage = true
                                 break
                             }
                        }
                    }
                    
                    if (!isMassStorage) {
                        continue
                    }

                    // We need to open the device to send SCSI INQUIRY
                    if (!usbManager.hasPermission(androidDevice)) {
                        Timber.w("No permission for device: ${androidDevice.productName}")
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
                                Timber.d("Device ${androidDevice.productName} SCSI Type: 0x%02X", periphType)
                                
                                if (periphType == SCSI_TYPE_CDROM) {
                                    Timber.i("Found Optical Drive: ${androidDevice.productName}")
                                    
                                    // Map Android UsbDevice to local UsbDevice model
                                    val dvdDevice = UsbDevice(
                                        vendorId = androidDevice.vendorId,
                                        productId = androidDevice.productId,
                                        deviceClass = androidDevice.deviceClass,
                                        deviceSubclass = androidDevice.deviceSubclass,
                                        deviceProtocol = androidDevice.deviceProtocol,
                                        serialNumber = androidDevice.serialNumber,
                                        manufacturer = androidDevice.manufacturerName,
                                        product = androidDevice.productName,
                                        version = androidDevice.version
                                    )
                                    
                                    return@withContext OpticalDrive(
                                        device = dvdDevice,
                                        mountPoint = null, // No longer needed/available
                                        devicePath = null, // Legacy
                                        fileSystem = FileSystem.UNKNOWN,
                                        type = DiscType.UNKNOWN // Will be determined on access
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w("SCSI INQUIRY failed for ${androidDevice.productName}: ${e.message}")
                        } finally {
                            driver.close()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error checking device ${androidDevice.productName}")
                        connection.close()
                    }
                }
                
                Timber.w("No Optical Drive found")
                null
            } catch (e: Exception) {
                Timber.e(e, "Error during optical drive detection")
                null
            }
        }
    }
    
    /**
     * Ejects the optical drive.
     */
    suspend fun ejectDrive(drive: OpticalDrive): Boolean = usbMutex.withLock {
        // Close active session first
         if (activeSession != null) {
            try { activeSession?.close() } catch (_: Exception) {}
            activeSession = null
        }

        return@withLock withContext(Dispatchers.IO) {
            Timber.d("Ejecting optical drive: ${drive.device.product}")

            val androidDevice = usbManager.deviceList.values.find {
                it.vendorId == drive.device.vendorId && it.productId == drive.device.productId
            } ?: return@withContext false

            if (!usbManager.hasPermission(androidDevice)) return@withContext false

            fun tryEject(): Boolean {
                val connection = usbManager.openDevice(androidDevice) ?: return false
                try {
                    // Find Mass Storage Interface (class 8)
                    val usbInterface = (0 until androidDevice.interfaceCount)
                        .map { androidDevice.getInterface(it) }
                        .firstOrNull { it.interfaceClass == 8 }
                        ?: androidDevice.getInterface(0)

                    val driver = ScsiDriver(connection, usbInterface)
                    try {
                        // Wait for drive readiness; be tolerant but limited
                        if (!driver.waitForReady(maxAttempts = 8, delayMs = 300)) {
                            Timber.w("Drive not ready for eject, attempting anyway...")
                        }
                        driver.eject()
                        Timber.i("Eject command sent successfully via SCSI")
                        return true
                    } catch (e: Exception) {
                        Timber.e(e, "SCSI Eject failed: ${e.message}")
                        return false
                    } finally {
                        driver.close()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error setting up SCSI driver for eject")
                    return false
                } finally {
                    try { connection.close() } catch (_: Exception) {}
                }
            }

            // First attempt
            val first = tryEject()
            if (first) return@withContext true

            Timber.w("Eject failed, retrying with fresh USB connection")
            // Short pause before retry to let USB stack settle
            kotlinx.coroutines.delay(250)
            return@withContext tryEject()
        }
    }

    /**
     * Lists files on the optical drive.
     * Currently returns empty list as file system mounting is not supported.
     */
    fun listFiles(): List<FileInfo> {
        Timber.w("Listing files not supported without mount point")
        return emptyList()
    }
}
