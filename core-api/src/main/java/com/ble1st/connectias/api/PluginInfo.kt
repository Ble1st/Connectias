package com.ble1st.connectias.api

import kotlinx.serialization.Serializable

@Serializable
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val minCoreVersion: String,
    val maxCoreVersion: String?,
    val permissions: List<PluginPermission>,
    val entryPoint: String, // Fully qualified class name
    val dependencies: List<String>? = null // Plugin-IDs von benötigten Plugins
)

enum class PluginPermission {
    NETWORK,
    STORAGE,
    SYSTEM_INFO,
    LOCATION
}
