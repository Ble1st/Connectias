package com.ble1st.connectias.feature.dvd.driver.scsi

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SCSI Command definitions and Command Block Wrapper (CBW) construction.
 */
object ScsiCommand {
    // SCSI Operation Codes
    const val TEST_UNIT_READY: Byte = 0x00
    const val REQUEST_SENSE: Byte = 0x03
    const val INQUIRY: Byte = 0x12
    const val READ_10: Byte = 0x28.toByte()
    const val READ_TOC: Byte = 0x43
    const val READ_CD: Byte = 0xBE.toByte()
    const val START_STOP_UNIT: Byte = 0x1B

    // DVD CSS Authentication Commands (MMC)
    const val REPORT_KEY: Byte = 0xA4.toByte()
    const val SEND_KEY: Byte = 0xA3.toByte()
    const val READ_DVD_STRUCTURE: Byte = 0xAD.toByte()
    
    // CSS Key Format values for REPORT KEY / SEND KEY
    const val CSS_AGID: Byte = 0x00           // Authentication Grant ID
    const val CSS_CHALLENGE: Byte = 0x01      // Challenge Key
    const val CSS_KEY1: Byte = 0x02           // Key 1
    const val CSS_KEY2: Byte = 0x03           // Key 2 (for SEND KEY)
    const val CSS_TITLE_KEY: Byte = 0x04      // Title Key (for REPORT KEY)
    const val CSS_ASF: Byte = 0x05            // Authentication Success Flag
    const val CSS_RPC: Byte = 0x08            // Region Playback Control State
    const val CSS_RPC_STATE: Byte = 0x08      // Alias for CSS_RPC
    const val CSS_INVALIDATE_AGID: Byte = 0x3F // Invalidate AGID

    // Common ASC/ASCQ values
    const val ASC_NO_MEDIUM: Byte = 0x3A  // No medium present

    // USB Mass Storage Bulk-Only Transport Constants
    const val CBW_SIGNATURE = 0x43425355 // "USBC" little endian
    const val CSW_SIGNATURE = 0x53425355 // "USBS" little endian
    const val CBW_SIZE = 31
    const val CSW_SIZE = 13
    
    const val DIRECTION_OUT: Byte = 0x00
    const val DIRECTION_IN: Byte = 0x80.toByte()

    /**
     * Creates a Command Block Wrapper (CBW).
     *
     * @param tag Unique tag for the transaction.
     * @param dataLength Length of data to transfer (Data Phase).
     * @param flags Direction flags (0x80 = IN, 0x00 = OUT).
     * @param lun Logical Unit Number (usually 0).
     * @param cdb Command Data Block (the actual SCSI command). Max 16 bytes.
     */
    fun createCbw(tag: Int, dataLength: Int, flags: Byte, lun: Byte, cdb: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(CBW_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(CBW_SIGNATURE)
        buffer.putInt(tag)
        buffer.putInt(dataLength)
        buffer.put(flags)
        buffer.put(lun)
        buffer.put(cdb.size.toByte()) // CDB Length
        
        // Put CDB and pad with zeros if < 16 bytes
        buffer.put(cdb)
        val padding = 16 - cdb.size
        if (padding > 0) {
            buffer.put(ByteArray(padding))
        }

        return buffer.array()
    }
    
    /**
     * Helper to create READ(10) CDB.
     */
    fun createRead10Cdb(lba: Int, blockCount: Short): ByteArray {
        val buffer = ByteBuffer.allocate(10)
        buffer.order(ByteOrder.BIG_ENDIAN) // SCSI uses Big Endian for fields
        
        buffer.put(READ_10)
        buffer.put(0x00) // Flags (DPO/FUA etc.)
        buffer.putInt(lba)
        buffer.put(0x00) // Group number
        buffer.putShort(blockCount)
        buffer.put(0x00) // Control
        
        return buffer.array()
    }

    /**
     * Helper to create READ CD CDB (for Audio CD).
     * Reading 2352 bytes sectors (UserData + Headers + EDC/ECC usually) or just UserData (2048).
     * For Audio CD ripping we usually want full 2352 bytes.
     */
    fun createReadCdCdb(lba: Int, blockCount: Int, sectorType: Byte = 0x10): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        buffer.put(READ_CD)
        buffer.put(sectorType) // Sector type (00=Any, 04=Mode2Form1, 08=Mode2Form2, 10=CD-DA)
        buffer.putInt(lba)
        buffer.putInt(0) // Transfer length (24-bit), using 32-bit int and masking
        
        // Correctly putting 24-bit length:
        buffer.put(6, ((blockCount shr 16) and 0xFF).toByte())
        buffer.put(7, ((blockCount shr 8) and 0xFF).toByte())
        buffer.put(8, (blockCount and 0xFF).toByte())
        
        buffer.put(9, 0xF8.toByte()) // Subchannel selection (F8 = User Data + Headers + Error codes? No, standard is usually just 0x10 for audio or 0xF8 for everything)
        
        buffer.put(0x00) // Control
        
        return buffer.array()
    }

    /**
     * Helper to create READ TOC CDB.
     * Format 0 (TOC), MSF 0 (LBA).
     */
    fun createReadTocCdb(allocationLength: Int, startTrack: Int = 1): ByteArray {
        val buffer = ByteBuffer.allocate(10)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        buffer.put(READ_TOC)
        buffer.put(0x00) // MSF: 0 = LBA, 1 = MSF. Byte 1 bit 1. 0x02 = MSF. 0x00 = LBA.
        buffer.put(0x00) // Format: 0 = TOC
        buffer.put(0x00) // Reserved
        buffer.put(0x00) // Reserved
        buffer.put(0x00) // Reserved
        buffer.put(startTrack.toByte()) // Track/Session Number
        buffer.putShort(allocationLength.toShort()) // Allocation Length
        buffer.put(0x00) // Control
        
        return buffer.array()
    }
    
    /**
     * Creates REPORT KEY CDB for CSS authentication.
     * 
     * @param allocationLength Expected response length
     * @param agid Authentication Grant ID (0-3)
     * @param keyFormat Key format (CSS_AGID, CSS_CHALLENGE, CSS_KEY1, CSS_TITLE_KEY, CSS_ASF, CSS_RPC_STATE)
     * @param lba Logical Block Address (only for Title Key)
     */
    fun createReportKeyCdb(allocationLength: Int, agid: Int, keyFormat: Byte, lba: Int = 0): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        buffer.put(REPORT_KEY)
        buffer.put(0x00) // Reserved
        buffer.putInt(lba) // LBA (for Title Key only)
        buffer.put(0x00) // Reserved
        buffer.put(0x00) // Reserved
        buffer.putShort(allocationLength.toShort()) // Allocation Length
        buffer.put((((agid and 0x03) shl 6) or (keyFormat.toInt() and 0x3F)).toByte()) // AGID + Key Format
        buffer.put(0x00) // Control
        
        return buffer.array()
    }
    
    /**
     * Creates SEND KEY CDB for CSS authentication.
     * 
     * @param parameterListLength Length of data to send
     * @param agid Authentication Grant ID (0-3)
     * @param keyFormat Key format (CSS_CHALLENGE, CSS_KEY2)
     */
    fun createSendKeyCdb(parameterListLength: Int, agid: Int, keyFormat: Byte): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        buffer.put(SEND_KEY)
        buffer.put(0x00) // Reserved
        buffer.putInt(0) // Reserved
        buffer.put(0x00) // Reserved
        buffer.put(0x00) // Reserved
        buffer.putShort(parameterListLength.toShort()) // Parameter List Length
        buffer.put((((agid and 0x03) shl 6) or (keyFormat.toInt() and 0x3F)).toByte()) // AGID + Key Format
        buffer.put(0x00) // Control
        
        return buffer.array()
    }
}
