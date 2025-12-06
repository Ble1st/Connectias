package com.ble1st.connectias.feature.network.topology.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.network.topology.models.NetworkTopology
import com.ble1st.connectias.feature.network.topology.models.TopologyNode
import com.ble1st.connectias.feature.network.topology.models.TopologyEdge
import com.ble1st.connectias.feature.network.topology.models.NodeType

/**
 * Interactive topology graph visualization using Compose Canvas.
 */
@Composable
fun TopologyGraph(
    topology: NetworkTopology,
    modifier: Modifier = Modifier,
    onNodeSelected: (TopologyNode) -> Unit = {}
) {
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomLevel by remember { mutableStateOf(1f) }

    Box(modifier = modifier) {
        var canvasSize by remember { mutableStateOf<Size?>(null) }
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput("transform") {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Update zoom level with clamping
                        zoomLevel = (zoomLevel * zoom).coerceIn(0.5f, 3f)
                        // Update pan offset
                        panOffset += pan
                    }
                }
                .pointerInput("drag") {
                    detectDragGestures { change, dragAmount ->
                        panOffset += dragAmount
                    }
                }
                .pointerInput("tap", canvasSize) {
                    if (canvasSize != null) {
                        detectTapGestures { tapOffset ->
                            val centerX = canvasSize!!.width / 2
                            val centerY = canvasSize!!.height / 2
                            
                            // Find tapped node
                            val node = findNodeAtPosition(
                                topology.nodes,
                                tapOffset,
                                panOffset,
                                zoomLevel,
                                centerX,
                                centerY
                            )
                            node?.let {
                                selectedNodeId = it.id
                                onNodeSelected(it)
                            }
                        }
                    }
                }
        ) {
            // Store canvas size for tap gesture handling
            canvasSize = size
            
            // Apply transformations manually
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // Draw edges first (behind nodes)
            topology.edges.forEach { edge ->
                drawEdge(edge, topology, panOffset, zoomLevel, centerX, centerY)
            }

            // Draw nodes
            topology.nodes.forEach { node ->
                val isSelected = node.id == selectedNodeId
                drawNode(node, isSelected, panOffset, zoomLevel, centerX, centerY)
            }
        }
    }
}

private fun DrawScope.drawEdge(
    edge: TopologyEdge,
    topology: NetworkTopology,
    panOffset: Offset,
    zoomLevel: Float,
    centerX: Float,
    centerY: Float
) {
    val fromNode = topology.getNodeById(edge.fromNodeId)
    val toNode = topology.getNodeById(edge.toNodeId)

    if (fromNode == null || toNode == null) return

    // Apply transformations: scale around center, then translate
    val fromX = (fromNode.x - centerX) * zoomLevel + centerX + panOffset.x
    val fromY = (fromNode.y - centerY) * zoomLevel + centerY + panOffset.y
    val toX = (toNode.x - centerX) * zoomLevel + centerX + panOffset.x
    val toY = (toNode.y - centerY) * zoomLevel + centerY + panOffset.y

    val start = Offset(fromX, fromY)
    val end = Offset(toX, toY)

    val color = when (edge.type) {
        com.ble1st.connectias.feature.network.topology.models.EdgeType.DIRECT -> Color(0xFF4CAF50)
        com.ble1st.connectias.feature.network.topology.models.EdgeType.ROUTED -> Color(0xFF2196F3)
        com.ble1st.connectias.feature.network.topology.models.EdgeType.SUBNET -> Color(0xFF9E9E9E)
    }

    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 2f * zoomLevel
    )
}

private fun DrawScope.drawNode(
    node: TopologyNode,
    isSelected: Boolean,
    panOffset: Offset,
    zoomLevel: Float,
    centerX: Float,
    centerY: Float
) {
    // Apply transformations: scale around center, then translate
    val x = (node.x - centerX) * zoomLevel + centerX + panOffset.x
    val y = (node.y - centerY) * zoomLevel + centerY + panOffset.y
    val position = Offset(x, y)
    val radius = 20f * zoomLevel

    val color = when (node.type) {
        NodeType.GATEWAY -> Color(0xFFF44336)
        NodeType.ROUTER -> Color(0xFFFF9800)
        NodeType.SWITCH -> Color(0xFF9C27B0)
        NodeType.DEVICE -> Color(0xFF2196F3)
        NodeType.SUBNET -> Color(0xFF9E9E9E)
    }

    // Draw node circle
    drawCircle(
        color = if (isSelected) color.copy(alpha = 0.8f) else color,
        radius = if (isSelected) radius * 1.2f else radius,
        center = position
    )

    // Draw border if selected
    if (isSelected) {
        drawCircle(
            color = Color.White,
            radius = radius * 1.3f,
            center = position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
    }
}

private fun findNodeAtPosition(
    nodes: List<TopologyNode>,
    position: Offset,
    panOffset: Offset,
    zoomLevel: Float,
    centerX: Float,
    centerY: Float
): TopologyNode? {
    // Base hit radius (matches base node radius of 20f)
    // Use world-space hit radius for distance comparison to match visual node size at all zoom levels
    val baseHitRadius = 20f
    
    // Reverse transformations: translate first, then unscale
    val adjustedX = (position.x - panOffset.x - centerX) / zoomLevel + centerX
    val adjustedY = (position.y - panOffset.y - centerY) / zoomLevel + centerY
    val adjustedPosition = Offset(adjustedX, adjustedY)

    return nodes.find { node ->
        val distance = kotlin.math.sqrt(
            (adjustedPosition.x - node.x) * (adjustedPosition.x - node.x) +
            (adjustedPosition.y - node.y) * (adjustedPosition.y - node.y)
        )
        // Compare with baseHitRadius in world space (not scaled) since position is already in world space
        distance <= baseHitRadius
    }
}
