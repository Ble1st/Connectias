package com.ble1st.connectias.feature.network.topology.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Complete network topology representation.
 */
@Parcelize
data class NetworkTopology(
    val nodes: List<TopologyNode>,
    val edges: List<TopologyEdge>
) : Parcelable {
    fun getNodeById(id: String): TopologyNode? {
        return nodes.find { it.id == id }
    }

    fun getEdgesForNode(nodeId: String): List<TopologyEdge> {
        return edges.filter { it.fromNodeId == nodeId || it.toNodeId == nodeId }
    }
}
