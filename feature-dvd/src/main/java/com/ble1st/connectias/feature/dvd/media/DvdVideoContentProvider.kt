package com.ble1st.connectias.feature.dvd.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.feature.dvd.driver.scsi.ScsiDriver
import com.ble1st.connectias.feature.dvd.native.DvdNative
import timber.log.Timber
import java.io.IOException
import kotlin.concurrent.thread

/**
 * ContentProvider that streams DVD Video data via direct USB/SCSI.
 * 
 * URI Format: content://com.ble1st.connectias.provider/dvd/{device_identifier}/{title_number}
 * 
 * Note: This provider bypasses the file system and talks directly to the USB device.
 * It requires the application to have USB permissions for the device.
 */
class DvdVideoContentProvider : ContentProvider() {

    companion object;

    override fun onCreate(): Boolean {
        Timber.d("=== DvdVideoContentProvider: onCreate ===")
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Timber.d("=== DvdVideoContentProvider: openFile CALLED ===")
        Timber.d("DvdVideoContentProvider: URI = $uri")
        Timber.d("DvdVideoContentProvider: Mode = $mode")
        Timber.d("DvdVideoContentProvider: Authority = ${uri.authority}")
        Timber.d("DvdVideoContentProvider: Path = ${uri.path}")
        Timber.d("DvdVideoContentProvider: Path segments = ${uri.pathSegments}")
        
        // Only "r" mode supported
        if (mode != "r") {
            Timber.e("DvdVideoContentProvider: Invalid mode '$mode', only 'r' supported")
            throw java.io.FileNotFoundException("Only read-only mode supported")
        }
        
        // Parse URI
        // Expected: /dvd/{device_id}/{title_number}
        // segments[0] = "dvd"
        // segments[1] = device_id
        // segments[2] = title_number
        
        val segments = uri.pathSegments
        Timber.d("DvdVideoContentProvider: Segments count = ${segments.size}")
        segments.forEachIndexed { index, segment ->
            Timber.d("DvdVideoContentProvider: Segment[$index] = '$segment'")
        }
        
        if (segments.size < 3 || segments[0] != "dvd") {
            Timber.e("DvdVideoContentProvider: Invalid URI format: $uri")
            Timber.e("DvdVideoContentProvider: Expected format: /dvd/{device_id}/{title_number}")
            throw java.io.FileNotFoundException("Invalid URI format")
        }
        
        val deviceId = segments[1].toIntOrNull()
        val titleNumber = segments[2].toIntOrNull() ?: 1
        
        Timber.d("DvdVideoContentProvider: Parsed deviceId = $deviceId")
        Timber.d("DvdVideoContentProvider: Parsed titleNumber = $titleNumber")
        
        if (deviceId == null) {
            Timber.e("DvdVideoContentProvider: Invalid Device ID in URI")
            throw java.io.FileNotFoundException("Invalid Device ID in URI")
        }
        
        Timber.d("DvdVideoContentProvider: Creating pipe...")
        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: IOException) {
            Timber.e(e, "DvdVideoContentProvider: Failed to create pipe")
            throw java.io.FileNotFoundException("Failed to create pipe: ${e.message}")
        }
        
        val readSide = pipe[0]
        val writeSide = pipe[1]
        Timber.d("DvdVideoContentProvider: Pipe created - readFd=${readSide.fd}, writeFd=${writeSide.fd}")
        
        // Spawn worker thread to feed the pipe
        Timber.d("DvdVideoContentProvider: Spawning streamer thread...")
        thread(name = "DvdStreamer-${System.currentTimeMillis()}") {
            Timber.d("=== DvdVideoContentProvider: Streamer thread STARTED ===")
            streamDvdToPipe(writeSide, deviceId, titleNumber)
            Timber.d("=== DvdVideoContentProvider: Streamer thread FINISHED ===")
        }
        
        Timber.d("DvdVideoContentProvider: Returning read side of pipe")
        return readSide
    }
    
    private fun streamDvdToPipe(writeSide: ParcelFileDescriptor, targetDeviceId: Int, titleNumber: Int) {
        Timber.d("DvdVideoContentProvider: streamDvdToPipe() called")
        Timber.d("DvdVideoContentProvider: streamDvdToPipe() - targetDeviceId: $targetDeviceId, titleNumber: $titleNumber")
        Timber.d("DvdVideoContentProvider: streamDvdToPipe() - writeSide FD: ${writeSide.fd}")
        
        var connection: UsbDeviceConnection?
        var scsiDriver: ScsiDriver? = null
        var dvdHandle: Long = -1
        
        try {
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Getting context")
            val context = context
            if (context == null) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - Context is null!")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Context obtained")
            
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Getting USB service")
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - UsbManager obtained")
            
            // Find device by ID
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Getting device list")
            val deviceList = usbManager.deviceList
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Found ${deviceList.size} USB devices")
            deviceList.values.forEachIndexed { index, d ->
                Timber.d("DvdVideoContentProvider: streamDvdToPipe() -   Device[$index]: ${d.deviceName}, ID=${d.deviceId}, Vendor=0x${d.vendorId.toString(16)}, Product=0x${d.productId.toString(16)}")
            }
            
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Searching for device with ID: $targetDeviceId")
            val device = deviceList.values.find { it.deviceId == targetDeviceId }
            
            if (device == null) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - USB Device with ID $targetDeviceId NOT FOUND!")
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - Available device IDs: ${deviceList.values.map { it.deviceId }}")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Found device: ${device.deviceName}, ID=${device.deviceId}")
            
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Checking USB permission")
            if (!usbManager.hasPermission(device)) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - No permission for device ${device.deviceName}!")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - USB permission verified")
            
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Opening USB device connection")
            connection = usbManager.openDevice(device)
            if (connection == null) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - Failed to open USB connection!")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - USB connection opened: $connection")
            
            // Claim Mass Storage interface
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Searching for Mass Storage interface (class 8)")
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Device has ${device.interfaceCount} interfaces")
            val iface = (0 until device.interfaceCount).map { 
                val i = device.getInterface(it)
                Timber.d("DvdVideoContentProvider: streamDvdToPipe() -   Interface[$it]: class=${i.interfaceClass}, subclass=${i.interfaceSubclass}, protocol=${i.interfaceProtocol}")
                i
            }.find { it.interfaceClass == 8 }
            if (iface == null) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - No Mass Storage interface found!")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Mass Storage interface found: class=${iface.interfaceClass}, endpoints=${iface.endpointCount}")
                
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Creating ScsiDriver")
            scsiDriver = ScsiDriver(connection, iface)
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - SCSI driver created: $scsiDriver")
            
            // Wait for drive to be ready
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Waiting for drive to be ready (max 15 attempts, 500ms delay)")
            val ready = scsiDriver.waitForReady(maxAttempts = 15, delayMs = 500)
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - waitForReady() returned: $ready")
            if (!ready) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - Drive not ready or no medium present!")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Drive is ready")
            
            // NOTE: CSS authentication is now handled automatically by libdvdcss
            // via the pf_ioctl callback in dvdcss_stream_cb. No manual authentication needed.
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - CSS authentication will be handled automatically by libdvdcss")
            
            // Initialize Native Layer if needed
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Loading native library")
            val libLoaded = DvdNative.ensureLibraryLoaded()
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - ensureLibraryLoaded() returned: $libLoaded")
            if (!libLoaded) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - Native library not loaded!")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Native library loaded")
            
            // Open Stream via ScsiDriver
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - Opening DVD stream via SCSI")
            dvdHandle = DvdNative.dvdOpenStream(scsiDriver)
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - dvdOpenStream() returned handle: $dvdHandle")
            if (dvdHandle <= 0) {
                Timber.e("DvdVideoContentProvider: streamDvdToPipe() - Failed to open DVD stream handle! (handle=$dvdHandle)")
                return
            }
            Timber.d("DvdVideoContentProvider: streamDvdToPipe() - DVD stream opened, handle: $dvdHandle")
            
            Timber.i("DvdVideoContentProvider: streamDvdToPipe() - Starting native stream for Title $titleNumber to FD ${writeSide.fd}")
            
            // Blocking call - pumps data from USB to Pipe
            val startTime = System.currentTimeMillis()
            val bytes = DvdNative.dvdStreamToFdNative(dvdHandle, titleNumber, writeSide.fd)
            val duration = System.currentTimeMillis() - startTime
            
            Timber.i("=== streamDvdToPipe: Stream finished ===")
            Timber.i("streamDvdToPipe: Bytes written: $bytes")
            Timber.i("streamDvdToPipe: Duration: ${duration}ms")
            if (bytes > 0 && duration > 0) {
                val mbPerSec = (bytes.toDouble() / (1024 * 1024)) / (duration.toDouble() / 1000)
                Timber.i("streamDvdToPipe: Speed: %.2f MB/s".format(mbPerSec))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "streamDvdToPipe: ERROR during DVD streaming")
        } finally {
            Timber.d("streamDvdToPipe: Cleanup started")
            
            // Cleanup
            if (dvdHandle > 0) {
                Timber.d("streamDvdToPipe: Closing DVD handle $dvdHandle")
                DvdNative.dvdClose(dvdHandle)
            }
            try {
                scsiDriver?.close()
                Timber.d("streamDvdToPipe: SCSI driver closed")
            } catch (e: Exception) {
                Timber.w(e, "streamDvdToPipe: Error closing SCSI driver")
            }
            
            // Close write side to signal EOF to reader
            try {
                writeSide.close()
                Timber.d("streamDvdToPipe: Write pipe closed")
            } catch (e: Exception) {
                Timber.w(e, "streamDvdToPipe: Error closing write pipe")
            }
            
            Timber.d("=== streamDvdToPipe: FINISHED ===")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String {
        // DVD VOB files are MPEG-2 Program Stream (PS), NOT Transport Stream (TS)
        return "video/mp2p" // MPEG-2 Program Stream (same as MimeTypes.VIDEO_PS)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        throw UnsupportedOperationException()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException()
    }
}