package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Network privacy information including DNS, VPN, and connection status.
 */
@Parcelize
data class NetworkPrivacyInfo(
    /**
     * DNS configuration status observed at runtime.
     * PRIVATE indicates private DNS (DoT/DoH) is active.
     * STANDARD indicates standard DNS is being used.
     * UNKNOWN indicates the status could not be determined.
     */
    val dnsStatus: DNSStatus,
    val vpnActive: Boolean,
    val networkType: NetworkType,
    val isConnected: Boolean,
    /**
     * Whether private DNS is enabled (user setting).
     * This represents the user's configuration preference.
     * Note: This may differ from dnsStatus if private DNS is enabled but not active
     * (e.g., due to network conditions or configuration issues).
     * Derived from dnsStatus for consistency: privateDnsEnabled = (dnsStatus == DNSStatus.PRIVATE)
     */
    val privateDnsEnabled: Boolean
) : Parcelable {
    init {
        // Validate consistency between privateDnsEnabled and dnsStatus
        // If privateDnsEnabled is true, dnsStatus should be PRIVATE (or UNKNOWN if status couldn't be determined)
        // If privateDnsEnabled is false, dnsStatus should be STANDARD (or UNKNOWN if status couldn't be determined)
        require(
            (privateDnsEnabled && (dnsStatus == DNSStatus.PRIVATE || dnsStatus == DNSStatus.UNKNOWN)) ||
            (!privateDnsEnabled && (dnsStatus == DNSStatus.STANDARD || dnsStatus == DNSStatus.UNKNOWN))
        ) {
            "Inconsistent state: privateDnsEnabled=$privateDnsEnabled but dnsStatus=$dnsStatus"
        }
    }
}
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

