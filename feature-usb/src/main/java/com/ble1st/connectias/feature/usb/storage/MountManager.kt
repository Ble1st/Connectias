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
            val fallbackMountPoint = findMountPointViaScan(device)
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
                
                // Try to get UUID for matching
                val uuid = try {
                    volume.uuid
                } catch (e: Exception) {
                    null
                }
                
                // Try to get description for matching
                val description = try {
                    volume.getDescription(context)
                } catch (e: Exception) {
                    null
                }
                
                Timber.d("  Volume $index: removable=$isRemovable, path=$directory, state=$state, uuid=$uuid, description=$description")
                
                // Check if volume is removable and mounted
                if (isRemovable && directory != null) {
                    val dir = File(directory)
                    if (dir.exists() && dir.isDirectory && dir.canRead()) {
                        candidateVolumes.add(Pair(volume, directory))
                        Timber.d("  Added candidate volume: $directory")
                    } else {
                        Timber.d("Volume directory exists but not accessible: $directory")
                    }
                }
            }
            
            // If only one candidate, return it (safe fallback)
            if (candidateVolumes.size == 1) {
                val mountPoint = candidateVolumes[0].second
                Timber.i("Found single removable storage volume: $mountPoint")
                return mountPoint
            }
            
            // If multiple candidates, try to match by device identifiers
            if (candidateVolumes.size > 1) {
                Timber.d("Multiple removable volumes found (${candidateVolumes.size}), attempting device matching...")
                
                // Try to match by UUID (filesystem UUID might correlate with device)
                if (device.serialNumber != null) {
                    for ((volume, directory) in candidateVolumes) {
                        try {
                            val volumeUuid = volume.uuid
                            // Check if UUID or description contains device identifiers
                            if (volumeUuid != null && device.serialNumber.isNotEmpty()) {
                                // UUID matching is not reliable, but we can try description matching
                                val volumeDescription = try {
                                    volume.getDescription(context) ?: ""
                                } catch (e: Exception) {
                                    ""
                                }
                                
                                // Check if description contains vendor/product info or serial
                                val matches = volumeDescription.contains(device.vendorId.toString(16), ignoreCase = true) ||
                                        volumeDescription.contains(device.productId.toString(16), ignoreCase = true) ||
                                        (device.serialNumber.isNotEmpty() && volumeDescription.contains(device.serialNumber, ignoreCase = true))
                                
                                if (matches) {
                                    Timber.i("Matched volume by description: $directory")
                                    return directory
                                }
                            }
                        } catch (e: Exception) {
                            Timber.d(e, "Error checking volume UUID/description")
                        }
                    }
                }
                
                // If no match found, log warning and return first candidate as fallback
                // This is not ideal but better than returning wrong device
                Timber.w("Could not uniquely match device to volume, returning first candidate. " +
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
    
    private fun findMountPointViaScan(device: UsbDevice? = null): String? {
        val commonPaths = listOf(
            "/storage/usbdisk",
            "/storage/usb",
            "/mnt/usbdisk",
            "/mnt/usb",
            "/mnt/media_rw",
            "/storage/emulated/0",
            "/sdcard"
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
        
        // Check if directory has subdirectories (heuristic for container)
        try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                val subdirs = dir.listFiles()?.filter { it.isDirectory }
                // If it has subdirectories, it's likely a container
                if (subdirs != null && subdirs.isNotEmpty()) {
                    Timber.d("  Path $path appears to be a container (has ${subdirs.size} subdirectories)")
                    return true
                }
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
}
