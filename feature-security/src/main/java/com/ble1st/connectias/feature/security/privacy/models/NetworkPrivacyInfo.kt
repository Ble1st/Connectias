package com.ble1st.connectias.feature.security.privacy.models

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
     * This is an explicit constructor parameter representing the user's configured preference
     * for private DNS (whether they have enabled private DNS in system settings).
     * This is not computed from dnsStatus.
     * 
     * Note: The runtime DNS state (dnsStatus) may differ from this user preference.
     * For example, private DNS may be enabled by the user but not active at runtime
     * due to network conditions, configuration issues, or DNS resolution failures.
     */
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

