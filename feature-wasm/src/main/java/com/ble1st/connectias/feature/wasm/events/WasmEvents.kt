package com.ble1st.connectias.feature.wasm.events

import com.ble1st.connectias.core.eventbus.Event
import com.ble1st.connectias.feature.wasm.plugin.models.PluginMetadata

/**
 * Event emitted when a plugin is loaded.
 */
data class PluginLoadedEvent(
    val pluginId: String,
    val metadata: PluginMetadata
) : Event()

/**
 * Event emitted when a plugin command is executed.
 */
data class PluginExecutedEvent(
    val pluginId: String,
    val command: String,
    val result: String
) : Event()

/**
 * Event emitted when a plugin is unloaded.
 */
data class PluginUnloadedEvent(
    val pluginId: String
) : Event()

/**
 * Event emitted when a resource limit is exceeded.
 */
data class ResourceLimitExceededEvent(
    val pluginId: String,
    val resourceType: String, // "memory", "fuel", "time"
    val limit: Long
) : Event()

/**
 * Event emitted when a plugin encounters an error.
 */
data class PluginErrorEvent(
    val pluginId: String,
    val error: String
) : Event()

