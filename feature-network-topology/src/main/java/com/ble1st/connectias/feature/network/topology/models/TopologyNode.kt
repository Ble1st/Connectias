package com.ble1st.connectias.feature.network.topology.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a node in the network topology graph.
 * 
 * Note: This class uses @Parcelize for automatic Parcelable implementation.
 * Validation is performed in the companion object factory methods, not in the constructor,
 * to avoid exceptions during Parcel deserialization.
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
    companion object {
        /**
         * Factory method to create a TopologyNode with validation.
         * Validates that id and label are not blank before creating the node.
         * 
         * @param id Node identifier (must not be blank)
         * @param label Node display label (must not be blank)
         * @param type Node type
         * @param ipAddress Optional IP address
         * @param macAddress Optional MAC address
         * @param x X coordinate (default: 0f)
         * @param y Y coordinate (default: 0f)
         * @return TopologyNode instance
         * @throws IllegalArgumentException if id or label is blank
         */
        fun create(
            id: String,
            label: String,
            type: NodeType,
            ipAddress: String? = null,
            macAddress: String? = null,
            x: Float = 0f,
            y: Float = 0f
        ): TopologyNode {
            require(id.isNotBlank()) { "Node id cannot be blank" }
            require(label.isNotBlank()) { "Node label cannot be blank" }
            return TopologyNode(id, label, type, ipAddress, macAddress, x, y)
        }
        
        /**
         * Factory method to create a TopologyNode from Parcel data with validation.
         * This should be used when deserializing from Parcel to ensure data integrity.
         * 
         * Note: With @Parcelize, this is called automatically during deserialization.
         * However, validation is performed here to catch invalid parcel data.
         * 
         * @param id Node identifier from parcel
         * @param label Node display label from parcel
         * @param type Node type from parcel
         * @param ipAddress Optional IP address from parcel
         * @param macAddress Optional MAC address from parcel
         * @param x X coordinate from parcel
         * @param y Y coordinate from parcel
         * @return TopologyNode instance if valid, or throws IllegalArgumentException if invalid
         * @throws IllegalArgumentException if id or label is blank
         */
        fun fromParcel(
            id: String,
            label: String,
            type: NodeType,
            ipAddress: String? = null,
            macAddress: String? = null,
            x: Float = 0f,
            y: Float = 0f
        ): TopologyNode {
            require(id.isNotBlank()) { "Node id cannot be blank" }
            require(label.isNotBlank()) { "Node label cannot be blank" }
            return TopologyNode(id, label, type, ipAddress, macAddress, x, y)
        }
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
