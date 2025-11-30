package com.ble1st.connectias.feature.usb.detection

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.ble1st.connectias.feature.usb.models.UsbDevice as UsbDeviceModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects USB devices automatically via BroadcastReceiver.
 */
@Singleton
class UsbDeviceDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    
    private val _detectedDevices = MutableStateFlow<List<UsbDeviceModel>>(emptyList())
    val detectedDevices: StateFlow<List<UsbDeviceModel>> = _detectedDevices.asStateFlow()
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let {
                        Timber.i("USB device attached: Vendor=0x%04X, Product=0x%04X", 
                            it.vendorId, it.productId)
                        val deviceModel = convertToModel(it)
                        _detectedDevices.value = _detectedDevices.value + deviceModel
                        Timber.d("USB device added to detected list: ${deviceModel.product}")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let {
                        Timber.i("USB device detached: Vendor=0x%04X, Product=0x%04X", 
                            it.vendorId, it.productId)
                        // Generate uniqueId for the detached device to match against our list
                        val detachedUniqueId = try {
                            it.serialNumber ?: try {
                                it.deviceName
                            } catch (e: SecurityException) {
                                null
                            } ?: "${it.vendorId}_${it.productId}_${it.deviceId}"
                        } catch (e: Exception) {
                            "${it.vendorId}_${it.productId}_${it.deviceId}"
                        }
                        // Remove by uniqueId to ensure only the specific detached device is removed
                        _detectedDevices.value = _detectedDevices.value.filter { d ->
                            d.uniqueId != detachedUniqueId
                        }
                        Timber.d("USB device removed from detected list (uniqueId: $detachedUniqueId)")
                    }
                }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling USB device broadcast: ${intent.action}")
            }
        }
    }
    
    private var isReceiverRegistered = false
    
    /**
     * Registers the BroadcastReceiver for automatic USB device detection.
     * Note: The activity parameter is currently unused. The receiver is registered with
     * Application context to persist across activity lifecycle. If you need activity-scoped
     * registration, use activity.registerReceiver() instead.
     */
    fun registerReceiver(activity: Activity) {
        if (isReceiverRegistered) {
            Timber.w("USB BroadcastReceiver already registered, skipping")
            return
        }
        
        try {
            Timber.d("Registering USB BroadcastReceiver...")
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            
            // For Android 13+ (API 33+), need to specify receiver export flag
            // System USB broadcasts (ACTION_USB_DEVICE_ATTACHED/DETACHED) require RECEIVER_EXPORTED
            // to receive broadcasts from the system
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(broadcastReceiver, filter)
            }
            isReceiverRegistered = true
            Timber.d("USB BroadcastReceiver registered successfully")
            
            // Also enumerate currently connected devices
            enumerateCurrentDevices()
        } catch (e: Exception) {
            Timber.e(e, "Failed to register USB BroadcastReceiver")
            isReceiverRegistered = false
        }
    }
    
    /**
     * Unregisters the BroadcastReceiver.
     */
    fun unregisterReceiver() {
        if (!isReceiverRegistered) {
            Timber.w("USB BroadcastReceiver was not registered, skipping unregister")
            return
        }
        
        try {
            Timber.d("Unregistering USB BroadcastReceiver...")
            context.unregisterReceiver(broadcastReceiver)
            isReceiverRegistered = false
            Timber.d("USB BroadcastReceiver unregistered successfully")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "USB BroadcastReceiver was not registered (IllegalArgumentException)")
            isReceiverRegistered = false
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering USB BroadcastReceiver")
            // Don't update flag on unexpected exceptions - receiver state is unknown
        }
    }
    
    /**
     * Enumerates currently connected USB devices.
     */
    private fun enumerateCurrentDevices() {
        try {
            Timber.d("Enumerating currently connected USB devices...")
            val devices = usbManager.deviceList.values.map { convertToModel(it) }
            _detectedDevices.value = devices
            Timber.i("Found ${devices.size} currently connected USB devices")
            devices.forEach { device ->
                Timber.d("Device: ${device.product} (Vendor=0x%04X, Product=0x%04X, MassStorage=${device.isMassStorage})",
                    device.vendorId, device.productId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to enumerate current USB devices")
        }
    }
    
    /**
     * Updates device information if permission was granted.
     * This should be called after the user grants USB permission.
     */
    fun refreshDeviceInfo(vendorId: Int, productId: Int) {
        try {
            val androidDevice = usbManager.deviceList.values.find { d ->
                d.vendorId == vendorId && d.productId == productId
            }
            
            androidDevice?.let { device ->
                if (usbManager.hasPermission(device)) {
                    Timber.d("Permission granted, refreshing device info for Vendor=0x%04X, Product=0x%04X",
                        vendorId, productId)
                    val updatedDevice = convertToModel(device)
                    
                    // Update the device in the list
                    _detectedDevices.value = _detectedDevices.value.map { existing ->
                        if (existing.vendorId == vendorId && existing.productId == productId) {
                            updatedDevice
                        } else {
                            existing
                        }
                    }
                    Timber.d("Device info refreshed with permission-granted data")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing device info")
        }
    }
    
    /**
     * Converts Android UsbDevice to UsbDeviceModel.
     * Handles SecurityException when accessing serial number, manufacturer, or product name
     * if the app doesn't have permission.
     */
    private fun convertToModel(device: UsbDevice): UsbDeviceModel {
        // Check if we have permission first
        val hasPermission = try {
            usbManager.hasPermission(device)
        } catch (e: Exception) {
            Timber.w(e, "Error checking USB permission")
            false
        }
        
        // Safely get serial number - requires permission
        val serialNumber = if (hasPermission) {
            try {
                device.serialNumber
            } catch (e: SecurityException) {
                Timber.w(e, "SecurityException accessing serial number despite permission check")
                null
            }
        } else {
            Timber.d("No permission to access serial number for device Vendor=0x%04X, Product=0x%04X",
                device.vendorId, device.productId)
            null
        }
        
        // Safely get manufacturer name - may require permission
        val manufacturer = if (hasPermission) {
            try {
                device.manufacturerName
            } catch (e: SecurityException) {
                Timber.w(e, "SecurityException accessing manufacturer name despite permission check")
                null
            }
        } else {
            null
        }
        
        // Safely get product name - may require permission
        val product = if (hasPermission) {
            try {
                device.productName
            } catch (e: SecurityException) {
                Timber.w(e, "SecurityException accessing product name despite permission check")
                null
            }
        } else {
            null
        }
        
        // Check if device is Mass Storage Class
        // 1. Check device class
        val isDeviceClassMassStorage = device.deviceClass == UsbDeviceModel.USB_CLASS_MASS_STORAGE
        
        // 2. Check interfaces (many devices have Mass Storage as interface, not device class)
        var hasMassStorageInterface = false
        var interfaceCount = 0
        try {
            interfaceCount = device.interfaceCount
            Timber.d("Device Vendor=0x%04X, Product=0x%04X has $interfaceCount interfaces, DeviceClass=${device.deviceClass}",
                device.vendorId, device.productId)
            
            // Try to check interfaces even without permission (may work for some info)
            for (i in 0 until interfaceCount) {
                try {
                    val usbInterface = device.getInterface(i)
                    val interfaceClass = usbInterface.interfaceClass
                    val interfaceSubclass = usbInterface.interfaceSubclass
                    val interfaceProtocol = usbInterface.interfaceProtocol
                    
                    Timber.d("  Interface $i: Class=$interfaceClass, Subclass=$interfaceSubclass, Protocol=$interfaceProtocol")
                    
                    if (interfaceClass == UsbDeviceModel.USB_CLASS_MASS_STORAGE) {
                        hasMassStorageInterface = true
                        Timber.d("Found Mass Storage interface at index $i for device Vendor=0x%04X, Product=0x%04X",
                            device.vendorId, device.productId)
                    }
                } catch (e: SecurityException) {
                    if (hasPermission) {
                        Timber.w(e, "SecurityException accessing interface $i despite permission check")
                    } else {
                        Timber.d("Cannot access interface $i without permission")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error accessing interface $i")
                }
            }
        } catch (e: SecurityException) {
            if (hasPermission) {
                Timber.w(e, "SecurityException accessing interface count despite permission check")
            } else {
                Timber.d("Cannot access interface count without permission for device Vendor=0x%04X, Product=0x%04X",
                    device.vendorId, device.productId)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error checking USB interfaces")
        }
        
        // 3. Check for known DVD/CD drive indicators in product/manufacturer names
        val productNameLower = product?.lowercase() ?: ""
        val manufacturerNameLower = manufacturer?.lowercase() ?: ""
        val hasDvdCdIndicators = productNameLower.contains("dvd") || 
                                 productNameLower.contains("cd") || 
                                 productNameLower.contains("optical") ||
                                 productNameLower.contains("disc") ||
                                 manufacturerNameLower.contains("dvd") ||
                                 manufacturerNameLower.contains("cd") ||
                                 manufacturerNameLower.contains("optical")
        
        val isMassStorage = isDeviceClassMassStorage || hasMassStorageInterface || hasDvdCdIndicators
        
        if (isMassStorage && !isDeviceClassMassStorage) {
            Timber.d("Device detected as Mass Storage via interface or name indicators: Vendor=0x%04X, Product=0x%04X, " +
                    "DeviceClass=${device.deviceClass}, HasInterface=$hasMassStorageInterface, HasIndicators=$hasDvdCdIndicators",
                device.vendorId, device.productId)
        }
        
        // Generate unique identifier: use serialNumber if available, otherwise use deviceName/deviceId
        val uniqueId = serialNumber ?: run {
            // Use deviceName if available (contains bus/port info), otherwise fallback to deviceId
            val deviceName = try {
                device.deviceName
            } catch (e: SecurityException) {
                null
            }
            deviceName ?: "${device.vendorId}_${device.productId}_${device.deviceId}"
        }
        
        return UsbDeviceModel(
            vendorId = device.vendorId,
            productId = device.productId,
            deviceClass = device.deviceClass,
            deviceSubclass = device.deviceSubclass,
            deviceProtocol = device.deviceProtocol,
            serialNumber = serialNumber,
            manufacturer = manufacturer,
            product = product,
            version = device.version,
            isMassStorage = isMassStorage,
            uniqueId = uniqueId
        )
    }
}
