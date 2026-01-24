// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin

import android.os.Bundle
import com.ble1st.connectias.core.plugin.ui.UIStateDiffer
import com.ble1st.connectias.plugin.ui.IPluginUIController
import com.ble1st.connectias.plugin.ui.UIEventParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import timber.log.Timber

/**
 * Implementation of IPluginUIController in Sandbox Process.
 *
 * Three-Process Architecture:
 * - Sandbox Process: Runs plugin business logic, sends UI state updates
 * - UI Process: Renders UI based on state from sandbox
 * - Main Process: Orchestrates lifecycle
 *
 * This controller is used by plugins to send UI updates to the UI Process.
 * It runs in the isolated sandbox process and forwards all calls to the
 * UI Process via AIDL IPC.
 */
class PluginUIControllerImpl : IPluginUIController.Stub() {

    // Reference to the actual UI controller in UI Process
    private var remoteUIController: IPluginUIController? = null

    // State cache for debugging and recovery
    private val stateCache = mutableMapOf<String, UIStateParcel>()

    /**
     * Sets the remote UI controller (called by PluginSandboxService).
     *
     * @param controller IPluginUIController from UI Process
     */
    fun setRemoteController(controller: IPluginUIController) {
        this.remoteUIController = controller
        Timber.i("[SANDBOX] Remote UI controller connected")
    }

    override fun updateUIState(pluginId: String, state: UIStateParcel) {
        Timber.d("[SANDBOX] Update UI state: $pluginId -> ${state.screenId}")

        // Performance Optimization: State Diffing
        val previousState = stateCache[pluginId]
        val diff = UIStateDiffer.diff(previousState, state)

        // Log diff statistics for monitoring
        UIStateDiffer.logDiffStats(pluginId, diff)

        // Only send update if state actually changed
        if (!UIStateDiffer.shouldUpdate(diff)) {
            Timber.v("[SANDBOX] State unchanged for $pluginId - skipping IPC")
            return
        }

        // Cache new state for next diff
        stateCache[pluginId] = state

        // Create optimized update (currently returns full state, future: partial updates)
        val optimizedState = UIStateDiffer.createOptimizedUpdate(state, diff)

        // Forward to UI Process
        try {
            remoteUIController?.updateUIState(pluginId, optimizedState)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - state update dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to update UI state for $pluginId")
        }
    }

    override fun showDialog(
        pluginId: String,
        title: String,
        message: String,
        dialogType: Int
    ) {
        Timber.d("[SANDBOX] Show dialog: $pluginId -> $title")

        try {
            remoteUIController?.showDialog(pluginId, title, message, dialogType)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - dialog request dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to show dialog for $pluginId")
        }
    }

    override fun showToast(pluginId: String, message: String, duration: Int) {
        Timber.d("[SANDBOX] Show toast: $pluginId -> $message")

        try {
            remoteUIController?.showToast(pluginId, message, duration)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - toast request dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to show toast for $pluginId")
        }
    }

    override fun navigateToScreen(pluginId: String, screenId: String, args: Bundle) {
        Timber.d("[SANDBOX] Navigate: $pluginId -> $screenId")

        try {
            remoteUIController?.navigateToScreen(pluginId, screenId, args)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - navigation request dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to navigate for $pluginId")
        }
    }

    override fun navigateBack(pluginId: String) {
        Timber.d("[SANDBOX] Navigate back: $pluginId")

        try {
            remoteUIController?.navigateBack(pluginId)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - back navigation dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to navigate back for $pluginId")
        }
    }

    override fun setLoading(pluginId: String, loading: Boolean, message: String?) {
        Timber.d("[SANDBOX] Set loading: $pluginId -> $loading")

        try {
            remoteUIController?.setLoading(pluginId, loading, message)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - loading state dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to set loading for $pluginId")
        }
    }

    override fun sendUIEvent(pluginId: String, event: UIEventParcel) {
        Timber.d("[SANDBOX] Send UI event: $pluginId -> ${event.eventType}")

        try {
            remoteUIController?.sendUIEvent(pluginId, event)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - UI event dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to send UI event for $pluginId")
        }
    }

    /**
     * Gets cached state for a plugin (for recovery/debugging).
     *
     * @param pluginId Plugin identifier
     * @return Cached UI state or null
     */
    fun getCachedState(pluginId: String): UIStateParcel? {
        return stateCache[pluginId]
    }

    /**
     * Clears cached state for a plugin.
     *
     * @param pluginId Plugin identifier
     */
    fun clearCachedState(pluginId: String) {
        stateCache.remove(pluginId)
        Timber.d("[SANDBOX] Cleared cached state for $pluginId")
    }

    /**
     * Disconnects remote controller.
     */
    fun disconnect() {
        remoteUIController = null
        stateCache.clear()
        Timber.i("[SANDBOX] UI controller disconnected")
    }

    /**
     * Checks if remote controller is connected.
     */
    fun isConnected(): Boolean = remoteUIController != null

    /**
     * Resends cached UI state for a plugin.
     * This is useful when a fragment is recreated after being destroyed.
     * Forces update even if state hasn't changed (bypasses state diffing).
     *
     * @param pluginId Plugin identifier
     */
    fun resendCachedState(pluginId: String) {
        val cachedState = stateCache[pluginId]
        if (cachedState != null) {
            Timber.d("[SANDBOX] Resending cached UI state for plugin: $pluginId -> ${cachedState.screenId}")
            try {
                // Force send cached state (bypass state diffing)
                remoteUIController?.updateUIState(pluginId, cachedState)
                    ?: Timber.w("[SANDBOX] Remote UI controller not connected - cached state resend dropped")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to resend cached UI state for $pluginId")
            }
        } else {
            Timber.d("[SANDBOX] No cached state found for plugin: $pluginId - cannot resend")
        }
    }
}
