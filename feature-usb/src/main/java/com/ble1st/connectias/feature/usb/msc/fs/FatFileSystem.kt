package com.ble1st.connectias.feature.usb.msc.fs

import com.ble1st.connectias.feature.usb.msc.storage.PartitionBlockDevice
import java.nio.ByteBuffer

class FatFileSystem(
    private val device: PartitionBlockDevice,
    private val boot: FatBootSector,
    private val fat: FatTable
) {
    private val dataStartLba: Long =
        boot.reservedSectors.toLong() + (boot.numFats.toLong() * boot.sectorsPerFat.toLong())

    val rootDirectory: FatDirectory = FatDirectory(
        fs = this,
        startCluster = boot.rootCluster,
        name = "/",
        lastModified = 0L
    )

    fun clusterToLba(cluster: Long): Long {
        return dataStartLba + (cluster - 2) * boot.sectorsPerCluster
    }

    fun readCluster(cluster: Long): ByteArray {
        return device.read(clusterToLba(cluster), boot.sectorsPerCluster)
    }

    fun getFat(): FatTable = fat

    fun getBoot(): FatBootSector = boot

    fun getDevice(): PartitionBlockDevice = device
}
