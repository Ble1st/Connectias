// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.os.Bundle
import com.ble1st.connectias.plugin.ui.IPluginUIController
import com.ble1st.connectias.plugin.ui.UIEventParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import timber.log.Timber

/**
 * Implementation of IPluginUIController in UI Process.
 *
 * Three-Process Architecture:
 * - Sandbox Process: Sends UI state updates via PluginUIControllerImpl
 * - UI Process: This class receives updates and forwards them to PluginUIFragment
 * - Main Process: Orchestrates lifecycle
 *
 * This controller receives UI state updates from the Sandbox Process and
 * forwards them to the appropriate PluginUIFragment instances for rendering.
 */
class PluginUIControllerUIProcess(
    private val fragmentRegistry: MutableMap<String, PluginUIFragment>
) : IPluginUIController.Stub() {

    // Cache UI state for plugins that haven't created their fragment yet
    private val pendingUIStates = mutableMapOf<String, UIStateParcel>()
    
    // Cache last UI state for each plugin (for re-initialization after destroy)
    private val lastUIStates = mutableMapOf<String, UIStateParcel>()

    override fun updateUIState(pluginId: String, state: UIStateParcel) {
        Timber.d("[UI_PROCESS] Update UI state: $pluginId -> ${state.screenId}")

        try {
            // Always cache the last state (for re-initialization after destroy)
            lastUIStates[pluginId] = state
            
            val fragment = fragmentRegistry[pluginId]
            if (fragment != null) {
                fragment.updateState(state)
                // Clear pending state if fragment now exists
                pendingUIStates.remove(pluginId)
                Timber.v("[UI_PROCESS] UI state updated for plugin: $pluginId")
            } else {
                // Fragment not created yet - cache the state
                pendingUIStates[pluginId] = state
                Timber.d("[UI_PROCESS] Fragment not found for plugin: $pluginId - state cached (will apply when fragment is created)")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to update UI state for plugin: $pluginId")
        }
    }

    /**
     * Applies any pending UI state when a fragment is created.
     * Called by PluginUIHostImpl after fragment creation.
     * 
     * If no pending state exists, tries to use the last known state (for re-initialization).
     */
    fun applyPendingState(pluginId: String, fragment: PluginUIFragment) {
        // First try pending state (state that arrived before fragment creation)
        pendingUIStates.remove(pluginId)?.let { pendingState ->
            Timber.d("[UI_PROCESS] Applying pending UI state for plugin: $pluginId -> ${pendingState.screenId}")
            fragment.updateState(pendingState)
            return
        }
        
        // If no pending state, try last known state (for re-initialization after destroy)
        lastUIStates[pluginId]?.let { lastState ->
            Timber.d("[UI_PROCESS] Applying last known UI state for plugin: $pluginId -> ${lastState.screenId} (re-initialization)")
            fragment.updateState(lastState)
        } ?: run {
            Timber.w("[UI_PROCESS] No pending or last state found for plugin: $pluginId - fragment will show loading state")
        }
    }

    override fun showDialog(
        pluginId: String,
        title: String,
        message: String,
        dialogType: Int
    ) {
        Timber.d("[UI_PROCESS] Show dialog: $pluginId -> $title")

        try {
            val fragment = fragmentRegistry[pluginId]
            if (fragment != null) {
                fragment.showDialog(title, message, dialogType)
                Timber.v("[UI_PROCESS] Dialog displayed: $title")
            } else {
                Timber.w("[UI_PROCESS] Fragment not found for plugin: $pluginId - dialog request dropped")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to show dialog for plugin: $pluginId")
        }
    }

    override fun showToast(pluginId: String, message: String, duration: Int) {
        Timber.d("[UI_PROCESS] Show toast: $pluginId -> $message")

        try {
            val fragment = fragmentRegistry[pluginId]
            if (fragment != null) {
                fragment.showToast(message, duration)
                Timber.v("[UI_PROCESS] Toast displayed: $message")
            } else {
                Timber.w("[UI_PROCESS] Fragment not found for plugin: $pluginId - toast request dropped")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to show toast for plugin: $pluginId")
        }
    }

    override fun navigateToScreen(pluginId: String, screenId: String, args: Bundle) {
        Timber.d("[UI_PROCESS] Navigate: $pluginId -> $screenId")

        try {
            val fragment = fragmentRegistry[pluginId]
            if (fragment != null) {
                fragment.navigateToScreen(screenId, args)
                Timber.v("[UI_PROCESS] Navigation requested: $screenId")
                // Note: Actual navigation happens when sandbox sends new UIStateParcel
            } else {
                Timber.w("[UI_PROCESS] Fragment not found for plugin: $pluginId - navigation request dropped")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to navigate for plugin: $pluginId")
        }
    }

    override fun navigateBack(pluginId: String) {
        Timber.d("[UI_PROCESS] Navigate back: $pluginId")

        try {
            val fragment = fragmentRegistry[pluginId]
            if (fragment != null) {
                fragment.navigateBack()
                Timber.v("[UI_PROCESS] Back navigation requested")
                // Note: Actual navigation happens when sandbox sends new UIStateParcel
            } else {
                Timber.w("[UI_PROCESS] Fragment not found for plugin: $pluginId - back navigation dropped")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to navigate back for plugin: $pluginId")
        }
    }

    override fun setLoading(pluginId: String, loading: Boolean, message: String?) {
        Timber.d("[UI_PROCESS] Set loading: $pluginId -> $loading")

        try {
            val fragment = fragmentRegistry[pluginId]
            if (fragment != null) {
                fragment.setLoading(loading, message)
                Timber.v("[UI_PROCESS] Loading state set: $loading")
            } else {
                Timber.w("[UI_PROCESS] Fragment not found for plugin: $pluginId - loading state dropped")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to set loading for plugin: $pluginId")
        }
    }

    override fun sendUIEvent(pluginId: String, event: UIEventParcel) {
        Timber.d("[UI_PROCESS] Send UI event: $pluginId -> ${event.eventType}")

        try {
            val fragment = fragmentRegistry[pluginId]
            if (fragment != null) {
                fragment.handleUIEvent(event)
                Timber.v("[UI_PROCESS] UI event handled: ${event.eventType}")
            } else {
                Timber.w("[UI_PROCESS] Fragment not found for plugin: $pluginId - UI event dropped")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to send UI event for plugin: $pluginId")
        }
    }
}
