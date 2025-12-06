package com.ble1st.connectias.feature.network.topology.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents an edge (connection) between two nodes in the network topology graph.
 */
@Parcelize
data class TopologyEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val type: EdgeType
) : Parcelable

/**
 * Type of network edge/connection.
 */
enum class EdgeType {
    DIRECT,
    ROUTED,
    SUBNET
}
