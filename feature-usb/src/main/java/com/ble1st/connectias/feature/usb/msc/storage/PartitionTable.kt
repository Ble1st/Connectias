package com.ble1st.connectias.feature.usb.msc.storage

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PartitionEntry(
    val startLba: Long,
    val totalLba: Long,
    val type: Int
)

/**
 * Minimal partition parsing: tries GPT first, then falls back to MBR.
 */
object PartitionTable {

    fun parse(blockDevice: BlockDevice): List<PartitionEntry> {
        // Try GPT: header at LBA 1
        val gptHeader = blockDevice.read(1, 1)
        if (isGptHeader(gptHeader)) {
            return parseGpt(blockDevice)
        }
        // Fallback to MBR
        val mbr = blockDevice.read(0, 1)
        return parseMbr(mbr)
    }

    private fun isGptHeader(buf: ByteArray): Boolean {
        if (buf.size < 8) return false
        return buf.copyOfRange(0, 8).decodeToString() == "EFI PART"
    }

    private fun parseGpt(blockDevice: BlockDevice): List<PartitionEntry> {
        val header = blockDevice.read(1, 1)
        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        h.position(72) // number of partition entries
        val entriesCount = h.int
        val entrySize = h.int
        h.position(32)
        val partLba = h.long
        val entriesPerBlock = blockDevice.blockSize / entrySize
        val totalBlocks = (entriesCount + entriesPerBlock - 1) / entriesPerBlock
        val entries = mutableListOf<PartitionEntry>()
        for (i in 0 until totalBlocks) {
            val buf = blockDevice.read(partLba + i, 1)
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            for (j in 0 until entriesPerBlock) {
                val offset = j * entrySize
                if (offset + entrySize > buf.size) break
                bb.position(offset)
                val firstLba = bb.long
                val lastLba = bb.long
                val attrs = bb.long // unused
                val nameBytes = ByteArray(72)
                bb.get(nameBytes)
                if (firstLba > 0 && lastLba >= firstLba) {
                    entries.add(
                        PartitionEntry(
                            startLba = firstLba,
                            totalLba = lastLba - firstLba + 1,
                            type = 0xEE // GPT placeholder
                        )
                    )
                }
            }
        }
        return entries
    }

    private fun parseMbr(mbr: ByteArray): List<PartitionEntry> {
        if (mbr.size < 512) return emptyList()
        val signature = ((mbr[510].toInt() and 0xFF) shl 8) or (mbr[511].toInt() and 0xFF)
        if (signature != 0x55AA) return emptyList()
        val entries = mutableListOf<PartitionEntry>()
        for (i in 0 until 4) {
            val offset = 446 + i * 16
            val partType = mbr[offset + 4].toInt() and 0xFF
            val startLba = ByteBuffer.wrap(mbr, offset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val totalLba = ByteBuffer.wrap(mbr, offset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            if (partType != 0 && totalLba > 0) {
                entries.add(PartitionEntry(startLba = startLba, totalLba = totalLba, type = partType))
            }
        }
        return entries
    }
}
