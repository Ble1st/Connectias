package com.ble1st.connectias.feature.network.topology.topology

import com.ble1st.connectias.core.services.NetworkService
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.topology.models.NetworkTopology
import com.ble1st.connectias.feature.network.topology.models.TopologyEdge
import com.ble1st.connectias.feature.network.topology.models.TopologyNode
import com.ble1st.connectias.feature.network.topology.models.NodeType
import com.ble1st.connectias.feature.network.topology.models.EdgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for network topology mapping.
 * Builds a graph representation of the network based on devices, gateways, and subnets.
 */
@Singleton
class TopologyMapperProvider @Inject constructor(
    private val networkService: NetworkService
) {

    /**
     * Builds network topology from discovered devices and network information.
     * 
     * @param devices List of discovered network devices
     * @return NetworkTopology graph
     */
    suspend fun buildTopology(devices: List<NetworkDevice>): NetworkTopology = withContext(Dispatchers.IO) {
        try {
            val nodes = mutableListOf<TopologyNode>()
            val edges = mutableListOf<TopologyEdge>()

            // Get gateway information
            val gateway = networkService.getGateway()

            // Create gateway node if available
            val gatewayNodeId = gateway?.let { gw ->
                val nodeId = "gateway_$gw"
                nodes.add(
                    TopologyNode(
                        id = nodeId,
                        label = "Gateway",
                        type = NodeType.GATEWAY,
                        ipAddress = gw,
                        macAddress = null
                    )
                )
                nodeId
            }

            // Group devices by subnet (simplified: use first 3 octets for /24)
            val devicesBySubnet = devices.groupBy { device ->
                val parts = device.ipAddress.split(".")
                if (parts.size == 4) {
                    "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
                } else {
                    "unknown"
                }
            }

            // Create subnet nodes and connect devices
            devicesBySubnet.forEach { (subnetCidr, subnetDevices) ->
                val subnetNodeId = "subnet_${subnetCidr.replace("/", "_")}"
                
                // Create subnet node
                nodes.add(
                    TopologyNode(
                        id = subnetNodeId,
                        label = subnetCidr,
                        type = NodeType.SUBNET,
                        ipAddress = null,
                        macAddress = null
                    )
                )

                // Connect gateway to subnet if available
                gatewayNodeId?.let {
                    edges.add(
                        TopologyEdge(
                            fromNodeId = it,
                            toNodeId = subnetNodeId,
                            type = EdgeType.ROUTED
                        )
                    )
                }

                // Create device nodes and connect to subnet
                subnetDevices.forEach { device ->
                    val deviceNodeId = "device_${device.ipAddress}"
                    nodes.add(
                        TopologyNode(
                            id = deviceNodeId,
                            label = device.hostname,
                            type = determineDeviceNodeType(device),
                            ipAddress = device.ipAddress,
                            macAddress = device.macAddress
                        )
                    )

                    // Connect device to subnet
                    edges.add(
                        TopologyEdge(
                            fromNodeId = deviceNodeId,
                            toNodeId = subnetNodeId,
                            type = EdgeType.SUBNET
                        )
                    )
                }
            }

            // Apply force-directed layout for positioning
            applyForceDirectedLayout(nodes)

            Timber.d("Built topology with ${nodes.size} nodes and ${edges.size} edges")
            NetworkTopology(nodes = nodes, edges = edges)
        } catch (e: Exception) {
            Timber.e(e, "Failed to build network topology")
            NetworkTopology(nodes = emptyList(), edges = emptyList())
        }
    }

    /**
     * Determines the node type for a device based on its characteristics.
     */
    private fun determineDeviceNodeType(device: NetworkDevice): NodeType {
        // Heuristic: Check hostname patterns
        val hostname = device.hostname?.lowercase() ?: ""
        return when {
            hostname.contains("router") || hostname.contains("gateway") -> NodeType.ROUTER
            hostname.contains("switch") -> NodeType.SWITCH
            else -> NodeType.DEVICE
        }
    }

    /**
     * Applies a simple force-directed layout algorithm to position nodes.
     * This is a simplified version for visualization.
     */
    private fun applyForceDirectedLayout(nodes: MutableList<TopologyNode>) {
        if (nodes.isEmpty()) return

        // Simple circular layout for now
        // In a full implementation, this would use force-directed graph layout
        val centerX = 400f
        val centerY = 400f
        val radius = 200f

        nodes.forEachIndexed { index, node ->
            val angle = (2 * Math.PI * index) / nodes.size
            val x = centerX + radius * Math.cos(angle).toFloat()
            val y = centerY + radius * Math.sin(angle).toFloat()
            
            // Update node position
            nodes[index] = node.copy(x = x, y = y)
        }
    }
}
