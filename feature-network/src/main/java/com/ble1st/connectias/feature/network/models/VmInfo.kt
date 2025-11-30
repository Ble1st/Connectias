package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about a virtual machine.
 */
@Parcelize
data class VmInfo(
    val ipAddress: String,
    val hostname: String,
    val macAddress: String?,
    val hypervisorType: HypervisorType,
    val hostIp: String?
) : Parcelable
