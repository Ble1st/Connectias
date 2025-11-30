package com.ble1st.connectias.feature.usb.storage

import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.models.DiscType
import com.ble1st.connectias.feature.usb.models.FileSystem
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import com.ble1st.connectias.feature.usb.models.UsbDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for optical drive (DVD/CD) detection and access.
 */
@Singleton
class OpticalDriveProvider @Inject constructor(
    private val deviceDetector: UsbDeviceDetector,
    private val mountManager: MountManager,
    private val fileSystemReader: FileSystemReader
) {
    
    /**
     * Detects and mounts an optical drive.
     */
    suspend fun detectAndMountOpticalDrive(): OpticalDrive? = withContext(Dispatchers.IO) {
        Timber.d("Starting optical drive detection...")
        
        try {
            // 1. Get USB devices from detector (uses Android USB Manager API)
            Timber.d("Enumerating USB devices...")
            val devices = deviceDetector.detectedDevices.first()
            Timber.d("Found ${devices.size} USB devices")
            
            // 2. Find Mass Storage device (check isMassStorage flag, not just deviceClass)
            Timber.d("Searching for Mass Storage devices...")
            val massStorageDevice = devices.find { device ->
                device.isMassStorage
            }
            
            if (massStorageDevice == null) {
                Timber.w("No Mass Storage device found. Available devices:")
                devices.forEach { device ->
                    Timber.d("  Device: ${device.product} (Vendor=0x%04X, Product=0x%04X, MassStorage=%b, DeviceClass=%d)",
                        device.vendorId, device.productId, device.isMassStorage, device.deviceClass)
                }
                return@withContext null
            }
            
            Timber.i("Found Mass Storage device: Vendor=0x%04X, Product=0x%04X", 
                massStorageDevice.vendorId, massStorageDevice.productId)
            
            // 3. Determine mount point
            Timber.d("Determining mount point...")
            val mountPoint = mountManager.findMountPoint(massStorageDevice)
            
            if (mountPoint == null) {
                Timber.w("Could not determine mount point for device")
                return@withContext null
            }
            
            Timber.i("Mount point determined: $mountPoint")
            
            // 4. Check mount point accessibility
            if (!mountManager.isMountPointAccessible(mountPoint)) {
                Timber.w("Mount point is not accessible: $mountPoint")
                return@withContext null
            }
            
            // 5. Detect file system
            Timber.d("Detecting file system...")
            val fileSystem = fileSystemReader.detectFileSystem(mountPoint)
            Timber.d("File system detected: $fileSystem")
            
            // 6. Detect disc type
            Timber.d("Detecting disc type...")
            val discType = detectDiscType(mountPoint, fileSystem)
            Timber.i("Disc type detected: $discType")
            
            val drive = OpticalDrive(
                device = massStorageDevice,
                mountPoint = mountPoint,
                fileSystem = fileSystem,
                type = discType
            )
            
            Timber.i("Optical drive successfully detected and mounted: type=$discType, fileSystem=$fileSystem")
            return@withContext drive
        } catch (e: Exception) {
            Timber.e(e, "Error during optical drive detection")
            return@withContext null
        }
    }
    
    /**
     * Lists files on an optical drive.
     */
    suspend fun listFiles(drive: OpticalDrive): List<FileInfo> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Listing files on optical drive: ${drive.mountPoint}")
            val files = fileSystemReader.listFiles(drive.mountPoint)
            Timber.i("Found ${files.size} items on optical drive")
            files
        } catch (e: Exception) {
            Timber.e(e, "Error listing files on optical drive")
            emptyList()
        }
    }
    
    /**
     * Detects the type of disc in the drive.
     */
    private fun detectDiscType(mountPoint: String, fileSystem: FileSystem): DiscType {
        Timber.d("Checking for disc type indicators at: $mountPoint")
        
        val rootDir = File(mountPoint)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            Timber.w("Mount point does not exist: $mountPoint")
            return DiscType.UNKNOWN
        }
        
        // Check for VIDEO_TS directory (Video DVD)
        val videoTsDir = File(rootDir, "VIDEO_TS")
        if (videoTsDir.exists() && videoTsDir.isDirectory) {
            Timber.d("VIDEO_TS directory found - Video DVD detected")
            return DiscType.VIDEO_DVD
        }
        
        // Check for AUDIO_TS directory (Audio DVD)
        val audioTsDir = File(rootDir, "AUDIO_TS")
        if (audioTsDir.exists() && audioTsDir.isDirectory) {
            Timber.d("AUDIO_TS directory found - Audio DVD detected")
            return DiscType.AUDIO_DVD
        }
        
        // Check for CDDA file (Audio CD indicator)
        val cddaFile = File(rootDir, "CDDA")
        if (cddaFile.exists()) {
            Timber.d("CDDA file found - Audio CD detected")
            return DiscType.AUDIO_CD
        }
        
        // Check for audio tracks (Audio CD)
        val files = rootDir.listFiles()
        val hasAudioTracks = files?.any { file ->
            file.isFile && (file.name.endsWith(".cda", ignoreCase = true) || 
                           file.name.matches(Regex("track\\d+\\.(wav|mp3)", RegexOption.IGNORE_CASE)))
        } ?: false
        
        if (hasAudioTracks) {
            Timber.d("Audio track files found - Audio CD detected")
            return DiscType.AUDIO_CD
        }
        
        // Default: Assume data disc
        // Try to determine if it's DVD or CD based on file system
        val discType = when (fileSystem) {
            FileSystem.UDF -> {
                Timber.d("UDF file system detected - assuming Data DVD")
                DiscType.DATA_DVD
            }
            FileSystem.ISO9660 -> {
                Timber.d("ISO9660 file system detected - assuming Data CD")
                DiscType.DATA_CD
            }
            else -> {
                Timber.d("Unknown file system - defaulting to Data DVD")
                DiscType.DATA_DVD
            }
        }
        
        Timber.d("Disc type detection complete: $discType")
        return discType
    }
}
