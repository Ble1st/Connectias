package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Network flow statistics.
 */
@Parcelize
data class FlowStats(
    val topTalkers: List<TopTalker>,
    val protocolDistribution: Map<String, Long>,
    val totalFlows: Int,
    val totalBytes: Long
) : Parcelable

/**
 * Top talker (device with most traffic).
 */
@Parcelize
data class TopTalker(
    val ipAddress: String,
    val hostname: String?,
    val bytesTransferred: Long,
    val flowCount: Int
) : Parcelable
