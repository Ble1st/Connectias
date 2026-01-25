package com.ble1st.connectias.core.plugin.declarative

import com.ble1st.connectias.core.plugin.SandboxPluginContext
import org.json.JSONObject
import timber.log.Timber

/**
 * Minimal state persistence using the FileSystemBridge via SandboxPluginContext.
 *
 * For declarative plugins, persistence is host-managed and can safely use sandbox-local storage
 * to avoid cross-process identity ambiguity (multiple plugins share one isolated UID).
 */
class DeclarativeStatePersistence(
    private val pluginId: String,
    private val context: SandboxPluginContext
) {
    private val statePath = "declarative/state.json"

    fun loadInto(state: MutableMap<String, Any?>) {
        try {
            val input = context.openLocalFile(statePath) ?: context.openFile(statePath) ?: return
            val text = input.bufferedReader().use { it.readText() }
            input.close()

            val json = JSONObject(text)
            json.keys().forEach { k ->
                state[k] = json.opt(k)
            }
        } catch (e: Exception) {
            Timber.w(e, "[SANDBOX][DECL:$pluginId] Failed to load persisted state")
        }
    }

    fun persist(state: Map<String, Any?>) {
        try {
            val out = context.openLocalFileForWrite(statePath) ?: context.openFileForWrite(statePath) ?: return
            val json = JSONObject()
            state.forEach { (k, v) ->
                if (!k.startsWith("_")) {
                    json.put(k, v)
                }
            }
            out.bufferedWriter().use { it.write(json.toString()) }
            out.close()
        } catch (e: Exception) {
            Timber.w(e, "[SANDBOX][DECL:$pluginId] Failed to persist state")
        }
    }
}

