package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about a hypervisor or virtualization platform.
 */
@Parcelize
data class HypervisorInfo(
    val type: HypervisorType,
    val hostIp: String?,
    val hostname: String?,
    val detectedVms: List<VmInfo>
) : Parcelable

/**
 * Type of hypervisor or virtualization platform.
 */
enum class HypervisorType {
    VMWARE,
    VIRTUALBOX,
    KVM,
    HYPER_V,
    PARALLELS,
    UNKNOWN
}
