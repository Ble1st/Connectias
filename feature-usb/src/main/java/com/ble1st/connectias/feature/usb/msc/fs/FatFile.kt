package com.ble1st.connectias.feature.usb.msc.fs

import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Provides read access to a file backed by a FAT cluster chain.
 */
class FatFile(
    private val fs: FatFileSystem,
    private val entry: FatDirectoryEntry
) {
    val name: String get() = entry.name
    val length: Long get() = entry.size
    val lastModified: Long get() = entry.lastModified
    val isDirectory: Boolean get() = entry.isDirectory

    fun read(offset: Long, buffer: ByteBuffer) {
        require(!entry.isDirectory) { "Cannot read directory" }
        val boot = fs.getBoot()
        val fat = fs.getFat()
        val clusterSize = boot.clusterSizeBytes
        var remaining = min(buffer.remaining().toLong(), length - offset)
        if (remaining <= 0) return
        var clusterIndex = (offset / clusterSize).toInt()
        var clusterOffset = (offset % clusterSize).toInt()
        var cluster = entry.firstCluster
        var traversed = 0
        while (traversed < clusterIndex && cluster >= 2 && !fat.isEoc(cluster)) {
            cluster = fat.getNext(cluster)
            traversed++
        }
        while (remaining > 0 && cluster >= 2) {
            val data = fs.readCluster(cluster)
            val toCopy = min(remaining.toInt(), data.size - clusterOffset)
            buffer.put(data, clusterOffset, toCopy)
            remaining -= toCopy.toLong()
            clusterOffset = 0
            if (remaining <= 0) break
            cluster = fat.getNext(cluster)
            if (fat.isEoc(cluster)) {
                // include final cluster
                if (remaining > 0) {
                    val lastData = fs.readCluster(cluster)
                    val copy = min(remaining.toInt(), lastData.size)
                    buffer.put(lastData, 0, copy)
                    remaining -= copy.toLong()
                }
                break
            }
        }
    }

    // Placeholder write support: currently not implemented.
    fun write(offset: Long, buffer: ByteBuffer) {
        throw UnsupportedOperationException("Write support not implemented yet")
    }
}
