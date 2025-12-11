package com.ble1st.connectias.feature.usb.msc.api

import com.ble1st.connectias.feature.usb.msc.fs.FatDirectory
import com.ble1st.connectias.feature.usb.msc.fs.FatDirectoryEntry
import com.ble1st.connectias.feature.usb.msc.fs.FatFile
import com.ble1st.connectias.feature.usb.msc.fs.FatFileSystem
import java.nio.ByteBuffer

class FatUsbDirectory(
    private val fs: FatFileSystem,
    private val dir: FatDirectory
) : UsbFile {
    override val name: String = dir.name
    override val isDirectory: Boolean = true
    override val length: Long = 0
    override val lastModified: Long = dir.lastModified

    override fun listFiles(): Array<UsbFile> {
        val entries = dir.listEntries()
        return entries.map { entry ->
            if (entry.isDirectory) {
                FatUsbDirectory(fs, FatDirectory(fs, entry.firstCluster, entry.name, entry.lastModified))
            } else {
                FatUsbFile(fs, entry)
            }
        }.toTypedArray()
    }

    override fun read(offset: Long, buffer: ByteBuffer) {
        throw UnsupportedOperationException("Cannot read directory")
    }
}
