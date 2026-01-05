package com.ble1st.connectias.feature.dvd.driver

/**
 * Interface for a USB Mass Storage Block Device (e.g., SCSI).
 * Provides raw read access to sectors.
 */
interface UsbBlockDevice : AutoCloseable {
    /**
     * Reads data from the device.
     * @param lba Logical Block Address to start reading from.
     * @param buffer Destination buffer.
     * @param length Number of bytes to read.
     * @return Number of bytes read, or -1 on error.
     */
    fun read(lba: Long, buffer: ByteArray, length: Int): Int
    
    /**
     * Gets the logical block size of the device (e.g. 2048 for DVD).
     */
    val blockSize: Int
    
    /**
     * Gets the total block count of the device.
     */
    val blockCount: Long
    
    /**
     * Performs a raw CSS IOCTL operation (for decryption).
     */
    fun cssIoctl(op: Int, data: ByteArray?, agid: IntArray, lba: Int): Int
    
    /**
     * Closes the device connection.
     */
    override fun close()
}
