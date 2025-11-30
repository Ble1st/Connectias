package com.ble1st.connectias.feature.usb.native

import timber.log.Timber
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native interface for USB operations via libusb.
 * 
 * Uses lazy loading to prevent hard failures when the native library is unavailable,
 * allowing graceful degradation and enabling tests without native dependencies.
 */
object UsbNative {
    
    @Volatile
    private var libraryLoaded = false
    
    @Volatile
    private var libraryAvailable = false
    
    private val libraryLoadLock = Any()
    
    /**
     * Ensures the native library is loaded. Thread-safe and idempotent.
     * @return true if library is available, false otherwise
     * @throws IllegalStateException if library loading fails and was not previously attempted
     */
    private fun ensureLibraryLoaded(): Boolean {
        if (libraryLoaded) {
            return libraryAvailable
        }
        
        synchronized(libraryLoadLock) {
            if (libraryLoaded) {
                return libraryAvailable
            }
            
            try {
                Timber.d("Loading USB native library...")
                System.loadLibrary("usb_jni")
                libraryAvailable = true
                libraryLoaded = true
                Timber.d("USB native library loaded successfully")
                return true
            } catch (e: UnsatisfiedLinkError) {
                libraryAvailable = false
                libraryLoaded = true
                Timber.e(e, "Failed to load USB native library - USB functionality will be unavailable")
                return false
            }
        }
    }
    
    /**
     * Checks if the native library is available.
     */
    fun isLibraryAvailable(): Boolean = libraryLoaded && libraryAvailable
    
    /**
     * Enumerates all connected USB devices.
     * @throws IllegalStateException if native library is not available
     */
    fun enumerateDevices(): Array<UsbDeviceNative> {
        if (!ensureLibraryLoaded()) {
            throw IllegalStateException("USB native library not available")
        }
        return enumerateDevicesNative()
    }
    
    private external fun enumerateDevicesNative(): Array<UsbDeviceNative>
    
    /**
     * Opens a USB device by vendor and product ID.
     * @param vendorId USB vendor ID
     * @param productId USB product ID
     * @return Handle to the opened device, or -1 on error
     * @throws IllegalStateException if native library is not available
     */
    fun openDevice(vendorId: Int, productId: Int): Long {
        if (!ensureLibraryLoaded()) {
            throw IllegalStateException("USB native library not available")
        }
        return openDeviceNative(vendorId, productId)
    }
    
    private external fun openDeviceNative(vendorId: Int, productId: Int): Long
    
    /**
     * Closes a USB device.
     * @param handle Device handle from openDevice, or -1 (no-op)
     * @return 0 on success, negative error code on failure (errno-style)
     * Thread-safe: Can be called from any thread
     */
    fun closeDevice(handle: Long): Int {
        if (!ensureLibraryLoaded() || handle < 0) {
            return 0 // No-op for invalid handles
        }
        return closeDeviceNative(handle)
    }
    
    private external fun closeDeviceNative(handle: Long): Int
    
    /**
     * Performs a bulk transfer.
     * @param handle Device handle from openDevice
     * @param endpoint USB endpoint address (bit 7: 0=OUT, 1=IN)
     * @param data Buffer for OUT transfers (written) or IN transfers (read into)
     * @param length Number of bytes to transfer (for OUT) or buffer size (for IN)
     * @param timeoutMs Timeout in milliseconds (default: 5000ms). Prevents indefinite blocking.
     * @return Number of bytes transferred, or negative libusb error code on error
     * @throws IllegalStateException if native library is not available
     */
    fun bulkTransfer(handle: Long, endpoint: Int, data: ByteArray, length: Int = data.size, timeoutMs: Int = 5000): Int {
        if (!ensureLibraryLoaded()) {
            throw IllegalStateException("USB native library not available")
        }
        return bulkTransferNative(handle, endpoint, data, length, timeoutMs)
    }
    
    private external fun bulkTransferNative(handle: Long, endpoint: Int, data: ByteArray, length: Int, timeoutMs: Int): Int
    
    /**
     * Performs an interrupt transfer.
     * @param handle Device handle from openDevice
     * @param endpoint USB endpoint address (bit 7: 0=OUT, 1=IN)
     * @param data Buffer for OUT transfers (written) or IN transfers (read into)
     * @param length Number of bytes to transfer (for OUT) or buffer size (for IN)
     * @param timeoutMs Timeout in milliseconds (default: 5000ms). Prevents indefinite blocking.
     * @return Number of bytes transferred, or negative libusb error code on error
     * @throws IllegalStateException if native library is not available
     */
    fun interruptTransfer(handle: Long, endpoint: Int, data: ByteArray, length: Int = data.size, timeoutMs: Int = 5000): Int {
        if (!ensureLibraryLoaded()) {
            throw IllegalStateException("USB native library not available")
        }
        return interruptTransferNative(handle, endpoint, data, length, timeoutMs)
    }
    
    private external fun interruptTransferNative(handle: Long, endpoint: Int, data: ByteArray, length: Int, timeoutMs: Int): Int
    
    /**
     * Performs a control transfer.
     * @param handle Device handle from openDevice
     * @param requestType Request type byte
     * @param request Request byte
     * @param value Value field
     * @param index Index field
     * @param data Buffer for data (null for no-data transfers)
     * @param timeoutMs Timeout in milliseconds (default: 5000ms)
     * @return Number of bytes transferred, or negative libusb error code on error
     * @throws IllegalStateException if native library is not available
     */
    fun controlTransfer(
        handle: Long,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
        timeoutMs: Int = 5000
    ): Int {
        if (!ensureLibraryLoaded()) {
            throw IllegalStateException("USB native library not available")
        }
        return controlTransferNative(handle, requestType, request, value, index, data, timeoutMs)
    }
    
    private external fun controlTransferNative(
        handle: Long,
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray?,
        timeoutMs: Int
    ): Int
}

/**
 * USB device handle wrapper that implements automatic resource management.
 * Use with Kotlin's `use { }` block to ensure proper cleanup.
 * 
 * Example:
 * ```
 * UsbDeviceHandle.open(vendorId, productId)?.use { handle ->
 *     // Use handle for transfers
 * } // Automatically closed
 * ```
 */
class UsbDeviceHandle private constructor(
    private val handle: Long
) : Closeable {
    
    companion object {
        /**
         * Opens a USB device by vendor and product ID.
         * @param vendorId USB vendor ID
         * @param productId USB product ID
         * @return UsbDeviceHandle instance, or null on error
         */
        fun open(vendorId: Int, productId: Int): UsbDeviceHandle? {
            return try {
                val handle = UsbNative.openDevice(vendorId, productId)
                if (handle >= 0) {
                    UsbDeviceHandle(handle)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to open USB device: Vendor=0x%04X, Product=0x%04X", vendorId, productId)
                null
            }
        }
    }
    
    /**
     * Gets the native handle value.
     */
    fun getHandle(): Long = handle
    
    /**
     * Closes the USB device handle.
     * Safe to call multiple times.
     */
    override fun close() {
        if (handle >= 0) {
            UsbNative.closeDevice(handle)
        }
    }
}

/**
 * libusb error codes mapped to Kotlin constants.
 * Negative values indicate errors from libusb.
 */
object UsbError {
    const val SUCCESS = 0
    const val ERROR_IO = -1
    const val ERROR_INVALID_PARAM = -2
    const val ERROR_ACCESS = -3
    const val ERROR_NO_DEVICE = -4
    const val ERROR_NOT_FOUND = -5
    const val ERROR_BUSY = -6
    const val ERROR_TIMEOUT = -7
    const val ERROR_OVERFLOW = -8
    const val ERROR_PIPE = -9
    const val ERROR_INTERRUPTED = -10
    const val ERROR_NO_MEM = -11
    const val ERROR_NOT_SUPPORTED = -12
    const val ERROR_OTHER = -99
    
    /**
     * Converts a libusb error code to a human-readable string.
     */
    fun errorCodeToString(code: Int): String {
        return when (code) {
            SUCCESS -> "Success"
            ERROR_IO -> "Input/Output error"
            ERROR_INVALID_PARAM -> "Invalid parameter"
            ERROR_ACCESS -> "Access denied"
            ERROR_NO_DEVICE -> "No device"
            ERROR_NOT_FOUND -> "Device not found"
            ERROR_BUSY -> "Device busy"
            ERROR_TIMEOUT -> "Timeout"
            ERROR_OVERFLOW -> "Overflow"
            ERROR_PIPE -> "Pipe error"
            ERROR_INTERRUPTED -> "Interrupted"
            ERROR_NO_MEM -> "Out of memory"
            ERROR_NOT_SUPPORTED -> "Not supported"
            else -> "Unknown error ($code)"
        }
    }
}

/**
 * USB device class enumeration.
 * Maps USB class integer values to type-safe enum.
 */
enum class UsbClass(val value: Int) {
    PER_INTERFACE(0),
    AUDIO(1),
    COMMUNICATIONS(2),
    HID(3),
    PHYSICAL(5),
    IMAGE(6),
    PRINTER(7),
    MASS_STORAGE(8),
    HUB(9),
    CDC_DATA(10),
    SMART_CARD(11),
    CONTENT_SECURITY(13),
    VIDEO(14),
    PERSONAL_HEALTHCARE(15),
    AUDIO_VIDEO(16),
    BILLBOARD(17),
    USB_TYPE_C_BRIDGE(18),
    DIAGNOSTIC(220),
    WIRELESS_CONTROLLER(224),
    MISCELLANEOUS(239),
    APPLICATION_SPECIFIC(254),
    VENDOR_SPECIFIC(255),
    UNKNOWN(-1);
    
    companion object {
        /**
         * Creates a UsbClass from a USB class integer value.
         * @param value USB class integer value
         * @return Corresponding UsbClass enum, or UNKNOWN if not mapped
         */
        fun fromValue(value: Int): UsbClass {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * Native USB device structure.
 */
data class UsbDeviceNative(
    val vendorId: Int,
    val productId: Int,
    val deviceClass: UsbClass,
    val serialNumber: String?,
    val manufacturer: String?,
    val product: String?
)
