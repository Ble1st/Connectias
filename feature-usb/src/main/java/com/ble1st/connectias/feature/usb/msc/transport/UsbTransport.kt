package com.ble1st.connectias.feature.usb.msc.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Implements a minimal Bulk-Only Transport (BOT) layer for SCSI commands.
 *
 * This is intentionally small and synchronous; higher layers are responsible for threading.
 */
class UsbTransport(
    private val connection: UsbDeviceConnection,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint
    ) {

    private var cbwTag: Int = 1

    fun readCapacity(): ScsiCapacity {
        val cbw = buildCbw(
            dataTransferLength = 8,
            flags = DIRECTION_IN,
            cbwcb = byteArrayOf(
                Scsi.READ_CAPACITY_10,
                0, 0, 0, 0, // LBA
                0, 0, 0, 0, // PMI
                0
            )
        )
        transferOut(cbw)
        val data = transferIn(8)
        val csw = readCsw()
        require(csw.status == 0.toByte()) { "READ CAPACITY failed, status=${csw.status}" }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val lastLba = buf.int.toLong() and 0xFFFFFFFFL
        val blockSize = buf.int
        return ScsiCapacity(blockCount = lastLba + 1, blockSize = blockSize)
    }

    fun readBlocks(lba: Long, blocks: Int, blockSize: Int, out: ByteArray) {
        val transferLength = blocks * blockSize
        require(out.size >= transferLength) { "Output buffer too small" }
        val cbw = buildCbw(
            dataTransferLength = transferLength,
            flags = DIRECTION_IN,
            cbwcb = byteArrayOf(
                Scsi.READ_10,
                0,
                ((lba shr 24) and 0xFF).toByte(),
                ((lba shr 16) and 0xFF).toByte(),
                ((lba shr 8) and 0xFF).toByte(),
                (lba and 0xFF).toByte(),
                0,
                ((blocks shr 8) and 0xFF).toByte(),
                (blocks and 0xFF).toByte(),
                0
            )
        )
        transferOut(cbw)
        val data = transferIn(transferLength)
        System.arraycopy(data, 0, out, 0, transferLength)
        val csw = readCsw()
        require(csw.status == 0.toByte()) { "READ10 failed, status=${csw.status}" }
    }

    fun writeBlocks(lba: Long, blocks: Int, blockSize: Int, data: ByteArray) {
        val transferLength = blocks * blockSize
        require(data.size >= transferLength) { "Input buffer too small" }
        val cbw = buildCbw(
            dataTransferLength = transferLength,
            flags = DIRECTION_OUT,
            cbwcb = byteArrayOf(
                Scsi.WRITE_10,
                0,
                ((lba shr 24) and 0xFF).toByte(),
                ((lba shr 16) and 0xFF).toByte(),
                ((lba shr 8) and 0xFF).toByte(),
                (lba and 0xFF).toByte(),
                0,
                ((blocks shr 8) and 0xFF).toByte(),
                (blocks and 0xFF).toByte(),
                0
            )
        )
        transferOut(cbw)
        transferOut(data.copyOfRange(0, transferLength))
        val csw = readCsw()
        require(csw.status == 0.toByte()) { "WRITE10 failed, status=${csw.status}" }
    }

    private fun buildCbw(
        dataTransferLength: Int,
        flags: Byte,
        cbwcb: ByteArray
    ): ByteArray {
        val cbw = ByteArray(31) { 0 }
        val buf = ByteBuffer.wrap(cbw).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(CBW_SIGNATURE)
        buf.putInt(cbwTag++)
        buf.putInt(dataTransferLength)
        buf.put(flags)
        buf.put(LUN_0)
        buf.put(cbwcb.size.toByte())
        buf.put(cbwcb.copyOf(16))
        return cbw
    }

    private fun transferOut(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val wrote = connection.bulkTransfer(
                outEndpoint,
                data,
                offset,
                data.size - offset,
                TRANSFER_TIMEOUT_MS
            )
            require(wrote > 0) { "bulk out failed" }
            offset += wrote
        }
    }

    private fun transferIn(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = connection.bulkTransfer(
                inEndpoint,
                buffer,
                offset,
                length - offset,
                TRANSFER_TIMEOUT_MS
            )
            require(read > 0) { "bulk in failed" }
            offset += read
        }
        return buffer
    }

    private fun readCsw(): CommandStatusWrapper {
        val buf = transferIn(13)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        val signature = bb.int
        require(signature == CSW_SIGNATURE) { "Invalid CSW signature: $signature" }
        val tag = bb.int
        val residue = bb.int
        val status = bb.get()
        return CommandStatusWrapper(tag, residue, status)
    }

    data class CommandStatusWrapper(
        val tag: Int,
        val residue: Int,
        val status: Byte
    )

    companion object {
        private const val CBW_SIGNATURE = 0x43425355
        private const val CSW_SIGNATURE = 0x53425355
        private const val LUN_0: Byte = 0
        private const val DIRECTION_IN: Byte = UsbConstants.USB_DIR_IN.toByte()
        private const val DIRECTION_OUT: Byte = UsbConstants.USB_DIR_OUT.toByte()
        private const val TRANSFER_TIMEOUT_MS = 5000
    }
}
