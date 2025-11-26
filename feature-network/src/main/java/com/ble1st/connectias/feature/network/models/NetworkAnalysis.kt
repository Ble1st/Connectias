package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import com.ble1st.connectias.core.models.ConnectionType
import kotlinx.parcelize.Parcelize

/**
 * Represents network analysis information.
 */
@Parcelize
data class NetworkAnalysis(
    val isConnected: Boolean,
    val dnsServers: List<String>,
    val gateway: String?,
    val networkSpeed: Long?, // Optional, in bits per second
    val connectionType: ConnectionType
) : Parcelable

