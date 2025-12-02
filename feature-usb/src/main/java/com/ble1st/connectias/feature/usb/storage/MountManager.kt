package com.ble1st.connectias.feature.usb.storage

import android.content.Context
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.ble1st.connectias.feature.usb.models.UsbDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages mount points for USB storage devices.
 */
@Singleton
class MountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val storageManager: StorageManager? by lazy {
        context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    }
    
    /**
     * Finds the mount point for a USB Mass Storage device.
     */
    suspend fun findMountPoint(device: UsbDevice): String? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Finding mount point for USB device: Vendor=0x%04X, Product=0x%04X", 
                device.vendorId, device.productId)
            
            // Try StorageManager API first (Android 7.0+)
            val mountPoint = findMountPointViaStorageManager(device)
            if (mountPoint != null) {
                Timber.i("Mount point found via StorageManager: $mountPoint")
                return@withContext mountPoint
            }
            
            // Fallback: Scan common mount points
            val fallbackMountPoint = findMountPointViaScan()
            if (fallbackMountPoint != null) {
                Timber.i("Mount point found via scan: $fallbackMountPoint")
                return@withContext fallbackMountPoint
            }
            
            Timber.w("Could not determine mount point for USB device")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error finding mount point")
            null
        }
    }
    
    private fun findMountPointViaStorageManager(device: UsbDevice): String? {
        return try {
            val volumes = storageManager?.storageVolumes
            Timber.d("Scanning ${volumes?.size ?: 0} storage volumes for device Vendor=0x%04X, Product=0x%04X, Serial=%s",
                device.vendorId, device.productId, device.serialNumber ?: "none")
            
            // First, collect all removable and mounted volumes
            val candidateVolumes = mutableListOf<Pair<StorageVolume, String>>()
            
            volumes?.forEachIndexed { index, volume ->
                val isRemovable = volume.isRemovable
                val directory = volume.directory?.absolutePath
                val state = try {
                    volume.state
                } catch (e: Exception) {
                    "unknown"
                }
                
                Timber.d("  Volume $index: removable=$isRemovable, path=$directory, state=$state")
                
                // Check if volume is removable and mounted
                if (isRemovable && directory != null) {
                    val dir = File(directory)
                    if (dir.exists() && dir.isDirectory && dir.canRead()) {
                        candidateVolumes.add(Pair(volume, directory))
                        Timber.d("  Added candidate volume: $directory")
                    } else {
                        Timber.d("Volume directory exists but not accessible: $directory")
                        // Even if not directly accessible, try to use it if it's the only removable volume
                        // The directory path from StorageVolume might work even if direct File access fails
                        if (state == "mounted") {
                            Timber.d("  Volume is mounted but not directly accessible, will try as fallback: $directory")
                            candidateVolumes.add(Pair(volume, directory))
                        }
                    }
                }
            }
            
            // If only one candidate, return it (safe fallback)
            if (candidateVolumes.size == 1) {
                val mountPoint = candidateVolumes[0].second
                Timber.i("Found single removable storage volume: $mountPoint")
                return mountPoint
            }
            
            // If multiple candidates, log warning and return first candidate as fallback
            // Note: Per-device matching via StorageManager is unreliable without sysfs correlation.
            // A robust implementation would resolve the volume's mount path and correlate it with
            // the USB device node via /sys/bus/usb (e.g., map mount point -> block device -> 
            // sysfs parent USB device and compare vendor/product/serial there).
            if (candidateVolumes.size > 1) {
                Timber.w("Multiple removable volumes found (${candidateVolumes.size}), " +
                        "per-device matching via StorageManager is unreliable. " +
                        "Returning first candidate as fallback. " +
                        "Device: Vendor=0x%04X, Product=0x%04X. " +
                        "Candidates: ${candidateVolumes.map { it.second }.joinToString()}", 
                        device.vendorId, device.productId)
                return candidateVolumes[0].second
            }
            
            Timber.w("No suitable removable storage volume found")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error accessing StorageManager")
            null
        }
    }
    
    private fun findMountPointViaScan(): String? {
        val commonPaths = listOf(
            "/storage/usbdisk",
            "/storage/usb",
            "/mnt/usbdisk",
            "/mnt/usb",
            "/mnt/media_rw"
            // Note: /storage/emulated/0 and /sdcard are internal storage, not USB devices
        )
        
        Timber.d("Scanning common mount points...")
        commonPaths.forEach { basePath ->
            val dir = File(basePath)
            if (dir.exists() && dir.isDirectory) {
                val canRead = dir.canRead()
                val canWrite = dir.canWrite()
                Timber.d("  Checking $basePath: exists=true, canRead=$canRead, canWrite=$canWrite")
                
                if (canRead) {
                    // Check if this is a container directory (hosts multiple mounts)
                    val isContainer = isContainerDirectory(basePath)
                    
                    if (isContainer) {
                        // Container directory: scan subdirectories and return first readable one
                        Timber.d("  $basePath is a container directory, scanning subdirectories...")
                        try {
                            val subdirs = dir.listFiles()?.filter { it.isDirectory }
                            if (subdirs != null && subdirs.isNotEmpty()) {
                                subdirs.forEach { subdir ->
                                    Timber.d("    Subdirectory: ${subdir.absolutePath}")
                                    if (subdir.canRead()) {
                                        Timber.i("Found potential mount point: ${subdir.absolutePath}")
                                        return subdir.absolutePath
                                    }
                                }
                                // If we have subdirectories but none are readable, continue searching
                                Timber.d("  No readable subdirectories found in container $basePath")
                                return@forEach
                            } else {
                                // Container has no subdirectories, skip it
                                Timber.d("  Container $basePath has no subdirectories, skipping")
                                return@forEach
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Error listing subdirectories in $basePath")
                            return@forEach
                        }
                    } else {
                        // Not a container: basePath is itself a mountable volume
                        Timber.i("Found potential mount point: $basePath")
                        return basePath
                    }
                }
            } else {
                Timber.d("  Checking $basePath: exists=false")
            }
        }
        
        Timber.w("No mount point found in common paths")
        return null
    }
    
    /**
     * Determines if a path is a container directory that hosts multiple mount points.
     * Container directories like /mnt/media_rw contain subdirectories for individual volumes.
     */
    private fun isContainerDirectory(path: String): Boolean {
        try {
            // Known container directories
            val containerPatterns = listOf(
                "media_rw",
                "/mnt/media_rw",
                "/storage/media"
            )
            
            // Check if path matches known container patterns
            if (containerPatterns.any { path.contains(it) || path.endsWith(it) }) {
                return true
            }
        } catch (e: Exception) {
            Timber.d(e, "Error checking if $path is container")
        }
        
        return false
    }
    
    /**
     * Checks if a mount point is accessible.
     */
    suspend fun isMountPointAccessible(mountPoint: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(mountPoint)
            val accessible = dir.exists() && dir.isDirectory && dir.canRead()
            Timber.d("Mount point accessibility check: $mountPoint = $accessible")
            accessible
        } catch (e: Exception) {
            Timber.e(e, "Error checking mount point accessibility")
            false
        }
    }
    
    /**
     * Finds the device path for an optical drive (e.g., /dev/sg0, /dev/sr0).
     * This is a fallback when mount point is not available.
     * 
     * @param device USB device to find device path for
     * @return Device path (e.g., /dev/sg0) or null if not found
     */
    suspend fun findDevicePath(device: UsbDevice): String? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Finding device path for USB device: Vendor=0x%04X, Product=0x%04X",
                device.vendorId, device.productId)
            
            // Try to find SCSI generic device (sg*) - preferred for optical drives
            val sgDevices = findSgDevices()
            if (sgDevices.isNotEmpty()) {
                // For now, return the first available sg device
                // In a more sophisticated implementation, we could match by USB vendor/product
                Timber.i("Found SCSI generic device: ${sgDevices.first()}")
                return@withContext sgDevices.first()
            }
            
            // Fallback: Try to find SCSI CD-ROM device (sr*)
            val srDevices = findSrDevices()
            if (srDevices.isNotEmpty()) {
                Timber.i("Found SCSI CD-ROM device: ${srDevices.first()}")
                return@withContext srDevices.first()
            }
            
            // Additional fallback: Try /proc/partitions to find block devices
            Timber.d("Trying /proc/partitions as fallback...")
            val procDevices = findDevicesViaProcPartitions()
            if (procDevices.isNotEmpty()) {
                Timber.i("Found device via /proc/partitions: ${procDevices.first()}")
                return@withContext procDevices.first()
            }
            
            Timber.w("No device path found for USB device after trying all methods")
            Timber.w("  - Checked /dev/sg0-15: ${sgDevices.size} found")
            Timber.w("  - Checked /dev/sr0-15: ${srDevices.size} found")
            Timber.w("  - Checked /proc/partitions: ${procDevices.size} found")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error finding device path")
            null
        }
    }
    
    /**
     * Finds available SCSI generic devices (/dev/sg*).
     * These are preferred for optical drives as they provide low-level access.
     */
    private fun findSgDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            Timber.d("Scanning for SCSI generic devices (/dev/sg*)...")
            // Check common SCSI generic device paths
            for (i in 0..15) {
                val devicePath = "/dev/sg$i"
                try {
                    val deviceFile = File(devicePath)
                    val exists = deviceFile.exists()
                    val canRead = if (exists) {
                        try {
                            deviceFile.canRead()
                        } catch (e: SecurityException) {
                            Timber.d("  $devicePath: exists=true, canRead=false (SecurityException: ${e.message})")
                            false
                        } catch (e: Exception) {
                            Timber.d("  $devicePath: exists=true, canRead=false (${e.javaClass.simpleName}: ${e.message})")
                            false
                        }
                    } else {
                        false
                    }
                    
                    Timber.d("  Checking $devicePath: exists=$exists, canRead=$canRead")
                    
                    // Add device if it exists, even if canRead is false
                    // The native library might be able to access it even if Java cannot
                    if (exists) {
                        devices.add(devicePath)
                        if (canRead) {
                            Timber.d("Found accessible SCSI generic device: $devicePath")
                        } else {
                            Timber.d("Found SCSI generic device (not readable from Java, but native library may access): $devicePath")
                        }
                    }
                } catch (e: Exception) {
                    Timber.d("  Error checking $devicePath: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            Timber.d("Found ${devices.size} SCSI generic device(s)")
        } catch (e: Exception) {
            Timber.w(e, "Error scanning for SCSI generic devices")
        }
        return devices
    }
    
    /**
     * Finds available SCSI CD-ROM devices (/dev/sr*).
     * These are traditional CD-ROM device paths.
     */
    private fun findSrDevices(): List<String> {
        val devices = mutableListOf<String>()
        try {
            Timber.d("Scanning for SCSI CD-ROM devices (/dev/sr*)...")
            // Check common SCSI CD-ROM device paths
            for (i in 0..15) {
                val devicePath = "/dev/sr$i"
                try {
                    val deviceFile = File(devicePath)
                    val exists = deviceFile.exists()
                    val canRead = if (exists) {
                        try {
                            deviceFile.canRead()
                        } catch (e: SecurityException) {
                            Timber.d("  $devicePath: exists=true, canRead=false (SecurityException: ${e.message})")
                            false
                        } catch (e: Exception) {
                            Timber.d("  $devicePath: exists=true, canRead=false (${e.javaClass.simpleName}: ${e.message})")
                            false
                        }
                    } else {
                        false
                    }
                    
                    Timber.d("  Checking $devicePath: exists=$exists, canRead=$canRead")
                    
                    // Add device if it exists, even if canRead is false
                    // The native library might be able to access it even if Java cannot
                    if (exists) {
                        devices.add(devicePath)
                        if (canRead) {
                            Timber.d("Found accessible SCSI CD-ROM device: $devicePath")
                        } else {
                            Timber.d("Found SCSI CD-ROM device (not readable from Java, but native library may access): $devicePath")
                        }
                    }
                } catch (e: Exception) {
                    Timber.d("  Error checking $devicePath: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            Timber.d("Found ${devices.size} SCSI CD-ROM device(s)")
        } catch (e: Exception) {
            Timber.w(e, "Error scanning for SCSI CD-ROM devices")
        }
        return devices
    }
    
    /**
     * Tries to find block devices via /proc/partitions.
     * This can help identify optical drives even if /dev/sg* or /dev/sr* are not accessible.
     */
    private fun findDevicesViaProcPartitions(): List<String> {
        val devices = mutableListOf<String>()
        try {
            Timber.d("Scanning /proc/partitions for block devices...")
            val partitionsFile = File("/proc/partitions")
            if (!partitionsFile.exists() || !partitionsFile.canRead()) {
                Timber.d("Cannot read /proc/partitions")
                return devices
            }
            
            partitionsFile.readLines().forEachIndexed { index, line ->
                // Skip header lines
                if (index < 2) return@forEachIndexed
                
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val deviceName = parts[3]
                    // Look for optical drive devices (sr*, sg*)
                    if (deviceName.startsWith("sr") || deviceName.startsWith("sg")) {
                        val devicePath = "/dev/$deviceName"
                        Timber.d("Found potential optical device in /proc/partitions: $devicePath")
                        devices.add(devicePath)
                    }
                }
            }
            Timber.d("Found ${devices.size} device(s) via /proc/partitions")
        } catch (e: Exception) {
            Timber.w(e, "Error reading /proc/partitions")
        }
        return devices
    }
    
    /**
     * Unmounts a storage volume.
     * 
     * @param mountPoint The mount point to unmount
     * @return true if unmount was successful, false otherwise
     */
    suspend fun unmountVolume(mountPoint: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("Attempting to unmount volume: $mountPoint")
            
            val volumes = storageManager?.storageVolumes
            volumes?.forEach { volume ->
                val volumePath = volume.directory?.absolutePath
                if (volumePath == mountPoint || mountPoint.startsWith(volumePath ?: "")) {
                    try {
                        // Try to unmount using StorageManager API
                        // Note: This may require special permissions
                        Timber.d("Found matching volume, attempting unmount...")
                        // StorageManager.unmount() is not directly accessible
                        // We'll try alternative methods
                        Timber.w("Direct unmount via StorageManager not available, trying alternative methods")
                        return@withContext false
                    } catch (e: Exception) {
                        Timber.e(e, "Error unmounting volume")
                        return@withContext false
                    }
                }
            }
            
            Timber.w("Volume not found in StorageManager: $mountPoint")
            false
        } catch (e: Exception) {
            Timber.e(e, "Error during unmount operation")
            false
        }
    }
}
