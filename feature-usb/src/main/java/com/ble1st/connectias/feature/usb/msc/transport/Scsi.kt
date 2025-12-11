package com.ble1st.connectias.feature.usb.msc.transport

/**
 * Basic SCSI command opcodes used for USB Mass Storage BOT.
 */
object Scsi {
    const val INQUIRY: Byte = 0x12
    const val REQUEST_SENSE: Byte = 0x03
    const val TEST_UNIT_READY: Byte = 0x00
    const val READ_CAPACITY_10: Byte = 0x25
    const val READ_10: Byte = 0x28
    const val WRITE_10: Byte = 0x2A
}

data class ScsiCapacity(
    val blockCount: Long,
    val blockSize: Int
)
