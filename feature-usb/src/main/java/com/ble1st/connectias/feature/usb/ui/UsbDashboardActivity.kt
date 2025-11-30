package com.ble1st.connectias.feature.usb.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber

/**
 * Activity that handles USB device attached intents.
 * Navigates to the USB Dashboard fragment in the main app.
 */
class UsbDashboardActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("UsbDashboardActivity: onCreate - USB device attached")
        
        // Check if intent has USB device attached action
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(
                android.hardware.usb.UsbManager.EXTRA_DEVICE
            )
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
                putExtra("navigate_to", "nav_usb_dashboard")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            Timber.d("Navigated to MainActivity with USB dashboard destination")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start MainActivity, trying fallback")
            // Fallback: Just start the launcher activity
            val launcherIntent = packageManager.getLaunchIntentForPackage(appPackageName)
            launcherIntent?.let {
                it.putExtra("navigate_to", "nav_usb_dashboard")
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(it)
            } ?: run {
                Timber.e("Could not find launcher intent for package: $appPackageName")
            }
        }
        
        // Finish this activity immediately
        finish()
    }
}
