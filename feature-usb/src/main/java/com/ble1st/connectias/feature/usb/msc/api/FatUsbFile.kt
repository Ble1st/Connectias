package com.ble1st.connectias.feature.usb.msc.api

import com.ble1st.connectias.feature.usb.msc.fs.FatDirectoryEntry
import com.ble1st.connectias.feature.usb.msc.fs.FatFile
import com.ble1st.connectias.feature.usb.msc.fs.FatFileSystem
import java.nio.ByteBuffer

class FatUsbFile(
    private val fs: FatFileSystem,
    private val entry: FatDirectoryEntry
) : UsbFile {
    private val delegate = FatFile(fs, entry)

    override val name: String get() = delegate.name
    override val isDirectory: Boolean get() = delegate.isDirectory
    override val length: Long get() = delegate.length
    override val lastModified: Long get() = delegate.lastModified

    override fun listFiles(): Array<UsbFile> = emptyArray()

    override fun read(offset: Long, buffer: ByteBuffer) {
        delegate.read(offset, buffer)
    }
}
