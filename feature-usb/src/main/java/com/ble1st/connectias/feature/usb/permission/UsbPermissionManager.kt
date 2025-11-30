package com.ble1st.connectias.feature.usb.permission

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.ble1st.connectias.feature.usb.models.UsbDevice as UsbDeviceModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages USB permission requests and status.
 */
@Singleton
class UsbPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: throw IllegalStateException("USB service not available")
    }
    
    companion object {
        private const val ACTION_USB_PERMISSION = "com.ble1st.connectias.feature.usb.USB_PERMISSION"
    }
    
    /**
     * Checks if permission is granted for a USB device.
     */
    fun hasPermission(device: UsbDeviceModel): Boolean {
        return try {
            // Try to find matching Android UsbDevice
            val androidDevice = usbManager.deviceList.values.find { d ->
                d.vendorId == device.vendorId && d.productId == device.productId
            }
            androidDevice?.let { usbManager.hasPermission(it) } ?: false
        } catch (e: Exception) {
            Timber.e(e, "Error checking USB permission")
            false
        }
    }
    
    /**
     * Requests permission for a USB device.
     * @return Flow that emits true when permission is granted, false when denied
     */
    fun requestPermission(device: UsbDeviceModel, activity: Activity): Flow<Boolean> = callbackFlow {
        Timber.d("Requesting USB permission for device: Vendor=0x%04X, Product=0x%04X", 
            device.vendorId, device.productId)
        
        val androidDevice = usbManager.deviceList.values.find { d ->
            d.vendorId == device.vendorId && d.productId == device.productId
        }
        
        if (androidDevice == null) {
            Timber.w("USB device not found in device list")
            trySend(false)
            close()
            return@callbackFlow
        }
        
        // Initial permission check
        if (usbManager.hasPermission(androidDevice)) {
            Timber.d("USB permission already granted")
            trySend(true)
            close()
            return@callbackFlow
        }
        
        // Generate unique request code per device to avoid PendingIntent conflicts
        // Use vendorId and productId combination to create unique identifier
        val requestCode = ((device.vendorId shl 16) or device.productId) and 0x7FFFFFFF // Mask to positive int
        
        val permissionIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        var permissionReceived = false
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        if (permissionReceived) {
                            return
                        }
                        val deviceExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        
                        if (deviceExtra?.vendorId == device.vendorId && 
                            deviceExtra.productId == device.productId) {
                            permissionReceived = true
                            Timber.i("USB permission ${if (granted) "granted" else "denied"} for device")
                            trySend(granted)
                            close()
                        }
                    }
                }
            }
        }
        
        // Register receiver on activity context (not application context) to match lifecycle
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, filter)
        }
        
        // Re-check permission immediately after registering receiver to handle race condition
        // where permission might have been granted externally between initial check and registration
        if (usbManager.hasPermission(androidDevice)) {
            Timber.d("USB permission granted after receiver registration (race condition handled)")
            permissionReceived = true
            try {
                activity.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering permission receiver")
            }
            trySend(true)
            close()
            return@callbackFlow
        }
        
        Timber.d("Requesting USB permission via system dialog")
        usbManager.requestPermission(androidDevice, permissionIntent)
        
        // Launch timeout job to prevent indefinite waiting if broadcast is missed
        val timeoutJob = launch {
            delay(30000L) // 30 second timeout
            synchronized(receiver) {
                if (!permissionReceived) {
                    Timber.w("USB permission request timed out after 30 seconds")
                    permissionReceived = true
                    try {
                        activity.unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        Timber.e(e, "Error unregistering permission receiver on timeout")
                    }
                    trySend(false)
                    close()
                }
            }
        }
        
        awaitClose {
            timeoutJob.cancel()
            try {
                activity.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering permission receiver")
            }
        }
    }
}
