package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a DHCP lease (inferred from network discovery).
 * Since Android doesn't provide direct DHCP access, this is inferred from device discovery.
 */
@Parcelize
data class DhcpLease(
    val ipAddress: String,
    val hostname: String,
    val macAddress: String?,
    val leaseStartTime: Long?,
    val leaseExpiryTime: Long?,
    val isStatic: Boolean, // true if IP appears to be static (doesn't change)
    val lastSeen: Long
) : Parcelable
