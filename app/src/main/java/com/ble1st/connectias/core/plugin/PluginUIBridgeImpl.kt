// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin

import android.os.Bundle
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.ui.IPluginUIBridge
import com.ble1st.connectias.plugin.ui.UserActionParcel
import timber.log.Timber

/**
 * Implementation of IPluginUIBridge in Sandbox Process.
 *
 * Three-Process Architecture:
 * - UI Process: Captures user input, sends events to sandbox
 * - Sandbox Process: Processes events, updates state
 * - Main Process: Orchestrates lifecycle
 *
 * This bridge receives user actions from the UI Process and dispatches them
 * to the appropriate plugins in the sandbox.
 *
 * @param pluginProvider Function that provides IPlugin instance for a given pluginId
 *                       This allows lazy access to plugins that are loaded after initialization
 */
class PluginUIBridgeImpl(
    private val pluginProvider: (String) -> IPlugin?, // Function to get plugin instance at runtime
    private val uiController: PluginUIControllerImpl? = null // Reference to UI controller for resending cached state
) : IPluginUIBridge.Stub() {

    // Lifecycle event listeners (plugin ID -> listener)
    private val lifecycleListeners = mutableMapOf<String, PluginLifecycleListener>()

    /**
     * Interface for plugin lifecycle events.
     */
    interface PluginLifecycleListener {
        fun onUILifecycle(event: String)
    }

    /**
     * Gets IPlugin instance for the given pluginId.
     * Uses the pluginProvider function to get the plugin at runtime.
     *
     * @param pluginId Plugin identifier
     * @return IPlugin instance or null if not found
     */
    private fun getPluginInstance(pluginId: String): IPlugin? {
        return try {
            val plugin = pluginProvider(pluginId)
            if (plugin == null) {
                Timber.w("[SANDBOX] Plugin not found in registry: $pluginId (pluginProvider returned null)")
            }
            plugin
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Error retrieving plugin instance for $pluginId: ${e.message}")
            null
        }
    }

    /**
     * Registers a lifecycle listener for a plugin.
     *
     * @param pluginId Plugin identifier
     * @param listener Lifecycle listener
     */
    fun registerLifecycleListener(pluginId: String, listener: PluginLifecycleListener) {
        lifecycleListeners[pluginId] = listener
        Timber.d("[SANDBOX] Registered lifecycle listener for $pluginId")
    }

    /**
     * Unregisters a lifecycle listener.
     *
     * @param pluginId Plugin identifier
     */
    fun unregisterLifecycleListener(pluginId: String) {
        lifecycleListeners.remove(pluginId)
        Timber.d("[SANDBOX] Unregistered lifecycle listener for $pluginId")
    }

    override fun onButtonClick(pluginId: String, buttonId: String, extras: Bundle) {
        Timber.d("[SANDBOX] Button clicked: $pluginId -> $buttonId")

        val plugin = getPluginInstance(pluginId)
        if (plugin != null) {
            try {
                val action = UserActionParcel().apply {
                    actionType = "click"
                    targetId = buttonId
                    data = extras
                    timestamp = System.currentTimeMillis()
                }
                plugin.onUserAction(action)
                Timber.v("[SANDBOX] Dispatched button click to plugin: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching button click to plugin: $pluginId")
            }
        } else {
            Timber.w("[SANDBOX] Plugin not found or not IPlugin: $pluginId")
        }
    }

    override fun onTextChanged(pluginId: String, fieldId: String, value: String) {
        Timber.d("[SANDBOX] Text changed: $pluginId -> $fieldId = $value")

        val plugin = getPluginInstance(pluginId)
        if (plugin != null) {
            try {
                val action = UserActionParcel().apply {
                    actionType = "text_changed"
                    targetId = fieldId
                    data = Bundle().apply { putString("value", value) }
                    timestamp = System.currentTimeMillis()
                }
                plugin.onUserAction(action)
                Timber.v("[SANDBOX] Dispatched text change to plugin: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching text change to plugin: $pluginId")
            }
        } else {
            Timber.w("[SANDBOX] Plugin not found or not IPlugin: $pluginId")
        }
    }

    override fun onItemSelected(
        pluginId: String,
        listId: String,
        position: Int,
        itemData: Bundle
    ) {
        Timber.d("[SANDBOX] Item selected: $pluginId -> $listId[$position]")

        val plugin = getPluginInstance(pluginId)
        if (plugin != null) {
            try {
                val action = UserActionParcel().apply {
                    actionType = "item_selected"
                    targetId = listId
                    data = Bundle().apply {
                        putInt("position", position)
                        putBundle("itemData", itemData)
                    }
                    timestamp = System.currentTimeMillis()
                }
                plugin.onUserAction(action)
                Timber.v("[SANDBOX] Dispatched item selection to plugin: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching item selection to plugin: $pluginId")
            }
        } else {
            Timber.w("[SANDBOX] Plugin not found or not IPlugin: $pluginId")
        }
    }

    override fun onLifecycleEvent(pluginId: String, event: String) {
        Timber.d("[SANDBOX] Lifecycle event: $pluginId -> $event")

        // CRITICAL: When fragment is recreated (onCreate), resend cached UI state
        // This ensures the UI is displayed even if the plugin doesn't send state again
        if (event == "onCreate") {
            if (uiController == null) {
                Timber.w("[SANDBOX] UI Controller is null - cannot resend cached state for plugin: $pluginId")
            } else {
                Timber.d("[SANDBOX] Resending cached UI state for plugin: $pluginId (onCreate event)")
                uiController.resendCachedState(pluginId)
            }
        }

        // Dispatch to registered lifecycle listener
        lifecycleListeners[pluginId]?.let { listener ->
            try {
                listener.onUILifecycle(event)
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching lifecycle event to listener: $pluginId")
            }
        } ?: run {
            Timber.w("[SANDBOX] No lifecycle listener registered for plugin: $pluginId")
        }

        // Dispatch to plugin's onUILifecycle method
        val plugin = getPluginInstance(pluginId)
        if (plugin != null) {
            try {
                plugin.onUILifecycle(event)
                Timber.v("[SANDBOX] Dispatched lifecycle event to plugin: $pluginId -> $event")

                // Cleanup on destroy
                if (event == "onDestroy") {
                    lifecycleListeners.remove(pluginId)
                }
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching lifecycle event to plugin: $pluginId")
            }
        } else {
            Timber.w("[SANDBOX] Plugin not found or not IPlugin in registry: $pluginId")
        }
    }

    override fun onUserAction(pluginId: String, action: UserActionParcel) {
        Timber.d("[SANDBOX] User action: $pluginId -> ${action.actionType} on ${action.targetId}")

        val plugin = getPluginInstance(pluginId)
        if (plugin != null) {
            try {
                plugin.onUserAction(action)
                Timber.v("[SANDBOX] Dispatched user action to plugin: $pluginId (${action.actionType})")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching user action to plugin: $pluginId")
            }
        } else {
            Timber.w("[SANDBOX] Plugin not found or not IPlugin: $pluginId")
        }
    }

    override fun onPermissionResult(
        pluginId: String,
        permission: String,
        granted: Boolean
    ) {
        Timber.d("[SANDBOX] Permission result: $pluginId -> $permission = $granted")

        val plugin = getPluginInstance(pluginId)
        if (plugin != null) {
            try {
                // Create user action for permission result
                val action = UserActionParcel().apply {
                    actionType = "permission_result"
                    targetId = permission
                    data = Bundle().apply { putBoolean("granted", granted) }
                    timestamp = System.currentTimeMillis()
                }
                plugin.onUserAction(action)
                Timber.v("[SANDBOX] Dispatched permission result to plugin: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching permission result to plugin: $pluginId")
            }
        } else {
            Timber.w("[SANDBOX] Plugin not found or not IPlugin: $pluginId")
        }
    }

    /**
     * Gets the number of registered plugins.
     * Note: This is an estimate since we use a provider function.
     */
    fun getPluginCount(): Int {
        // Cannot determine count from provider function
        return -1
    }

    /**
     * Checks if a plugin is registered.
     */
    fun hasPlugin(pluginId: String): Boolean {
        return getPluginInstance(pluginId) != null
    }

    /**
     * Clears all lifecycle listeners.
     */
    fun clearLifecycleListeners() {
        lifecycleListeners.clear()
        Timber.d("[SANDBOX] Cleared all lifecycle listeners")
    }
}
