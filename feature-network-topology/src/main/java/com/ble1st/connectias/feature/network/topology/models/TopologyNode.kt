package com.ble1st.connectias.feature.network.topology.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a node in the network topology graph.
 */
@Parcelize
data class TopologyNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val ipAddress: String?,
    val macAddress: String?,
    val x: Float = 0f,
    val y: Float = 0f
) : Parcelable {
    init {
        require(id.isNotBlank()) { "Node id cannot be blank" }
        require(label.isNotBlank()) { "Node label cannot be blank" }
    }
}

/**
 * Type of network node.
 */
enum class NodeType {
    GATEWAY,
    ROUTER,
    SWITCH,
    DEVICE,
    SUBNET
}
