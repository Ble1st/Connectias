package com.ble1st.connectias.feature.network.analysis.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about a VLAN (Virtual LAN).
 * Since Android doesn't provide direct VLAN tag access, this is inferred from subnet segmentation.
 */
@Parcelize
data class VlanInfo(
    val vlanId: Int?,
    val subnetInfo: SubnetInfo,
    val devices: List<String> // IP addresses in this VLAN
) : Parcelable {
    /**
     * Number of devices in this VLAN.
     * Computed from devices.size to avoid redundancy.
     */
    val deviceCount: Int get() = devices.size
}
