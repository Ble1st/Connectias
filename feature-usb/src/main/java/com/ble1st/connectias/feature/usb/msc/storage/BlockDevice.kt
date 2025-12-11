package com.ble1st.connectias.feature.usb.msc.storage

import com.ble1st.connectias.feature.usb.msc.transport.UsbTransport
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Thin block device wrapper around UsbTransport.
 */
class BlockDevice(
    private val transport: UsbTransport,
    val blockSize: Int,
    val blockCount: Long
) {

    fun read(lba: Long, blocks: Int): ByteArray {
        val out = ByteArray(blocks * blockSize)
        transport.readBlocks(lba, blocks, blockSize, out)
        return out
    }

    fun write(lba: Long, blocks: Int, data: ByteArray) {
        transport.writeBlocks(lba, blocks, blockSize, data)
    }
}
