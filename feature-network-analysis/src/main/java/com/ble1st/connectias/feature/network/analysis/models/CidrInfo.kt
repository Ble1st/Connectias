package com.ble1st.connectias.feature.network.analysis.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * CIDR notation information.
 */
@Parcelize
data class CidrInfo(
    val ipAddress: String,
    val prefixLength: Int
) : Parcelable {
    /**
     * CIDR notation string computed from ipAddress and prefixLength.
     * Format: "ipAddress/prefixLength"
     */
    val cidrNotation: String get() = "$ipAddress/$prefixLength"
}
