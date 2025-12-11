package com.ble1st.connectias.feature.usb.msc.api

import java.nio.ByteBuffer

interface UsbFile {
    val name: String
    val isDirectory: Boolean
    val length: Long
    val lastModified: Long

    fun listFiles(): Array<UsbFile>
    fun read(offset: Long, buffer: ByteBuffer)
}
