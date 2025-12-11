package com.ble1st.connectias.feature.usb.msc.fs

/**
 * Represents a directory backed by a FAT cluster chain.
 */
class FatDirectory(
    private val fs: FatFileSystem,
    private val startCluster: Long,
    val name: String,
    val lastModified: Long
) {

    fun listEntries(): List<FatDirectoryEntry> {
        val data = readAllClusters()
        return FatDirParser.parse(data)
    }

    fun findChild(target: String): FatDirectoryEntry? {
        return listEntries().firstOrNull { it.name == target }
    }

    private fun readAllClusters(): ByteArray {
        val fat = fs.getFat()
        val boot = fs.getBoot()
        val buffers = mutableListOf<ByteArray>()
        var cluster = startCluster
        while (cluster >= 2 && !fat.isEoc(cluster)) {
            buffers.add(fs.readCluster(cluster))
            cluster = fat.getNext(cluster)
        }
        // include last cluster if EOC is marked on current
        if (cluster >= 2 && fat.isEoc(cluster)) {
            buffers.add(fs.readCluster(cluster))
        }
        val total = buffers.sumOf { it.size }
        val out = ByteArray(total)
        var offset = 0
        buffers.forEach { buf ->
            System.arraycopy(buf, 0, out, offset, buf.size)
            offset += buf.size
        }
        return out
    }
}
