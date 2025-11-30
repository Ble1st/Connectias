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
    init {
        val nodeIds = nodes.map { it.id }.toSet()
        val invalidEdges = edges.filter { 
            it.fromNodeId !in nodeIds || it.toNodeId !in nodeIds 
        }
        require(invalidEdges.isEmpty()) { 
            "Edges reference non-existent nodes: ${invalidEdges.map { "${it.fromNodeId} -> ${it.toNodeId}" }}" 
        }
    }

    fun getNodeById(id: String): TopologyNode? {
        return nodes.find { it.id == id }
    }

    fun getEdgesForNode(nodeId: String): List<TopologyEdge> {
        return edges.filter { it.fromNodeId == nodeId || it.toNodeId == nodeId }
    }
}
