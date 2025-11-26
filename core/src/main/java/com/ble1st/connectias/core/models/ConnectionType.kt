package com.ble1st.connectias.core.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents network connection types.
 */
@Parcelize
enum class ConnectionType : Parcelable {
    WIFI,
    MOBILE,
    VPN,
    ETHERNET,
    UNKNOWN,
    NONE
}