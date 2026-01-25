package com.ble1st.connectias.core.plugin.logging

/**
 * Thread-local plugin execution context used to attribute logs to a plugin.
 *
 * This allows capturing logs emitted by plugin code without requiring plugins to
 * pass a pluginId explicitly to a logging API.
 */
object PluginExecutionContext {
    private val current = ThreadLocal<String?>()

    fun currentPluginId(): String? = current.get()

    fun <T> withPlugin(pluginId: String, block: () -> T): T {
        val prev = current.get()
        current.set(pluginId)
        return try {
            block()
        } finally {
            current.set(prev)
        }
    }
}

