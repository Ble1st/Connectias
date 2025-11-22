package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Network privacy information including DNS, VPN, and connection status.
 */
@Parcelize
data class NetworkPrivacyInfo(
    val dnsStatus: DNSStatus,
    val vpnActive: Boolean,
    val networkType: NetworkType,
    val isConnected: Boolean,
    val privateDnsEnabled: Boolean
) : Parcelable

/**
 * DNS configuration status.
 */
enum class DNSStatus {
    STANDARD,      // Using standard DNS
    PRIVATE,       // Using private DNS (DoT/DoH)
    UNKNOWN        // Cannot determine DNS status
}

/**
 * Network connection type.
 */
enum class NetworkType {
    WIFI,
    MOBILE,
    ETHERNET,
    VPN,
    NONE,
    UNKNOWN
}

