package com.ble1st.connectias.hardware

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Manages runtime permission requests for Hardware Bridge
 * Coordinates between Background Service and MainActivity
 */
class PermissionRequestManager {
    
    companion object {
        private const val PERMISSION_REQUEST_TIMEOUT_SECONDS = 30L
        
        @Volatile
        private var instance: PermissionRequestManager? = null
        
        fun getInstance(): PermissionRequestManager {
            return instance ?: synchronized(this) {
                instance ?: PermissionRequestManager().also { instance = it }
            }
        }
    }
    
    // Pending permission requests with their latches
    private val pendingRequests = ConcurrentHashMap<String, PermissionRequest>()
    
    // Weak reference to current activity (set by MainActivity) to avoid static leak.
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    /** Current activity for permission dialogs. Use [setCurrentActivity] / [clearCurrentActivity]. */
    var currentActivity: Activity?
        get() = currentActivityRef?.get()
        set(value) {
            currentActivityRef = value?.let { WeakReference(it) }
        }
    
    data class PermissionRequest(
        val pluginId: String,
        val permission: String,
        val latch: CountDownLatch,
        var granted: Boolean = false
    )
    
    /**
     * Request permission from user (blocking call)
     * This is called from Hardware Bridge background thread
     * 
     * @param activity Activity to show permission dialog
     * @param pluginId Plugin requesting permission
     * @param permission Android permission string
     * @return true if granted, false if denied
     */
    fun requestPermissionBlocking(activity: Activity, pluginId: String, permission: String): Boolean {
        // Check if already granted
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            Timber.d("[PERMISSION] $permission already granted for $pluginId")
            return true
        }
        
        Timber.i("[PERMISSION] Requesting $permission for $pluginId")
        
        // Create request
        val requestKey = "$pluginId:$permission:${System.currentTimeMillis()}"
        val request = PermissionRequest(
            pluginId = pluginId,
            permission = permission,
            latch = CountDownLatch(1)
        )
        
        pendingRequests[requestKey] = request
        
        // Request on main thread
        activity.runOnUiThread {
            // Android requires requestCode >= 0. hashCode() can be negative.
            val requestCode = requestKey.hashCode() and 0x7FFFFFFF
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(permission),
                requestCode
            )
        }
        
        // Wait for result (blocking)
        val granted = try {
            val finished = request.latch.await(PERMISSION_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                Timber.w("[PERMISSION] Timeout waiting for $permission")
                false
            } else {
                request.granted
            }
        } catch (e: InterruptedException) {
            Timber.e(e, "[PERMISSION] Interrupted while waiting for $permission")
            false
        } finally {
            pendingRequests.remove(requestKey)
        }
        
        Timber.i("[PERMISSION] $permission for $pluginId: ${if (granted) "GRANTED" else "DENIED"}")
        return granted
    }
    
    /**
     * Handle permission result from MainActivity
     * Called from Activity.onRequestPermissionsResult
     */
    fun handlePermissionResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (permissions.isEmpty()) return

        permissions[0]
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        
        // Find matching request
        val matchingEntry = pendingRequests.entries.find { 
            (it.key.hashCode() and 0x7FFFFFFF) == requestCode
        }
        
        if (matchingEntry != null) {
            Timber.d("[PERMISSION] Result for ${matchingEntry.value.permission}: ${if (granted) "GRANTED" else "DENIED"}")
            matchingEntry.value.granted = granted
            matchingEntry.value.latch.countDown()
        } else {
            Timber.w("[PERMISSION] No pending request found for code $requestCode")
        }
    }
}
