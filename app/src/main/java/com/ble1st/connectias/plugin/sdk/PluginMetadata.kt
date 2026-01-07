package com.ble1st.connectias.plugin.sdk

data class PluginMetadata(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val author: String,
    val minApiLevel: Int,
    val maxApiLevel: Int,
    val minAppVersion: String,
    val nativeLibraries: List<String>,
    val fragmentClassName: String?,
    val description: String,
    val permissions: List<String>,
    val category: PluginCategory,
    val dependencies: List<String>
)

enum class PluginCategory {
    NETWORK,
    SECURITY,
    UTILITY,
    MEDIA,
    COMMUNICATION,
    DEVELOPMENT
}
