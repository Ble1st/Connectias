package com.ble1st.connectias.feature.dvd.driver.scsi

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.ble1st.connectias.feature.dvd.driver.CssIoctlOp
import com.ble1st.connectias.feature.dvd.driver.UsbBlockDevice
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * SCSI Sense Data structure for error reporting.
 */
data class ScsiSenseData(
    val senseKey: Byte,
    val asc: Byte,        // Additional Sense Code
    val ascq: Byte,       // Additional Sense Code Qualifier
    val isValid: Boolean
) {
    val senseKeyName: String
        get() = when (senseKey.toInt() and 0x0F) {
            0x00 -> "NO_SENSE"
            0x01 -> "RECOVERED_ERROR"
            0x02 -> "NOT_READY"
            0x03 -> "MEDIUM_ERROR"
            0x04 -> "HARDWARE_ERROR"
            0x05 -> "ILLEGAL_REQUEST"
            0x06 -> "UNIT_ATTENTION"
            0x07 -> "DATA_PROTECT"
            0x08 -> "BLANK_CHECK"
            0x0B -> "ABORTED_COMMAND"
            else -> "UNKNOWN(${senseKey.toInt() and 0x0F})"
        }
    
    val isRetryable: Boolean
        get() = when (senseKey.toInt() and 0x0F) {
            0x02 -> true  // NOT_READY - might become ready
            0x06 -> true  // UNIT_ATTENTION - retry after attention cleared
            0x0B -> true  // ABORTED_COMMAND - transient
            else -> false
        }
    
    val isNoMedium: Boolean
        get() = asc == ScsiCommand.ASC_NO_MEDIUM
    
    override fun toString(): String = "SenseData($senseKeyName, ASC=0x${(asc.toInt() and 0xFF).toString(16).uppercase()}, ASCQ=0x${(ascq.toInt() and 0xFF).toString(16).uppercase()})"
}

/**
 * SCSI command execution result.
 */
sealed class ScsiResult {
    data class Success(val bytesTransferred: Int) : ScsiResult()
    data class CheckCondition(val sense: ScsiSenseData) : ScsiResult()
    data class Error(val message: String) : ScsiResult()
}

/**
 * Driver for USB Mass Storage devices using SCSI Transparent Command Set via Bulk-Only Transport (BOT).
 */
class ScsiDriver(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface
) : UsbBlockDevice {

    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private val tagGenerator = AtomicInteger(1)
    
    // Cache block size and count
    private var _blockSize: Int = 2048 // Default DVD/CD size
    private var _blockCount: Long = 0
    
    // Last sense data for diagnostics
    private var lastSenseData: ScsiSenseData? = null
    
    // Recovery state tracking
    @Volatile
    private var consecutiveErrors: Int = 0
    private val maxConsecutiveErrors = 3
    
    // Flag to track if connection is still valid
    @Volatile
    private var isConnectionValid = true

    override val blockSize: Int
        get() = _blockSize

    override val blockCount: Long
        get() = _blockCount

    init {
        // Find endpoints
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    endpointIn = endpoint
                } else {
                    endpointOut = endpoint
                }
            }
        }

        if (endpointIn == null || endpointOut == null) {
            throw IOException("Could not find bulk endpoints")
        }
        
        // Claim interface
        if (!connection.claimInterface(usbInterface, true)) {
            throw IOException("Could not claim USB interface")
        }
    }
    
    /**
     * Clears a stalled endpoint using USB control transfer.
     * This is called after a transfer failure to reset the endpoint state.
     */
    private fun clearStall(endpoint: UsbEndpoint): Boolean {
        val result = connection.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_STANDARD or UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
            0x01, // CLEAR_FEATURE
            0x00, // ENDPOINT_HALT
            endpoint.address,
            null, 0,
            TIMEOUT
        )
        Timber.d("Clear stall on endpoint 0x${endpoint.address.toString(16)}: ${if (result >= 0) "success" else "failed"}")
        return result >= 0
    }
    
    /**
     * Performs USB Mass Storage Reset (class-specific request).
     */
    private fun usbMassStorageReset(): Boolean {
        val result = connection.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
            0xFF, // Bulk-Only Mass Storage Reset
            0,
            usbInterface.id,
            null, 0,
            TIMEOUT
        )
        Timber.d("USB Mass Storage Reset: ${if (result >= 0) "success" else "failed"}")
        return result >= 0
    }
    
    /**
     * Attempts to recover from a USB error by resetting endpoints and optionally the device.
     */
    private fun attemptRecovery(): Boolean {
        Timber.w("Attempting USB recovery...")
        
        // 1. Try clearing both endpoint stalls
        var recovered = false
        endpointIn?.let { recovered = clearStall(it) || recovered }
        endpointOut?.let { recovered = clearStall(it) || recovered }
        
        if (recovered) {
            Thread.sleep(100) // Small delay after clear stall
            return true
        }
        
        // 2. Try Mass Storage Reset
        if (usbMassStorageReset()) {
            Thread.sleep(200) // Longer delay after reset
            
            // Clear stalls again after reset
            endpointIn?.let { clearStall(it) }
            endpointOut?.let { clearStall(it) }
            
            return true
        }
        
        Timber.e("USB recovery failed")
        isConnectionValid = false
        return false
    }
    
    /**
     * Waits for the drive to become ready.
     * 
     * @param maxAttempts Maximum number of TEST UNIT READY attempts
     * @param delayMs Delay between attempts in milliseconds
     * @return true if drive is ready, false otherwise
     */
    fun waitForReady(maxAttempts: Int = 10, delayMs: Long = 500): Boolean {
        Timber.d("Waiting for drive to become ready (max $maxAttempts attempts)...")
        
        for (attempt in 1..maxAttempts) {
            when (val result = testUnitReady()) {
                is ScsiResult.Success -> {
                    Timber.d("Drive ready after $attempt attempt(s)")
                    return true
                }
                is ScsiResult.CheckCondition -> {
                    val sense = result.sense
                    Timber.d("TEST UNIT READY attempt $attempt: $sense")
                    
                    if (sense.isNoMedium) {
                        Timber.w("No medium present in drive")
                        return false
                    }
                    
                    if (!sense.isRetryable) {
                        Timber.w("Non-retryable error: $sense")
                        return false
                    }
                    
                    // Wait before retry
                    if (attempt < maxAttempts) {
                        Thread.sleep(delayMs)
                    }
                }
                is ScsiResult.Error -> {
                    Timber.e("TEST UNIT READY error: ${result.message}")
                    return false
                }
            }
        }
        
        Timber.w("Drive not ready after $maxAttempts attempts")
        return false
    }
    
    /**
     * Sends TEST UNIT READY command.
     */
    fun testUnitReady(): ScsiResult {
        val cdb = ByteArray(6).apply {
            this[0] = ScsiCommand.TEST_UNIT_READY
        }
        return executeScsiCommandWithSense(cdb, null, UsbConstants.USB_DIR_IN)
    }
    
    /**
     * Sends REQUEST SENSE command to get error details.
     */
    fun requestSense(): ScsiSenseData {
        val buffer = ByteArray(18) // Standard sense data length
        val cdb = ByteArray(6).apply {
            this[0] = ScsiCommand.REQUEST_SENSE
            this[4] = 18 // Allocation length
        }
        
        try {
            // Use raw execute without sense handling to avoid recursion
            executeScsiCommandRaw(cdb, buffer, UsbConstants.USB_DIR_IN)
            
            // Parse sense data (Fixed format)
            val responseCode = buffer[0].toInt() and 0x7F
            if (responseCode == 0x70 || responseCode == 0x71) {
                val senseKey = (buffer[2].toInt() and 0x0F).toByte()
                val asc = buffer[12]
                val ascq = buffer[13]
                
                val sense = ScsiSenseData(senseKey, asc, ascq, true)
                lastSenseData = sense
                return sense
            }
        } catch (e: Exception) {
            Timber.e(e, "REQUEST SENSE failed")
        }
        
        return ScsiSenseData(0, 0, 0, false)
    }

    /**
     * Executes a SCSI command with automatic sense data retrieval on CHECK CONDITION.
     * Includes retry logic and USB recovery on transfer failures.
     */
    @Synchronized
    fun executeScsiCommandWithSense(cdb: ByteArray, dataBuffer: ByteArray?, direction: Int): ScsiResult {
        return executeScsiCommandWithRetry(cdb, dataBuffer, direction)
    }
    
    /**
     * Internal method that executes SCSI command with retry logic.
     */
    private fun executeScsiCommandWithRetry(
        cdb: ByteArray, 
        dataBuffer: ByteArray?, 
        direction: Int, 
        maxRetries: Int = 3
    ): ScsiResult {
        var lastError = "Unknown error"
        
        for (attempt in 0 until maxRetries) {
            if (!isConnectionValid) {
                return ScsiResult.Error("USB connection is no longer valid")
            }

            when (val result = executeScsiCommandInternal(cdb, dataBuffer, direction)) {
                is ScsiResult.Success -> {
                    consecutiveErrors = 0
                    return result
                }
                is ScsiResult.CheckCondition -> {
                    consecutiveErrors = 0
                    return result
                }
                is ScsiResult.Error -> {
                    lastError = result.message
                    consecutiveErrors++
                    
                    Timber.w("SCSI command failed (attempt ${attempt + 1}/$maxRetries): ${result.message}")
                    
                    // Check if this is a recoverable USB error
                    if (result.message.contains("CBW") || result.message.contains("CSW") || 
                        result.message.contains("Data phase")) {
                        
                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            Timber.e("Too many consecutive errors, attempting recovery...")
                            if (!attemptRecovery()) {
                                return ScsiResult.Error("USB recovery failed after $consecutiveErrors consecutive errors")
                            }
                            consecutiveErrors = 0
                        }
                        
                        // Small delay before retry
                        Thread.sleep(100L * (attempt + 1))
                        continue
                    }
                    
                    // Non-recoverable error
                    return result
                }
            }
        }
        
        return ScsiResult.Error("SCSI command failed after $maxRetries attempts: $lastError")
    }
    
    /**
     * Internal implementation of SCSI command execution without retry logic.
     */
    private fun executeScsiCommandInternal(cdb: ByteArray, dataBuffer: ByteArray?, direction: Int): ScsiResult {
        val tag = tagGenerator.getAndIncrement()
        val dataLength = dataBuffer?.size ?: 0
        val flags = if (direction == UsbConstants.USB_DIR_IN) ScsiCommand.DIRECTION_IN else ScsiCommand.DIRECTION_OUT
        
        // 1. Send CBW
        val cbw = ScsiCommand.createCbw(tag, dataLength, flags, 0, cdb)
        val cbwTransfer = connection.bulkTransfer(endpointOut, cbw, cbw.size, TIMEOUT_CBW)
        if (cbwTransfer != cbw.size) {
            Timber.e("Failed to send CBW. Transferred: $cbwTransfer, Expected: ${cbw.size}")
            return ScsiResult.Error("Failed to send CBW. Transferred: $cbwTransfer, Expected: ${cbw.size}")
        }

        // 2. Data Phase
        var transferred = 0
        if (dataLength > 0 && dataBuffer != null) {
            val endpoint = if (direction == UsbConstants.USB_DIR_IN) endpointIn else endpointOut
            val chunkSize = 16384 
            var offset = 0
            var dataPhaseRetries = 0
            val maxDataPhaseRetries = 2
            
            while (offset < dataLength) {
                val len = minOf(dataLength - offset, chunkSize)
                val chunkTransfer = connection.bulkTransfer(endpoint, dataBuffer, offset, len, TIMEOUT_DATA)
                
                if (chunkTransfer < 0) {
                    dataPhaseRetries++
                    if (dataPhaseRetries > maxDataPhaseRetries) {
                        Timber.e("Data phase failed at offset $offset after $dataPhaseRetries retries")
                        return ScsiResult.Error("Data phase transfer failed at offset $offset")
                    }
                    
                    // Try to clear endpoint stall and retry
                    Timber.w("Data phase transfer returned $chunkTransfer at offset $offset, attempting recovery...")
                    endpoint?.let { clearStall(it) }
                    Thread.sleep(50)
                    continue
                }
                
                transferred += chunkTransfer
                offset += chunkTransfer
                dataPhaseRetries = 0 // Reset retry counter on success
                
                if (chunkTransfer < len) {
                    // Short transfer - this is normal at end of data
                    break
                }
            }
        }

        // 3. Receive CSW
        val cswBuffer = ByteArray(ScsiCommand.CSW_SIZE)
        var cswTransfer = connection.bulkTransfer(endpointIn, cswBuffer, ScsiCommand.CSW_SIZE, TIMEOUT_CSW)
        
        // Retry CSW read once on failure
        if (cswTransfer != ScsiCommand.CSW_SIZE) {
            Timber.w("First CSW read failed (got $cswTransfer), clearing stall and retrying...")
            endpointIn?.let { clearStall(it) }
            Thread.sleep(50)
            cswTransfer = connection.bulkTransfer(endpointIn, cswBuffer, ScsiCommand.CSW_SIZE, TIMEOUT_CSW)
        }
        
        if (cswTransfer != ScsiCommand.CSW_SIZE) {
            Timber.e("Failed to read CSW. Read: $cswTransfer")
            return ScsiResult.Error("Failed to read CSW. Read: $cswTransfer")
        }

        val csw = ByteBuffer.wrap(cswBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val signature = csw.int
        val cswTag = csw.int
        @Suppress("UNUSED_VARIABLE")
        val residue = csw.int
        val status = csw.get()

        if (signature != ScsiCommand.CSW_SIGNATURE) {
            return ScsiResult.Error("Invalid CSW signature: 0x${signature.toString(16)}")
        }
        if (cswTag != tag) {
            return ScsiResult.Error("CSW tag mismatch. Expected: $tag, Got: $cswTag")
        }
        
        return when (status.toInt()) {
            0 -> ScsiResult.Success(transferred)
            1 -> {
                // CHECK CONDITION - get sense data
                val sense = requestSense()
                ScsiResult.CheckCondition(sense)
            }
            else -> ScsiResult.Error("SCSI Command Failed with status: $status")
        }
    }
    
    /**
     * Raw SCSI command execution without sense handling (for REQUEST SENSE itself).
     */
    @Synchronized
    private fun executeScsiCommandRaw(cdb: ByteArray, dataBuffer: ByteArray?, direction: Int): Int {
        val tag = tagGenerator.getAndIncrement()
        val dataLength = dataBuffer?.size ?: 0
        val flags = if (direction == UsbConstants.USB_DIR_IN) ScsiCommand.DIRECTION_IN else ScsiCommand.DIRECTION_OUT
        
        val cbw = ScsiCommand.createCbw(tag, dataLength, flags, 0, cdb)
        val cbwTransfer = connection.bulkTransfer(endpointOut, cbw, cbw.size, TIMEOUT_CBW)
        if (cbwTransfer != cbw.size) {
            throw IOException("Failed to send CBW")
        }

        var transferred = 0
        if (dataLength > 0 && dataBuffer != null) {
            val endpoint = if (direction == UsbConstants.USB_DIR_IN) endpointIn else endpointOut
            val chunkSize = 16384 
            var offset = 0
            while (offset < dataLength) {
                val len = minOf(dataLength - offset, chunkSize)
                val chunkTransfer = connection.bulkTransfer(endpoint, dataBuffer, offset, len, TIMEOUT_DATA)
                if (chunkTransfer < 0) break
                transferred += chunkTransfer
                offset += chunkTransfer
                if (chunkTransfer < len) break
            }
        }

        val cswBuffer = ByteArray(ScsiCommand.CSW_SIZE)
        val cswTransfer = connection.bulkTransfer(endpointIn, cswBuffer, ScsiCommand.CSW_SIZE, TIMEOUT_CSW)
        if (cswTransfer != ScsiCommand.CSW_SIZE) {
            throw IOException("Failed to read CSW")
        }

        return transferred
    }

    /**
     * Executes a SCSI command (legacy method for compatibility).
     * Throws IOException on error.
     *
     * @param cdb Command Data Block
     * @param dataBuffer Buffer for data phase (in or out). Null if no data phase.
     * @param direction Direction of data phase (UsbConstants.USB_DIR_IN or USB_DIR_OUT).
     * @return Bytes transferred in data phase.
     */
    @Synchronized
    fun executeScsiCommand(cdb: ByteArray, dataBuffer: ByteArray?, direction: Int): Int {
        return when (val result = executeScsiCommandWithSense(cdb, dataBuffer, direction)) {
            is ScsiResult.Success -> result.bytesTransferred
            is ScsiResult.CheckCondition -> {
                val sense = result.sense
                Timber.w("SCSI CHECK CONDITION: $sense")
                throw IOException("SCSI Command Failed: ${sense.senseKeyName} (ASC=0x${(sense.asc.toInt() and 0xFF).toString(16)}, ASCQ=0x${(sense.ascq.toInt() and 0xFF).toString(16)})")
            }
            is ScsiResult.Error -> {
                throw IOException(result.message)
            }
        }
    }

    override fun read(lba: Long, buffer: ByteArray, length: Int): Int {
        if (!isConnectionValid) {
            throw IOException("USB connection is no longer valid")
        }
        
        // Determine sector size (assume 2048 or 2352 based on use case, but SCSI Read(10) uses blocks)
        // For READ(10), we need block count.
        val blocks = length / _blockSize
        if (blocks * _blockSize != length) {
            Timber.w("Read length $length is not a multiple of block size $_blockSize")
        }
        
        if (blocks <= 0) {
            Timber.w("Invalid block count: $blocks (length=$length, blockSize=$_blockSize)")
            return 0
        }
        
        // For large reads, split into smaller chunks to improve reliability
        // DVD drives can sometimes struggle with very large transfers
        val maxBlocksPerRead = 64 // 128KB per read for DVD (64 * 2048)
        
        if (blocks <= maxBlocksPerRead) {
            val cdb = ScsiCommand.createRead10Cdb(lba.toInt(), blocks.toShort())
            return executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
        }
        
        // Split large reads into smaller chunks
        var totalRead = 0
        var currentLba = lba
        var offset = 0
        var remainingBlocks = blocks
        
        while (remainingBlocks > 0) {
            val blocksToRead = minOf(remainingBlocks, maxBlocksPerRead)
            val bytesToRead = blocksToRead * _blockSize
            
            // Create a temporary buffer for this chunk
            val chunkBuffer = ByteArray(bytesToRead)
            val cdb = ScsiCommand.createRead10Cdb(currentLba.toInt(), blocksToRead.toShort())
            
            val bytesRead = executeScsiCommand(cdb, chunkBuffer, UsbConstants.USB_DIR_IN)
            
            if (bytesRead <= 0) {
                Timber.w("Read returned $bytesRead at LBA $currentLba")
                break
            }
            
            // Copy to main buffer
            System.arraycopy(chunkBuffer, 0, buffer, offset, bytesRead)
            
            totalRead += bytesRead
            currentLba += blocksToRead
            offset += bytesRead
            remainingBlocks -= blocksToRead
        }
        
        return totalRead
    }
    
    /**
     * Sends SCSI INQUIRY command.
     */
    fun inquiry(): ByteArray {
        val buffer = ByteArray(36)
        val cdb = ByteArray(6).apply {
            this[0] = ScsiCommand.INQUIRY
            this[4] = 36 // Allocation length
        }
        executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
        return buffer
    }

    /**
     * Reads Table of Contents (TOC).
     *
     * @return Raw TOC data.
     */
    fun readToc(): ByteArray {
        // Allocation length of 804 bytes (Header + 100 tracks * 8 bytes) is usually enough
        val allocationLength = 4 + 100 * 8 
        val buffer = ByteArray(allocationLength)
        val cdb = ScsiCommand.createReadTocCdb(allocationLength)
        
        val transferred = executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
        return buffer.copyOf(transferred)
    }

    /**
     * Reads raw CD sectors (2352 bytes).
     */
    fun readCd(lba: Int, blockCount: Int): ByteArray {
        // 2352 bytes per sector for Audio CD (CD-DA)
        val bytesToRead = blockCount * 2352
        val buffer = ByteArray(bytesToRead)
        
        val cdb = ScsiCommand.createReadCdCdb(lba, blockCount)
        val transferred = executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
        
        if (transferred != bytesToRead) {
            Timber.w("Read CD incomplete: requested $bytesToRead, got $transferred")
            return buffer.copyOf(transferred)
        }
        return buffer
    }
    
    /**
     * Ejects the media using SCSI START STOP UNIT command.
     */
    fun eject() {
        val cdb = ByteArray(6).apply {
            this[0] = ScsiCommand.START_STOP_UNIT
            this[1] = 0x00 // Immed=0
            this[4] = 0x02 // LoEj=1 (Eject), Start=0 (Stop)
        }
        executeScsiCommand(cdb, null, UsbConstants.USB_DIR_IN)
    }
    
    // ===== CSS Authentication Methods =====
    
    /** Current Authentication Grant ID, or -1 if not authenticated */
    private var cssAgid: Int = -1
    
    /** Session key after successful authentication */
    private var cssSessionKey: ByteArray? = null

    /**
     * Invalidates an AGID.
     */
    private fun cssInvalidateAgid(agid: Int) {
        val cdb = ScsiCommand.createReportKeyCdb(0, agid, ScsiCommand.CSS_INVALIDATE_AGID)
        try {
            executeScsiCommand(cdb, null, UsbConstants.USB_DIR_IN)
            Timber.d("CSS: AGID $agid invalidated")
        } catch (e: Exception) {
            Timber.w(e, "CSS: Failed to invalidate AGID $agid")
        }
    }

    // ===== CSS Crypto Helper Functions =====

    // ===== CSS IOCTL Implementation for libdvdcss =====
    
    /**
     * CSS IOCTL handler for libdvdcss callbacks.
     * Routes CSS operations to the appropriate SCSI commands.
     */
    override fun cssIoctl(op: Int, data: ByteArray?, agid: IntArray, lba: Int): Int {
        return try {
            when (op) {
                CssIoctlOp.REPORT_AGID -> {
                    val result = cssScsiReportAgid()
                    if (result >= 0) {
                        agid[0] = result
                        0
                    } else -1
                }
                
                CssIoctlOp.REPORT_CHALLENGE -> {
                    val challenge = cssScsiReportChallenge(agid[0])
                    if (challenge != null && data != null && data.size >= 10) {
                        System.arraycopy(challenge, 0, data, 0, 10)
                        0
                    } else -1
                }
                
                CssIoctlOp.REPORT_KEY1 -> {
                    val key1 = cssScsiReportKey1(agid[0])
                    if (key1 != null && data != null && data.size >= 5) {
                        System.arraycopy(key1, 0, data, 0, 5)
                        0
                    } else -1
                }
                
                CssIoctlOp.REPORT_TITLE_KEY -> {
                    val titleKey = cssScsiReportTitleKey(agid[0], lba)
                    if (titleKey != null && data != null && data.size >= 5) {
                        System.arraycopy(titleKey, 0, data, 0, 5)
                        0
                    } else -1
                }
                
                CssIoctlOp.REPORT_ASF -> {
                    val asf = cssScsiReportAsf(agid[0])
                    if (asf >= 0 && data != null && data.size >= 4) {
                        // Store ASF as int in data buffer (little-endian)
                        data[0] = (asf and 0xFF).toByte()
                        data[1] = 0
                        data[2] = 0
                        data[3] = 0
                        0
                    } else -1
                }
                
                CssIoctlOp.REPORT_RPC -> {
                    val rpc = cssScsiReportRpc()
                    if (rpc != null && data != null && data.size >= 3) {
                        data[0] = rpc.type.toByte()
                        data[1] = rpc.mask.toByte()
                        data[2] = rpc.scheme.toByte()
                        0
                    } else -1
                }
                
                CssIoctlOp.SEND_CHALLENGE -> {
                    if (data != null && data.size >= 10) {
                        if (cssScsiSendChallenge(agid[0], data)) 0 else -1
                    } else -1
                }
                
                CssIoctlOp.SEND_KEY2 -> {
                    if (data != null && data.size >= 5) {
                        if (cssScsiSendKey2(agid[0], data)) 0 else -1
                    } else -1
                }
                
                CssIoctlOp.INVALIDATE_AGID -> {
                    cssScsiInvalidateAgid(agid[0])
                    0
                }
                
                CssIoctlOp.READ_COPYRIGHT -> {
                    val copyright = cssScsiReadCopyright(lba) // lba is used as layer number
                    if (copyright >= 0 && data != null && data.size >= 4) {
                        data[0] = (copyright and 0xFF).toByte()
                        data[1] = 0
                        data[2] = 0
                        data[3] = 0
                        0
                    } else -1
                }
                
                CssIoctlOp.READ_DISC_KEY -> {
                    val discKey = cssScsiReadDiscKey(agid[0])
                    if (discKey != null && data != null && data.size >= discKey.size) {
                        System.arraycopy(discKey, 0, data, 0, discKey.size)
                        0
                    } else -1
                }
                
                else -> {
                    Timber.w("Unknown CSS IOCTL operation: 0x${op.toString(16)}")
                    -1
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CSS IOCTL operation 0x${op.toString(16)} failed")
            -1
        }
    }
    
    // ===== Low-level CSS SCSI Commands =====
    
    private fun cssScsiReportAgid(): Int {
        val buffer = ByteArray(8)
        val cdb = ScsiCommand.createReportKeyCdb(8, 0, ScsiCommand.CSS_AGID)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            (buffer[7].toInt() shr 6) and 0x03
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: REPORT AGID failed")
            -1
        }
    }
    
    private fun cssScsiReportChallenge(agid: Int): ByteArray? {
        val buffer = ByteArray(16)
        val cdb = ScsiCommand.createReportKeyCdb(16, agid, ScsiCommand.CSS_CHALLENGE)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            buffer.copyOfRange(4, 14)
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: REPORT CHALLENGE failed")
            null
        }
    }
    
    private fun cssScsiReportKey1(agid: Int): ByteArray? {
        val buffer = ByteArray(12)
        val cdb = ScsiCommand.createReportKeyCdb(12, agid, ScsiCommand.CSS_KEY1)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            buffer.copyOfRange(4, 9)
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: REPORT KEY1 failed")
            null
        }
    }
    
    private fun cssScsiReportTitleKey(agid: Int, lba: Int): ByteArray? {
        val buffer = ByteArray(12)
        val cdb = ScsiCommand.createReportKeyCdb(12, agid, ScsiCommand.CSS_TITLE_KEY, lba)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            buffer.copyOfRange(5, 10)
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: REPORT TITLE KEY failed")
            null
        }
    }
    
    private fun cssScsiReportAsf(agid: Int): Int {
        val buffer = ByteArray(8)
        val cdb = ScsiCommand.createReportKeyCdb(8, agid, ScsiCommand.CSS_ASF)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            (buffer[7].toInt() and 0x01)
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: REPORT ASF failed")
            -1
        }
    }
    
    data class RpcInfo(val type: Int, val mask: Int, val scheme: Int)
    
    private fun cssScsiReportRpc(): RpcInfo? {
        val buffer = ByteArray(8)
        val cdb = ScsiCommand.createReportKeyCdb(8, 0, ScsiCommand.CSS_RPC)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            RpcInfo(
                type = (buffer[4].toInt() shr 6) and 0x03,
                mask = buffer[5].toInt() and 0xFF,
                scheme = buffer[6].toInt() and 0xFF
            )
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: REPORT RPC failed")
            null
        }
    }
    
    private fun cssScsiSendChallenge(agid: Int, challenge: ByteArray): Boolean {
        val buffer = ByteArray(16).apply {
            this[0] = 0x00
            this[1] = 0x0E // Data length
            System.arraycopy(challenge, 0, this, 4, minOf(10, challenge.size))
        }
        val cdb = ScsiCommand.createSendKeyCdb(16, agid, ScsiCommand.CSS_CHALLENGE)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_OUT)
            true
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: SEND CHALLENGE failed")
            false
        }
    }
    
    private fun cssScsiSendKey2(agid: Int, key2: ByteArray): Boolean {
        val buffer = ByteArray(12).apply {
            this[0] = 0x00
            this[1] = 0x0A // Data length
            System.arraycopy(key2, 0, this, 4, minOf(5, key2.size))
        }
        val cdb = ScsiCommand.createSendKeyCdb(12, agid, ScsiCommand.CSS_KEY2)
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_OUT)
            true
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: SEND KEY2 failed")
            false
        }
    }
    
    private fun cssScsiInvalidateAgid(agid: Int) {
        val cdb = ScsiCommand.createReportKeyCdb(0, agid, ScsiCommand.CSS_INVALIDATE_AGID)
        try {
            executeScsiCommand(cdb, null, UsbConstants.USB_DIR_IN)
        } catch (e: Exception) {
            Timber.w(e, "CSS SCSI: INVALIDATE AGID failed")
        }
    }
    
    private fun cssScsiReadCopyright(layer: Int): Int {
        val buffer = ByteArray(8)
        val cdb = ByteArray(12).apply {
            this[0] = ScsiCommand.READ_DVD_STRUCTURE
            this[6] = layer.toByte()
            this[7] = 0x01 // Copyright
            this[8] = 0x00
            this[9] = 0x08 // Allocation length
        }
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            buffer[4].toInt() and 0xFF
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: READ COPYRIGHT failed")
            -1
        }
    }
    
    private fun cssScsiReadDiscKey(agid: Int): ByteArray? {
        val buffer = ByteArray(2048 + 4) // DVD_DISCKEY_SIZE + header
        val cdb = ByteArray(12).apply {
            this[0] = ScsiCommand.READ_DVD_STRUCTURE
            this[7] = 0x02 // Disc Key
            this[8] = ((buffer.size shr 8) and 0xFF).toByte()
            this[9] = (buffer.size and 0xFF).toByte()
            this[10] = (agid shl 6).toByte()
        }
        return try {
            executeScsiCommand(cdb, buffer, UsbConstants.USB_DIR_IN)
            buffer.copyOfRange(4, 4 + 2048)
        } catch (e: Exception) {
            Timber.e(e, "CSS SCSI: READ DISC KEY failed")
            null
        }
    }

    override fun close() {
        // Invalidate AGID if authenticated
        if (cssAgid >= 0) {
            try { cssInvalidateAgid(cssAgid) } catch (_: Exception) {}
            cssAgid = -1
            cssSessionKey = null
        }
        connection.releaseInterface(usbInterface)
        connection.close()
    }

    companion object {
        // Timeouts for different phases of SCSI command execution
        private const val TIMEOUT = 5000       // Default timeout (ms)
        private const val TIMEOUT_CBW = 5000   // Timeout for sending CBW (ms)
        private const val TIMEOUT_DATA = 30000 // Timeout for data phase (ms) - longer for DVD reads
        private const val TIMEOUT_CSW = 10000  // Timeout for receiving CSW (ms)
    }
}
