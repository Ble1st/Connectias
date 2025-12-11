package com.ble1st.connectias.feature.usb.msc.api

data class FileSystem(
    val rootDirectory: UsbFile,
    val volumeLabel: String? = null,
    val capacity: Long = 0,
    val freeSpace: Long = 0
)
