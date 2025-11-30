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
            Timber.d("Scanning ${volumes?.size ?: 0} storage volumes for device Vendor=0x%04X, Product=0x%04X",
                device.vendorId, device.productId)
            
            volumes?.forEachIndexed { index, volume ->
                val isRemovable = volume.isRemovable
                val directory = volume.directory?.absolutePath
                val state = try {
                    storageManager?.getVolumeState(volume)
                } catch (e: Exception) {
                    "unknown"
                }
                
                Timber.d("  Volume $index: removable=$isRemovable, path=$directory, state=$state")
                
                // Check if volume is removable and mounted
                if (isRemovable && directory != null) {
                    val dir = File(directory)
                    if (dir.exists() && dir.isDirectory && dir.canRead()) {
                        Timber.i("Found removable storage volume: $directory (state=$state)")
                        return directory
                    } else {
                        Timber.d("Volume directory exists but not accessible: $directory")
                    }
                }
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
                    // Also check for subdirectories (e.g., /mnt/media_rw/XXXX-XXXX)
                    try {
                        val subdirs = dir.listFiles()?.filter { it.isDirectory }
                        subdirs?.forEach { subdir ->
                            Timber.d("    Subdirectory: ${subdir.absolutePath}")
                            if (subdir.canRead()) {
                                Timber.i("Found potential mount point: ${subdir.absolutePath}")
                                return subdir.absolutePath
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error listing subdirectories in $basePath")
                    }
                    
                    Timber.i("Found potential mount point: $basePath")
                    return basePath
                }
            } else {
                Timber.d("  Checking $basePath: exists=false")
            }
        }
        
        Timber.w("No mount point found in common paths")
        return null
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
