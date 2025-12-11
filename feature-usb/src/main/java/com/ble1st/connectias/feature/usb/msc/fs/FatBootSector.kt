package com.ble1st.connectias.feature.usb.msc.fs

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses a FAT32 boot sector.
 */
data class FatBootSector(
    val bytesPerSector: Int,
    val sectorsPerCluster: Int,
    val reservedSectors: Int,
    val numFats: Int,
    val sectorsPerFat: Int,
    val rootCluster: Long,
    val totalSectors: Long
) {
    val clusterSizeBytes: Int = bytesPerSector * sectorsPerCluster

    companion object {
        fun parse(buf: ByteArray): FatBootSector {
            val b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            val bytesPerSector = b.getShort(11).toInt() and 0xFFFF
            val sectorsPerCluster = b.get(13).toInt() and 0xFF
            val reserved = b.getShort(14).toInt() and 0xFFFF
            val numFats = b.get(16).toInt() and 0xFF
            val total16 = b.getShort(19).toInt() and 0xFFFF
            val sectorsPerFat16 = b.getShort(22).toInt() and 0xFFFF
            val total32 = b.getInt(32)
            val totalSectors = if (total16 != 0) total16.toLong() else total32.toLong() and 0xFFFFFFFFL
            val sectorsPerFat32 = b.getInt(36)
            val rootCluster = b.getInt(44).toLong() and 0xFFFFFFFFL
            val sectorsPerFat = if (sectorsPerFat16 != 0) sectorsPerFat16 else sectorsPerFat32
            return FatBootSector(
                bytesPerSector = bytesPerSector,
                sectorsPerCluster = sectorsPerCluster,
                reservedSectors = reserved,
                numFats = numFats,
                sectorsPerFat = sectorsPerFat,
                rootCluster = rootCluster,
                totalSectors = totalSectors
            )
        }
    }
}
