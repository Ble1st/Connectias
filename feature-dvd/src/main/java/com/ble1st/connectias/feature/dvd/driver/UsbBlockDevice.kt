package com.ble1st.connectias.feature.dvd.driver

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
