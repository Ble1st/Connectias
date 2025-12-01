package com.ble1st.connectias.feature.usb.ui

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

/**
 * Activity that handles USB device attached intents.
 * Requests USB permission and navigates to the USB Dashboard fragment in the main app.
 */
class UsbDashboardActivity : AppCompatActivity() {
    
    companion object {
        private const val EXTRA_NAVIGATE_TO = "navigate_to"
        private const val DESTINATION_USB_DASHBOARD = "nav_usb_dashboard"
        private const val ACTION_USB_PERMISSION = "com.ble1st.connectias.feature.usb.USB_PERMISSION"
    }
    
    private var usbDevice: android.hardware.usb.UsbDevice? = null
    private var permissionReceiver: BroadcastReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("UsbDashboardActivity: onCreate - USB device attached")
        
        // Check if intent has USB device attached action
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            usbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    UsbManager.EXTRA_DEVICE,
                    android.hardware.usb.UsbDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.hardware.usb.UsbDevice>(
                    UsbManager.EXTRA_DEVICE
                )
            }
            usbDevice?.let { device ->
                Timber.i("USB device attached via intent: Vendor=0x%04X, Product=0x%04X",
                    device.vendorId, device.productId)
                
                // Request USB permission
                requestUsbPermission(device)
            } ?: run {
                Timber.w("USB device not found in intent, navigating to dashboard")
                navigateToDashboard()
            }
        } else {
            // No USB device in intent, just navigate to dashboard
            navigateToDashboard()
        }
    }
    
    private fun requestUsbPermission(device: android.hardware.usb.UsbDevice) {
        val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            Timber.e("USB Manager not available")
            navigateToDashboard()
            return
        }
        
        // Check if permission already granted
        if (usbManager.hasPermission(device)) {
            Timber.d("USB permission already granted")
            navigateToDashboard()
            return
        }
        
        // Register broadcast receiver for permission result
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val deviceExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
                        }
                        
                        if (deviceExtra != null && deviceExtra == device) {
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                Timber.i("USB permission granted by user")
                            } else {
                                Timber.w("USB permission denied by user")
                            }
                            // Unregister receiver
                            try {
                                unregisterReceiver(this)
                            } catch (e: Exception) {
                                Timber.e(e, "Error unregistering permission receiver")
                            }
                            permissionReceiver = null
                            navigateToDashboard()
                        }
                    }
                }
            }
        }
        
        try {
            // For Android 13+ (API 33+), need to specify receiver export flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(permissionReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(permissionReceiver, filter)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to register permission receiver")
            navigateToDashboard()
            return
        }
        
        // Request permission
        val requestCode = ((device.vendorId shl 16) or device.productId) and 0x7FFFFFFF
        val permissionIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        Timber.d("Requesting USB permission via system dialog")
        usbManager.requestPermission(device, permissionIntent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        permissionReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering permission receiver in onDestroy")
            }
        }
    }
    
    private fun navigateToDashboard() {
        
        // Navigate to MainActivity using package name (no direct class reference)
        // This avoids module dependency issues
        // packageName returns the application package (e.g., "com.ble1st.connectias")
        val appPackageName = packageName
        val mainActivityClassName = "$appPackageName.MainActivity"
        
        try {
            val mainIntent = Intent().apply {
                setClassName(appPackageName, mainActivityClassName)
                // Add flag to navigate to USB dashboard
                putExtra(EXTRA_NAVIGATE_TO, DESTINATION_USB_DASHBOARD)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
            }
            startActivity(mainIntent)
            Timber.d("Navigated to MainActivity with USB dashboard destination")
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "MainActivity not found, trying launcher fallback")
            // Fallback: Just start the launcher activity
            val launcherIntent = packageManager.getLaunchIntentForPackage(appPackageName)
            launcherIntent?.let {
                it.putExtra(EXTRA_NAVIGATE_TO, DESTINATION_USB_DASHBOARD)
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
                startActivity(it)
            } ?: run {
                Timber.e("Could not find launcher intent for package: $appPackageName")
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception when starting MainActivity - insufficient permissions")
            // Don't attempt fallback for security exceptions as it may be unsafe
        } catch (e: Exception) {
            Timber.e(e, "Failed to start MainActivity, trying fallback")
            // Fallback: Just start the launcher activity
            val launcherIntent = packageManager.getLaunchIntentForPackage(appPackageName)
            launcherIntent?.let {
                it.putExtra(EXTRA_NAVIGATE_TO, DESTINATION_USB_DASHBOARD)
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
                startActivity(it)
            } ?: run {
                Timber.e("Could not find launcher intent for package: $appPackageName")
            }
        }
        
        // Don't call finish() - let the system manage lifecycle via FLAG_ACTIVITY_NO_HISTORY
    }
}
