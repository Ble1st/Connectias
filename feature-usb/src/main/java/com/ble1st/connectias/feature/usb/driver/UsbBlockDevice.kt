package com.ble1st.connectias.feature.usb.driver

/**
 * CSS IOCTL operation types for DVD copy protection handling.
 * These match the constants defined in dvdcss.h.
 */
object CssIoctlOp {
    const val REPORT_AGID = 0x01
    const val REPORT_CHALLENGE = 0x02
    const val REPORT_KEY1 = 0x03
    const val REPORT_TITLE_KEY = 0x04
    const val REPORT_ASF = 0x05
    const val REPORT_RPC = 0x06
    const val SEND_CHALLENGE = 0x11
    const val SEND_KEY2 = 0x12
    const val INVALIDATE_AGID = 0x3F
    const val READ_COPYRIGHT = 0x20
    const val READ_DISC_KEY = 0x21
}

/**
 * Interface for a block-based USB device.
 * Abstracts the underlying communication protocol (e.g., SCSI/Bulk-Only Transport).
 */
interface UsbBlockDevice {
    /**
     * Device block size in bytes (typically 512 for DVD/HDD, 2048 or 2352 for CD).
     */
    val blockSize: Int

    /**
     * Total number of addressable blocks.
     */
    val blockCount: Long

    /**
     * Reads data from the device.
     *
     * @param lba Logical Block Address to start reading from.
     * @param buffer Buffer to store the read data.
     * @param length Number of bytes to read.
     * @return Number of bytes actually read, or -1 on error.
     */
    fun read(lba: Long, buffer: ByteArray, length: Int): Int
    
    /**
     * Performs a CSS (Content Scramble System) IOCTL operation.
     * This is used by libdvdcss for DVD copy protection handling.
     *
     * @param op Operation type (see [CssIoctlOp] constants)
     * @param data Data buffer for input/output (may be null for some operations)
     * @param agid Authentication Grant ID (input/output parameter)
     * @param lba Logical Block Address (used for title key operations)
     * @return 0 on success, negative on error
     */
    fun cssIoctl(op: Int, data: ByteArray?, agid: IntArray, lba: Int): Int {
        // Default implementation returns error - override in implementations that support CSS
        return -1
    }
    
    /**
     * Closes the device connection.
     */
    fun close()
}
