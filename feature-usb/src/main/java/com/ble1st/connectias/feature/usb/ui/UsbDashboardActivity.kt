package com.ble1st.connectias.feature.usb.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

/**
 * Activity that handles USB device attached intents.
 * Navigates to the USB Dashboard fragment in the main app.
 */
class UsbDashboardActivity : AppCompatActivity() {
    
    companion object {
        private const val EXTRA_NAVIGATE_TO = "navigate_to"
        private const val DESTINATION_USB_DASHBOARD = "nav_usb_dashboard"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("UsbDashboardActivity: onCreate - USB device attached")
        
        // Check if intent has USB device attached action
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    android.hardware.usb.UsbManager.EXTRA_DEVICE,
                    android.hardware.usb.UsbDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.hardware.usb.UsbDevice>(
                    android.hardware.usb.UsbManager.EXTRA_DEVICE
                )
            }
            device?.let {
                Timber.i("USB device attached via intent: Vendor=0x%04X, Product=0x%04X",
                    it.vendorId, it.productId)
            }
        }
        
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
