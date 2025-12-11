package com.ble1st.connectias.feature.usb.msc.storage

/**
 * BlockDevice view scoped to a partition (adds LBA offset).
 */
class PartitionBlockDevice(
    private val parent: BlockDevice,
    private val startLba: Long,
    val totalBlocks: Long
) {
    val blockSize: Int get() = parent.blockSize

    fun read(lba: Long, blocks: Int): ByteArray {
        require(lba + blocks <= totalBlocks) { "Read beyond partition" }
        return parent.read(startLba + lba, blocks)
    }

    fun write(lba: Long, blocks: Int, data: ByteArray) {
        require(lba + blocks <= totalBlocks) { "Write beyond partition" }
        parent.write(startLba + lba, blocks, data)
    }
}
