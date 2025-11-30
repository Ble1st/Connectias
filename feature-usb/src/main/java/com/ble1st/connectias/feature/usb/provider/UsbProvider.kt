package com.ble1st.connectias.feature.usb.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.models.UsbTransfer
import com.ble1st.connectias.feature.usb.native.UsbClass
import com.ble1st.connectias.feature.usb.native.UsbError
import com.ble1st.connectias.feature.usb.native.UsbNative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result type for USB operations that can fail.
 */
sealed class UsbResult<out T> {
    data class Success<T>(val data: T) : UsbResult<T>()
    data class Failure(val error: Throwable) : UsbResult<Nothing>()
}

/**
 * Provider for USB device operations.
 */
@Singleton
class UsbProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    // Persistent mapping for devices without stable identifiers
    private val deviceMappingPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                "usb_device_mapping",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            // EncryptedSharedPreferences failed due to security/keystore issues
            // Attempt to detect and handle corrupted encrypted file before falling back
            val remediation = handleCorruptedEncryptedFile("usb_device_mapping")
            Timber.w(e, "Failed to create EncryptedSharedPreferences for device mapping (GeneralSecurityException). " +
                    "Remediation: $remediation. Using plain SharedPreferences with separate filename.")
            context.getSharedPreferences("usb_device_mapping_plain", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            // Other exceptions (IO, etc.) - check if encrypted file exists and might be corrupted
            val remediation = handleCorruptedEncryptedFile("usb_device_mapping")
            Timber.w(e, "Failed to create EncryptedSharedPreferences for device mapping. " +
                    "Remediation: $remediation. Using plain SharedPreferences with separate filename.")
            context.getSharedPreferences("usb_device_mapping_plain", Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Attempts to detect and handle a corrupted encrypted SharedPreferences file.
     * If the encrypted file exists, it may contain encrypted bytes that would be unreadable
     * as plain SharedPreferences. This method attempts to delete or rename the corrupted file.
     * 
     * @param prefsName The name of the SharedPreferences file (without .xml extension)
     * @return String describing the remediation action taken
     */
    private fun handleCorruptedEncryptedFile(prefsName: String): String {
        return try {
            val prefsFile = File(context.filesDir.parent, "shared_prefs/$prefsName.xml")
            if (prefsFile.exists()) {
                // Encrypted file exists - attempt to detect if it's corrupted by trying a minimal read
                // If it's encrypted, reading as plain XML will fail or return garbage
                try {
                    val testPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    val testKey = "__corruption_test__"
                    // Try to read a non-existent key - if file is encrypted, this might throw or return garbage
                    testPrefs.getString(testKey, null)
                    // If we get here without exception, file might be readable as plain (or empty)
                    // But since EncryptedSharedPreferences.create() failed, it's likely corrupted
                    // Delete it to prevent confusion
                    val deleted = prefsFile.delete()
                    if (deleted) {
                        "Deleted potentially corrupted encrypted file: $prefsName.xml"
                    } else {
                        "Failed to delete potentially corrupted encrypted file: $prefsName.xml"
                    }
                } catch (readException: Exception) {
                    // File is definitely corrupted or encrypted - delete it
                    val deleted = prefsFile.delete()
                    if (deleted) {
                        "Deleted corrupted encrypted file (read test failed): $prefsName.xml"
                    } else {
                        "Failed to delete corrupted encrypted file (read test failed): $prefsName.xml"
                    }
                }
            } else {
                "Encrypted file does not exist, no remediation needed"
            }
        } catch (e: Exception) {
            "Error during corruption detection: ${e.message}"
        }
    }
    
    /**
     * Computes a stable hash-based identifier from device descriptor fields.
     * Uses SHA-256 hash of stable descriptor fields (vendorId, productId, manufacturer, product, deviceClass).
     * 
     * @param native The native USB device descriptor
     * @return Hexadecimal hash string (64 characters for SHA-256)
     */
    private fun computeStableHash(native: com.ble1st.connectias.feature.usb.native.UsbDeviceNative): String {
        val descriptorString = buildString {
            append("vid:${native.vendorId}")
            append("|pid:${native.productId}")
            append("|class:${native.deviceClass.value}")
            native.manufacturer?.let { append("|mfg:$it") }
            native.product?.let { append("|prod:$it") }
        }
        
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(descriptorString.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to compute hash for device descriptor")
            // Fallback to simple concatenation if hashing fails
            descriptorString.replace("|", "_").replace(":", "_")
        }
    }
    
    /**
     * Generates or retrieves a persistent UUID for a device fingerprint.
     * Uses encrypted SharedPreferences to persist the mapping across app restarts.
     * 
     * @param fingerprint Temporary device fingerprint (e.g., hash of available fields)
     * @return Stable UUID string for the device
     */
    private fun getOrCreatePersistentId(fingerprint: String): String {
        val existingId = deviceMappingPrefs.getString(fingerprint, null)
        if (existingId != null) {
            return existingId
        }
        
        // Generate new UUID and persist it
        val newId = UUID.randomUUID().toString()
        deviceMappingPrefs.edit().putString(fingerprint, newId).apply()
        Timber.d("Generated new persistent ID for device fingerprint: $fingerprint -> $newId")
        return newId
    }
    
    /**
     * Creates a stable unique identifier for a USB device.
     * Priority order:
     * 1. serialNumber (if available) - most stable
     * 2. Hash of stable descriptor fields (vendorId, productId, manufacturer, product, deviceClass)
     * 3. Persistent mapping from device fingerprint to generated UUID (for devices with no stable fields)
     * 
     * Note: Bus/port information is not available from the native layer, so we rely on descriptor fields.
     * For devices without serial numbers and with identical descriptors, the hash will be the same,
     * which may cause identification issues if multiple identical devices are connected.
     * 
     * @param native The native USB device descriptor
     * @return Stable unique identifier string
     */
    private fun createStableUniqueId(native: com.ble1st.connectias.feature.usb.native.UsbDeviceNative): String {
        // Priority 1: Use serial number if available (most stable)
        if (!native.serialNumber.isNullOrBlank()) {
            return native.serialNumber
        }
        
        // Priority 2: Compute hash from stable descriptor fields
        val hasStableFields = native.vendorId != 0 || native.productId != 0 || 
                             !native.manufacturer.isNullOrBlank() || !native.product.isNullOrBlank()
        
        if (hasStableFields) {
            val hash = computeStableHash(native)
            Timber.d("Using hash-based identifier for device: Vendor=0x%04X, Product=0x%04X, Hash=$hash",
                native.vendorId, native.productId)
            return "hash_$hash"
        }
        
        // Priority 3: Fallback to persistent mapping (for devices with no stable fields)
        // This handles edge cases where device has no serial number and minimal descriptor info
        val minimalFingerprint = "vid:${native.vendorId}|pid:${native.productId}|class:${native.deviceClass.value}"
        val persistentId = getOrCreatePersistentId(minimalFingerprint)
        Timber.w("Using persistent mapping for device with minimal descriptor: Vendor=0x%04X, Product=0x%04X, ID=$persistentId",
            native.vendorId, native.productId)
        return "persistent_$persistentId"
    }
    
    /**
     * Enumerates all connected USB devices.
     * @return Result containing list of devices or error
     */
    suspend fun enumerateDevices(): UsbResult<List<UsbDevice>> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting USB device enumeration...")
            val nativeDevices = UsbNative.enumerateDevices().toList()
            Timber.d("Native enumeration returned ${nativeDevices.size} devices")
            
            val devices = nativeDevices.map { native ->
                // Create stable unique identifier using hash-based approach
                val uniqueId = createStableUniqueId(native)
                
                UsbDevice(
                    vendorId = native.vendorId,
                    productId = native.productId,
                    deviceClass = native.deviceClass.value,
                    deviceSubclass = 0, // Not available from native layer
                    deviceProtocol = 0, // Not available from native layer
                    serialNumber = native.serialNumber,
                    manufacturer = native.manufacturer,
                    product = native.product,
                    version = null, // Not available from native layer
                    uniqueId = uniqueId
                )
            }
            
            Timber.i("USB device enumeration complete: ${devices.size} devices found")
            devices.forEach { device ->
                Timber.d("Device: Vendor=0x%04X, Product=0x%04X, Class=%d, MassStorage=%b",
                    device.vendorId, device.productId, device.deviceClass, device.isMassStorage)
            }
            
            UsbResult.Success(devices)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enumerate USB devices")
            UsbResult.Failure(e)
        }
    }
    
    /**
     * Opens a USB device.
     * Note: This method uses only vendorId and productId, which may not uniquely identify
     * multiple identical devices. Consider using a unique device identifier (e.g., serialNumber
     * or bus/port combination) when available.
     * 
     * @param device USB device to open
     * @return Device handle, or -1 on error
     */
    suspend fun openDevice(device: UsbDevice): Long = withContext(Dispatchers.IO) {
        try {
            Timber.d("Opening USB device: Vendor=0x%04X, Product=0x%04X, Serial=${device.serialNumber}", 
                device.vendorId, device.productId)
            val handle = UsbNative.openDevice(device.vendorId, device.productId)
            if (handle >= 0) {
                Timber.i("USB device opened successfully, handle: $handle")
            } else {
                Timber.w("Failed to open USB device, handle: $handle")
            }
            handle
        } catch (e: Exception) {
            Timber.e(e, "Failed to open USB device")
            throw e
        }
    }
    
    /**
     * Closes a USB device.
     */
    suspend fun closeDevice(handle: Long) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Closing USB device, handle: $handle")
            UsbNative.closeDevice(handle)
            Timber.d("USB device closed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to close USB device")
        }
    }
    
    /**
     * Performs a bulk transfer.
     * For IN (read) transfers, returns only the bytes actually read.
     * For OUT (write) transfers, returns the original data buffer.
     */
    suspend fun bulkTransfer(handle: Long, endpoint: Int, data: ByteArray): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            val isInEndpoint = (endpoint and 0x80) != 0
            Timber.d("Bulk transfer: handle=$handle, endpoint=0x%02X, length=${data.size}, direction=${if (isInEndpoint) "IN" else "OUT"}")
            
            val bytesTransferred = UsbNative.bulkTransfer(handle, endpoint, data)
            
            val resultData: ByteArray
            val status: UsbTransfer.TransferStatus
            
            when {
                bytesTransferred < 0 -> {
                    // Error occurred
                    val errorMsg = UsbError.errorCodeToString(bytesTransferred)
                    Timber.e("Bulk transfer failed: $errorMsg (code: $bytesTransferred)")
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = when (bytesTransferred) {
                        UsbError.ERROR_TIMEOUT -> UsbTransfer.TransferStatus.TIMEOUT
                        UsbError.ERROR_NO_DEVICE -> UsbTransfer.TransferStatus.NO_DEVICE
                        else -> UsbTransfer.TransferStatus.ERROR
                    }
                }
                bytesTransferred == 0 -> {
                    // Zero-length transfer (may be valid for some operations)
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Bulk transfer complete: 0 bytes (zero-length transfer)")
                }
                isInEndpoint -> {
                    // IN transfer: return only the bytes actually read
                    resultData = data.copyOf(bytesTransferred)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Bulk transfer complete: $bytesTransferred bytes read")
                }
                else -> {
                    // OUT transfer: return original buffer
                    resultData = data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Bulk transfer complete: $bytesTransferred bytes written")
                }
            }
            
            UsbTransfer(
                type = UsbTransfer.TransferType.BULK,
                endpoint = endpoint,
                data = resultData,
                bytesTransferred = bytesTransferred,
                status = status
            )
        } catch (e: Exception) {
            Timber.e(e, "Bulk transfer failed with exception")
            UsbTransfer(
                type = UsbTransfer.TransferType.BULK,
                endpoint = endpoint,
                data = data,
                bytesTransferred = -1,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
    
    /**
     * Performs an interrupt transfer.
     * For IN (read) transfers, returns only the bytes actually read.
     * For OUT (write) transfers, returns the original data buffer.
     * 
     * @param timeoutMs Timeout in milliseconds (default: 5000ms)
     */
    suspend fun interruptTransfer(
        handle: Long, 
        endpoint: Int, 
        data: ByteArray,
        timeoutMs: Int = 5000
    ): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            val isInEndpoint = (endpoint and 0x80) != 0
            Timber.d("Interrupt transfer: handle=$handle, endpoint=0x%02X, length=${data.size}, timeout=${timeoutMs}ms, direction=${if (isInEndpoint) "IN" else "OUT"}")
            
            val bytesTransferred = UsbNative.interruptTransfer(handle, endpoint, data, timeoutMs)
            
            val resultData: ByteArray
            val status: UsbTransfer.TransferStatus
            
            when {
                bytesTransferred < 0 -> {
                    // Error occurred
                    val errorMsg = UsbError.errorCodeToString(bytesTransferred)
                    Timber.e("Interrupt transfer failed: $errorMsg (code: $bytesTransferred)")
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = when (bytesTransferred) {
                        UsbError.ERROR_TIMEOUT -> UsbTransfer.TransferStatus.TIMEOUT
                        UsbError.ERROR_NO_DEVICE -> UsbTransfer.TransferStatus.NO_DEVICE
                        else -> UsbTransfer.TransferStatus.ERROR
                    }
                }
                bytesTransferred == 0 -> {
                    // Zero-length transfer
                    resultData = if (isInEndpoint) ByteArray(0) else data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Interrupt transfer complete: 0 bytes (zero-length transfer)")
                }
                isInEndpoint -> {
                    // IN transfer: return only the bytes actually read
                    resultData = data.copyOf(bytesTransferred)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Interrupt transfer complete: $bytesTransferred bytes read")
                }
                else -> {
                    // OUT transfer: return original buffer
                    resultData = data
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Interrupt transfer complete: $bytesTransferred bytes written")
                }
            }
            
            UsbTransfer(
                type = UsbTransfer.TransferType.INTERRUPT,
                endpoint = endpoint,
                data = resultData,
                bytesTransferred = bytesTransferred,
                status = status
            )
        } catch (e: Exception) {
            Timber.e(e, "Interrupt transfer failed with exception")
            UsbTransfer(
                type = UsbTransfer.TransferType.INTERRUPT,
                endpoint = endpoint,
                data = data,
                bytesTransferred = -1,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
    
    /**
     * Performs a control transfer.
     * For IN (read) transfers, returns only the bytes actually read.
     * For OUT (write) transfers or no-data transfers, returns the original data buffer or empty array.
     */
    suspend fun controlTransfer(
        handle: Long,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
        timeoutMs: Int = 5000
    ): UsbTransfer = withContext(Dispatchers.IO) {
        try {
            val isInRequest = (requestType and 0x80) != 0
            Timber.d("Control transfer: handle=$handle, requestType=0x%02X, request=0x%02X, direction=${if (isInRequest) "IN" else "OUT"}", requestType, request)
            
            val bytesTransferred = UsbNative.controlTransfer(handle, requestType, request, value, index, data, timeoutMs)
            
            val resultData: ByteArray
            val status: UsbTransfer.TransferStatus
            
            when {
                bytesTransferred < 0 -> {
                    // Error occurred
                    val errorMsg = UsbError.errorCodeToString(bytesTransferred)
                    Timber.e("Control transfer failed: $errorMsg (code: $bytesTransferred)")
                    // For OUT (write) operations, return original buffer; for IN (read) or no-data, return empty array
                    resultData = if (!isInRequest && data != null) {
                        data
                    } else {
                        ByteArray(0)
                    }
                    status = when (bytesTransferred) {
                        UsbError.ERROR_TIMEOUT -> UsbTransfer.TransferStatus.TIMEOUT
                        UsbError.ERROR_NO_DEVICE -> UsbTransfer.TransferStatus.NO_DEVICE
                        else -> UsbTransfer.TransferStatus.ERROR
                    }
                }
                bytesTransferred == 0 -> {
                    // Zero-length transfer (valid for no-data or zero-length data transfers)
                    resultData = data ?: ByteArray(0)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Control transfer complete: 0 bytes")
                }
                isInRequest && data != null -> {
                    // IN transfer: return only the bytes actually read
                    resultData = data.copyOf(bytesTransferred)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Control transfer complete: $bytesTransferred bytes read")
                }
                else -> {
                    // OUT transfer or no-data transfer: return original buffer
                    resultData = data ?: ByteArray(0)
                    status = UsbTransfer.TransferStatus.SUCCESS
                    Timber.d("Control transfer complete: $bytesTransferred bytes")
                }
            }
            
            UsbTransfer(
                type = UsbTransfer.TransferType.CONTROL,
                endpoint = 0,
                data = resultData,
                bytesTransferred = bytesTransferred,
                status = status
            )
        } catch (e: Exception) {
            Timber.e(e, "Control transfer failed with exception")
            UsbTransfer(
                type = UsbTransfer.TransferType.CONTROL,
                endpoint = 0,
                data = data ?: ByteArray(0),
                bytesTransferred = -1,
                status = UsbTransfer.TransferStatus.ERROR
            )
        }
    }
}
