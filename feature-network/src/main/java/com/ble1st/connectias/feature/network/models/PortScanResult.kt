package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Result of a port scan operation.
 * Parcelable for data transfer between components.
 */
@Parcelize
data class PortScanResult(
    val host: String,
    val port: Int,
    val isOpen: Boolean,
    val service: String? = null
) : Parcelable

