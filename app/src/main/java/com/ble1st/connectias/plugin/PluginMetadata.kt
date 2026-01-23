package com.ble1st.connectias.plugin

/**
 * Plugin metadata containing all information about a plugin
 */
data class PluginMetadata(
    val pluginId: String,                    // e.g., "network_tools"
    val pluginName: String,                  // e.g., "Network Tools"
    val version: String,                     // e.g., "1.2.3"
    val author: String,                      // e.g., "Ble1st"
    val minApiLevel: Int = 33,
    val maxApiLevel: Int = 36,
    val minAppVersion: String = "1.0.0",
    val nativeLibraries: List<String> = emptyList(),  // e.g., ["libconnectias_port_scanner.so"]
    val category: PluginCategory = PluginCategory.UTILITY,
    val description: String = "",
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),    // Other plugin IDs
    val fragmentClassName: String? = null            // Fully qualified Fragment class name
)

/**
 * Categories for organizing plugins
 */
enum class PluginCategory {
    SECURITY,
    NETWORK,
    PRIVACY,
    UTILITY,
    SYSTEM
}
