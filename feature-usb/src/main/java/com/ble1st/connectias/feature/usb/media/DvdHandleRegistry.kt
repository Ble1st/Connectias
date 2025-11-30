package com.ble1st.connectias.feature.usb.media

import com.ble1st.connectias.feature.usb.models.DvdInfo
import com.ble1st.connectias.feature.usb.native.DvdNative
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for managing DVD handles to allow ContentProvider access.
 * 
 * This registry stores DVD handles keyed by mount point, allowing the ContentProvider
 * to access DVD data even when called from outside the app context.
 * 
 * **Lifecycle Responsibility:**
 * - This registry owns and is responsible for closing native handles
 * - When a handle is unregistered or replaced, the registry automatically closes the native handle
 * - Callers should not close handles that are registered here; the registry manages cleanup
 * 
 * Thread-safe: All operations are synchronized.
 */
@Singleton
class DvdHandleRegistry @Inject constructor() {
    
    private val lock = Any()
    private val dvdHandles = mutableMapOf<String, Long>()
    
    /**
     * Registers a DVD handle for the given mount point.
     * 
     * If a handle is already registered for this mount point, the old handle is closed
     * before registering the new one. All operations are atomic under the same lock.
     * 
     * @param mountPoint The mount point path (e.g., "/mnt/dvd")
     * @param handle The DVD handle from DvdNative.dvdOpen()
     */
    fun register(mountPoint: String, handle: Long) {
        synchronized(lock) {
            if (handle <= 0) {
                Timber.w("Attempted to register invalid DVD handle: $handle for mount point: $mountPoint")
                return
            }
            
            // Check for existing handle and cleanup if present
            val oldHandle = dvdHandles[mountPoint]
            if (oldHandle != null && oldHandle != handle) {
                // Check if the old handle is still referenced by other mount points
                val isHandleUsedElsewhere = dvdHandles.any { (otherMountPoint, otherHandle) ->
                    otherMountPoint != mountPoint && otherHandle == oldHandle
                }
                
                if (isHandleUsedElsewhere) {
                    Timber.w("Skipping close of DVD handle $oldHandle for mount point: $mountPoint - handle is still in use by other mount point(s)")
                } else {
                    Timber.w("Replacing existing DVD handle $oldHandle with $handle for mount point: $mountPoint")
                    try {
                        DvdNative.dvdClose(oldHandle)
                    } catch (e: Exception) {
                        Timber.e(e, "Error closing old DVD handle $oldHandle during registration")
                    }
                }
            }
            
            dvdHandles[mountPoint] = handle
            Timber.d("Registered DVD handle $handle for mount point: $mountPoint")
        }
    }
    
    /**
     * Unregisters a DVD handle for the given mount point.
     * 
     * This method removes the handle from the registry and closes the native handle
     * to prevent resource leaks. The cleanup is performed atomically under the same lock.
     * 
     * @param mountPoint The mount point path
     */
    fun unregister(mountPoint: String) {
        synchronized(lock) {
            val handle = dvdHandles.remove(mountPoint)
            if (handle != null) {
                try {
                    DvdNative.dvdClose(handle)
                    Timber.d("Unregistered and closed DVD handle $handle for mount point: $mountPoint")
                } catch (e: Exception) {
                    Timber.e(e, "Error closing DVD handle $handle during unregistration")
                }
            } else {
                Timber.w("Attempted to unregister non-existent DVD handle for mount point: $mountPoint")
            }
        }
    }
    
    /**
     * Gets the DVD handle for the given mount point.
     * 
     * @param mountPoint The mount point path
     * @return The DVD handle, or null if not registered
     */
    fun getHandle(mountPoint: String): Long? {
        synchronized(lock) {
            return dvdHandles[mountPoint]
        }
    }
    
    /**
     * Checks if a DVD handle is registered for the given mount point.
     * 
     * @param mountPoint The mount point path
     * @return True if a handle is registered, false otherwise
     */
    fun isRegistered(mountPoint: String): Boolean {
        synchronized(lock) {
            return dvdHandles.containsKey(mountPoint)
        }
    }
    
    /**
     * Registers a DVD from DvdInfo.
     * 
     * @param dvdInfo The DVD information containing handle and mount point
     */
    fun registerDvd(dvdInfo: DvdInfo) {
        register(dvdInfo.mountPoint, dvdInfo.handle)
    }
    
    /**
     * Unregisters a DVD from DvdInfo.
     * 
     * @param dvdInfo The DVD information containing mount point
     */
    fun unregisterDvd(dvdInfo: DvdInfo) {
        unregister(dvdInfo.mountPoint)
    }
    
    /**
     * Clears all registered DVD handles.
     * Should be called during app shutdown or cleanup.
     */
    fun clear() {
        synchronized(lock) {
            val count = dvdHandles.size
            // Consider: dvdHandles.values.forEach { DvdNative.dvdClose(it) }
            dvdHandles.clear()
            Timber.d("Cleared $count DVD handles from registry")
        }
    }
}
