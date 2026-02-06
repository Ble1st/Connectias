@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.ble1st.connectias.analytics.model

import kotlinx.serialization.Serializable

/**
 * Lightweight analytics models for performance monitoring.
 *
 * Storage is append-only JSONL to avoid Room migrations for the MVP.
 */
@Serializable
data class PluginPerformanceSample(
    val timestamp: Long,
    val pluginId: String,
    val memoryUsedMB: Int,
    val memoryPeakMB: Int,
    val cpuPercent: Float,
    val activeThreads: Int,
    val diskUsageMB: Long,
    val netBytesIn: Long,
    val netBytesOut: Long
)

@Serializable
data class PluginUiActionEvent(
    val timestamp: Long,
    val pluginId: String,
    val actionType: String,
    val targetId: String
)

@Serializable
data class SecurityEventCounterSample(
    val timestamp: Long,
    val pluginId: String,
    val eventType: String,
    val severity: String
)

/**
 * Plugin session tracking event for foreground start/end.
 * Used to calculate usage duration and session count.
 */
@Serializable
data class PluginSessionEvent(
    val timestamp: Long,
    val pluginId: String,
    val eventType: SessionEventType
)

@Serializable
enum class SessionEventType {
    FOREGROUND_START,
    FOREGROUND_END
}

/**
 * Plugin lifecycle event for tracking enable/disable/install/uninstall.
 */
@Serializable
data class PluginLifecycleEvent(
    val timestamp: Long,
    val pluginId: String,
    val eventType: LifecycleEventType,
    val pluginName: String = "",
    val pluginVersion: String = ""
)

@Serializable
enum class LifecycleEventType {
    INSTALLED,
    UNINSTALLED,
    ENABLED,
    DISABLED
}

