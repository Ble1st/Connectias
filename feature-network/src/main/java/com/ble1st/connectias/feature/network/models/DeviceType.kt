package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents device types discovered on the network.
 * Uses generic categories to avoid platform-specific overlap.
 */
@Parcelize
enum class DeviceType : Parcelable {
    ROUTER,
    COMPUTER,
    SMARTPHONE,
    TABLET,
    IOT_DEVICE,
    PRINTER,
    UNKNOWN
}

