package com.ble1st.connectias.feature.network.analysis.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about a network subnet.
 */
@Parcelize
data class SubnetInfo(
    val networkAddress: String,
    val subnetMask: String,
    val cidr: String,
    val firstHost: String,
    val lastHost: String,
    val broadcastAddress: String,
    val totalHosts: Long,
    val usableHosts: Long
) : Parcelable
