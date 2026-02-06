package com.ble1st.connectias.core.plugin

/**
 * Plugin metadata - must match the SDK version
 * This is a copy for the Core module to avoid dependency on plugin SDK
 */
data class PluginMetadata(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val author: String,
    val minApiLevel: Int = 33,
    val maxApiLevel: Int = 36,
    val minAppVersion: String = "1.0.0",
    val nativeLibraries: List<String> = emptyList(),
    val category: PluginCategory = PluginCategory.UTILITY,
    val description: String = "",
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val fragmentClassName: String? = null
)

enum class PluginCategory {
    SECURITY,
    NETWORK,
    PRIVACY,
    UTILITY,
    SYSTEM
}
