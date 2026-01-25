package com.ble1st.connectias.plugin.sdk

import kotlinx.parcelize.Parcelize

@Parcelize
data class PluginMetadata(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val versionCode: Int = 1,
    val author: String,
    val minApiLevel: Int,
    val maxApiLevel: Int,
    val minAppVersion: String,
    val nativeLibraries: List<String>,
    val fragmentClassName: String?,
    val description: String,
    val permissions: List<String>,
    val category: PluginCategory,
    val dependencies: List<String>,
    val changelog: String = "",
    val releaseDate: Long = System.currentTimeMillis(),
    val isPrerelease: Boolean = false
) : android.os.Parcelable

enum class PluginCategory {
    NETWORK,
    SECURITY,
    UTILITY,
    MEDIA,
    COMMUNICATION,
    DEVELOPMENT
}

