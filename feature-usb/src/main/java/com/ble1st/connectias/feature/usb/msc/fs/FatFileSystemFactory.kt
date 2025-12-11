package com.ble1st.connectias.feature.usb.msc.fs

import com.ble1st.connectias.feature.usb.msc.storage.PartitionBlockDevice

object FatFileSystemFactory {
    fun create(device: PartitionBlockDevice): FatFileSystem {
        // Read boot sector (first sector of partition)
        val bootSector = device.read(0, 1)
        val boot = FatBootSector.parse(bootSector)
        val fat = FatTable(device, boot)
        return FatFileSystem(device, boot, fat)
    }
}
