package com.ble1st.connectias.feature.usb.media

import com.ble1st.connectias.feature.usb.models.DvdInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for managing DVD handles to allow ContentProvider access.
 * 
 * This registry stores DVD handles keyed by mount point, allowing the ContentProvider
 * to access DVD data even when called from outside the app context.
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
     * @param mountPoint The mount point path (e.g., "/mnt/dvd")
     * @param handle The DVD handle from DvdNative.dvdOpen()
     */
    fun register(mountPoint: String, handle: Long) {
        synchronized(lock) {
            if (handle <= 0) {
                Timber.w("Attempted to register invalid DVD handle: $handle for mount point: $mountPoint")
                return
            }
            dvdHandles[mountPoint] = handle
            Timber.d("Registered DVD handle $handle for mount point: $mountPoint")
        }
    }
    
    /**
     * Unregisters a DVD handle for the given mount point.
     * 
     * @param mountPoint The mount point path
     */
    fun unregister(mountPoint: String) {
        synchronized(lock) {
            val handle = dvdHandles.remove(mountPoint)
            if (handle != null) {
                Timber.d("Unregistered DVD handle $handle for mount point: $mountPoint")
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
            dvdHandles.clear()
            Timber.d("Cleared $count DVD handles from registry")
        }
    }
}
