package com.ble1st.connectias.feature.dvd.driver

/**
 * CSS IOCTL operation types for DVD copy protection handling.
 * These match the constants defined in dvdcss.h.
 */
object CssIoctlOp {
    const val REPORT_AGID = 0x01
    const val REPORT_CHALLENGE = 0x02
    const val REPORT_KEY1 = 0x03
    const val REPORT_TITLE_KEY = 0x04
    const val REPORT_ASF = 0x05
    const val REPORT_RPC = 0x06
    const val SEND_CHALLENGE = 0x11
    const val SEND_KEY2 = 0x12
    const val INVALIDATE_AGID = 0x3F
    const val READ_COPYRIGHT = 0x20
    const val READ_DISC_KEY = 0x21
}
