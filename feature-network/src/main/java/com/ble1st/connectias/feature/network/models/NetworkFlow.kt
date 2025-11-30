package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a network flow (connection).
 * 
 * @param timestamp Unix epoch time in milliseconds
 */
@Parcelize
data class NetworkFlow(
    val sourceIp: String,
    val destinationIp: String,
    val protocol: String?,
    val bytesTransferred: Long,
    val packetsTransferred: Long,
    val timestamp: Long
) : Parcelable {
    init {
        require(bytesTransferred >= 0) { "bytesTransferred must be non-negative" }
        require(packetsTransferred >= 0) { "packetsTransferred must be non-negative" }
    }
}
