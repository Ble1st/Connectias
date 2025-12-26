package com.ble1st.connectias.feature.dvd.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.ble1st.connectias.feature.dvd.driver.scsi.ScsiDriver
import com.ble1st.connectias.feature.dvd.models.AudioTrack
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ExoPlayer DataSource that reads raw CD-DA audio from an Optical Drive via ScsiDriver.
 * It simulates a WAV file by prepending a valid WAV header to the raw PCM stream.
 */
@UnstableApi
class AudioCdDataSource(
    private val scsiDriver: ScsiDriver,
    private val track: AudioTrack
) : BaseDataSource(/* isNetwork = */ false) {

    companion object {
        private const val WAV_HEADER_SIZE = 44
        private const val CD_SECTOR_SIZE = 2352
        // 44.1kHz * 16bit * 2ch / 8 = 176,400 bytes/sec
    }

    private var dataSpec: DataSpec? = null
    private var bytesRemaining: Long = 0
    private var currentPosition: Long = 0
    private var isOpen = false

    // Total size of the "virtual" WAV file
    private val pcmDataSize: Long = (track.endSector - track.startSector) * CD_SECTOR_SIZE
    private val totalFileSize: Long = WAV_HEADER_SIZE + pcmDataSize

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val position = dataSpec.position
        if (position >= totalFileSize) {
            // EOF
            bytesRemaining = 0
            return C.LENGTH_UNSET.toLong() // Or 0? C.LENGTH_UNSET usually means unknown, but here we know we are at end.
        }

        currentPosition = position
        
        // Calculate bytes remaining based on requested length
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            totalFileSize - position
        }

        isOpen = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!isOpen) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = minOf(bytesRemaining, readLength.toLong()).toInt()
        if (bytesToRead == 0) return C.RESULT_END_OF_INPUT

        var bytesRead = 0
        var bufferOffset = offset
        
        // 1. Handle WAV Header reading if currentPosition < WAV_HEADER_SIZE
        if (currentPosition < WAV_HEADER_SIZE) {
            val header = createWavHeader(pcmDataSize)
            val headerBytesNeeded = minOf(bytesToRead, (WAV_HEADER_SIZE - currentPosition).toInt())
            
            System.arraycopy(header, currentPosition.toInt(), buffer, bufferOffset, headerBytesNeeded)
            
            bytesRead += headerBytesNeeded
            bufferOffset += headerBytesNeeded
            currentPosition += headerBytesNeeded
        }

        // 2. Handle PCM Data reading
        if (bytesRead < bytesToRead) {
            val pcmBytesNeeded = bytesToRead - bytesRead
            val pcmOffset = currentPosition - WAV_HEADER_SIZE
            
            // Calculate LBA and offset within LBA
            val startLbaIndex = track.startSector + (pcmOffset / CD_SECTOR_SIZE)
            val offsetInSector = (pcmOffset % CD_SECTOR_SIZE).toInt()
            
            // We might need to read multiple sectors if the request crosses sector boundaries
            // Or simply read one sector and return partial data (ExoPlayer handles partial reads fine)
            
            // Let's read enough sectors to cover the request
            val sectorsToRead = ((offsetInSector + pcmBytesNeeded + CD_SECTOR_SIZE - 1) / CD_SECTOR_SIZE)
            
            try {
                val rawData = scsiDriver.readCd(startLbaIndex.toInt(), sectorsToRead)
                
                // Copy relevant part from rawData to buffer
                val bytesToCopy = minOf(pcmBytesNeeded, rawData.size - offsetInSector)
                System.arraycopy(rawData, offsetInSector, buffer, bufferOffset, bytesToCopy)
                
                bytesRead += bytesToCopy
                currentPosition += bytesToCopy
            } catch (e: IOException) {
                Timber.e(e, "Error reading CD sector at LBA $startLbaIndex")
                throw e
            }
        }

        bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? {
        return dataSpec?.uri
    }

    override fun close() {
        if (isOpen) {
            isOpen = false
            transferEnded()
        }
    }

    /**
     * Creates a standard WAV header for CD quality audio (44.1kHz, 16-bit, Stereo).
     */
    private fun createWavHeader(pcmDataLength: Long): ByteArray {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF Chunk
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt((pcmDataLength + 36).toInt()) // ChunkSize (Total - 8)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt Chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size (16 for PCM)
        buffer.putShort(1) // AudioFormat (1 = PCM)
        buffer.putShort(2) // NumChannels (2 = Stereo)
        buffer.putInt(44100) // SampleRate
        buffer.putInt(176400) // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
        buffer.putShort(4) // BlockAlign (NumChannels * BitsPerSample/8)
        buffer.putShort(16) // BitsPerSample

        // data Chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmDataLength.toInt()) // Subchunk2Size (NumBytes of data)

        return header
    }
}
