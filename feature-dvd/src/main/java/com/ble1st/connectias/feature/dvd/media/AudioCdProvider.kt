package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import android.hardware.usb.UsbManager
import com.ble1st.connectias.feature.dvd.driver.scsi.ScsiDriver
import com.ble1st.connectias.feature.dvd.models.AudioTrack
import com.ble1st.connectias.feature.dvd.models.OpticalDrive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for Audio CD operations using direct SCSI commands.
 */
@Singleton
class AudioCdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    /**
     * Gets all tracks from an Audio CD by reading the Table of Contents (TOC).
     * 
     * @param drive The optical drive containing the Audio CD
     * @return AudioCdResult.Success with tracks, or AudioCdResult.Error on failure
     */
    suspend fun getTracks(drive: OpticalDrive): AudioCdResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Reading Audio CD TOC from device: ${drive.device.product}")
            
            // Find matching Android UsbDevice
            val androidDevice = usbManager.deviceList.values.find { 
                it.vendorId == drive.device.vendorId && it.productId == drive.device.productId 
            } ?: return@withContext AudioCdResult.Error("USB Device not found")

            if (!usbManager.hasPermission(androidDevice)) {
                return@withContext AudioCdResult.Error("No USB permission")
            }

            val connection = usbManager.openDevice(androidDevice)
                ?: return@withContext AudioCdResult.Error("Failed to open USB device connection")

            try {
                // Find Mass Storage Interface (usually index 0)
                // Simplified: assume first interface is correct or find Mass Storage
                var interfaceIndex = 0
                for (i in 0 until androidDevice.interfaceCount) {
                    if (androidDevice.getInterface(i).interfaceClass == 8) { // Mass Storage
                        interfaceIndex = i
                        break
                    }
                }
                val usbInterface = androidDevice.getInterface(interfaceIndex)
                
                val driver = ScsiDriver(connection, usbInterface)
                try {
                    // Ensure unit is ready? (Test Unit Ready)
                    // driver.testUnitReady() 
                    
                    val tocData = driver.readToc()
                    val tracks = parseToc(tocData)
                    
                    Timber.i("Audio CD TOC read: ${tracks.size} tracks found")
                    AudioCdResult.Success(tracks)
                } finally {
                    driver.close() // Releases interface and closes connection
                }
            } catch (e: Exception) {
                Timber.e(e, "SCSI error reading TOC")
                connection.close()
                AudioCdResult.Error.fromThrowable(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error reading Audio CD")
            AudioCdResult.Error.fromThrowable(e)
        }
    }

    /**
     * Parses raw TOC data into AudioTrack list.
     * 
     * TOC Header (4 bytes):
     * [0-1]: Data Length
     * [2]: First Track
     * [3]: Last Track
     * 
     * Descriptors (8 bytes each):
     * [0]: Reserved
     * [1]: ADR/Control
     * [2]: Track Number
     * [3]: Reserved
     * [4-7]: Start Address (LBA) (Big Endian)
     */
    private fun parseToc(tocData: ByteArray): List<AudioTrack> {
        if (tocData.size < 4) return emptyList()
        
        val buffer = ByteBuffer.wrap(tocData).order(ByteOrder.BIG_ENDIAN)
        val tocLength = buffer.short.toInt() + 2 // Length field doesn't include itself
        val firstTrack = buffer.get().toInt()
        val lastTrack = buffer.get().toInt()
        
        Timber.d("TOC: Length=$tocLength, Tracks=$firstTrack-$lastTrack")
        
        val tracks = mutableListOf<AudioTrack>()
        val trackDescriptors = mutableMapOf<Int, Long>() // TrackNum -> StartLBA
        var leadOutLba: Long = 0
        
        // Read descriptors
        // Header is 4 bytes. Each descriptor is 8 bytes.
        // We expect (lastTrack - firstTrack + 1) descriptors + Lead-Out descriptor.
        
        while (buffer.remaining() >= 8) {
            buffer.get() // Reserved
            buffer.get().toInt()
            val trackNum = buffer.get().toInt()
            buffer.get() // Reserved
            val lba = buffer.int.toLong()
            
            if (trackNum == 0xAA) {
                leadOutLba = lba
            } else {
                trackDescriptors[trackNum] = lba
            }
        }
        
        // Create AudioTrack objects
        // Skip track 0 (Lead-In) - only process actual audio tracks (1-99)
        for (i in firstTrack..lastTrack) {
            // Skip track 0 (Lead-In area, not a real audio track)
            if (i == 0) continue
            
            val startLba = trackDescriptors[i] ?: continue
            
            // Determine end LBA: Start of next track, or Lead-Out if last track
            val nextStartLba = trackDescriptors[i + 1] ?: leadOutLba
            
            // Calculate duration
            // 75 sectors per second (CD-DA)
            val sectors = nextStartLba - startLba
            val durationMs = (sectors * 1000L) / 75
            
            tracks.add(AudioTrack(
                number = i,
                title = "Track $i",
                duration = durationMs,
                startSector = startLba,
                endSector = nextStartLba
            ))
        }
        
        return tracks
    }
}
