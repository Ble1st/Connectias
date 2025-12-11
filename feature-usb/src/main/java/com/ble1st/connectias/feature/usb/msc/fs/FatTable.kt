package com.ble1st.connectias.feature.usb.msc.fs

import com.ble1st.connectias.feature.usb.msc.storage.PartitionBlockDevice
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Provides next-cluster lookup for FAT32.
 */
class FatTable(
    private val device: PartitionBlockDevice,
    private val boot: FatBootSector
) {
    private val fatOffsetLba: Long = boot.reservedSectors.toLong()
    private val entriesPerSector = device.blockSize / 4

    fun getNext(cluster: Long): Long {
        val fatIndex = cluster.toInt()
        val sector = fatIndex / entriesPerSector
        val offset = (fatIndex % entriesPerSector) * 4
        val sectorData = device.read(fatOffsetLba + sector, 1)
        val entry = ByteBuffer.wrap(sectorData, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return entry.toLong() and 0x0FFFFFFFL
    }

    fun isEoc(cluster: Long): Boolean = cluster >= 0x0FFFFFF8
}
