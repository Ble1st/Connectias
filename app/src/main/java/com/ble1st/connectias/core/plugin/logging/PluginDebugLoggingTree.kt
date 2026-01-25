package com.ble1st.connectias.core.plugin.logging

import android.util.Log
import timber.log.Timber

/**
 * Timber Tree that forwards *plugin-attributed* logs to the main process via IPluginLogBridge.
 *
 * IMPORTANT:
 * - Only forwards when [PluginExecutionContext.currentPluginId] is set.
 * - This keeps noise low and focuses on plugin debugging.
 */
internal class PluginDebugLoggingTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val pluginId = PluginExecutionContext.currentPluginId() ?: return
        val bridge = PluginLogBridgeHolder.bridge ?: return

        try {
            bridge.log(
                pluginId,
                priority,
                tag,
                message,
                Thread.currentThread().name,
                t?.stackTraceToString(),
                System.currentTimeMillis()
            )
        } catch (_: Exception) {
            // Best-effort: avoid crashing sandbox due to logging bridge issues.
            Log.w("PluginDebugLoggingTree", "Failed to forward plugin log")
        }
    }
}

