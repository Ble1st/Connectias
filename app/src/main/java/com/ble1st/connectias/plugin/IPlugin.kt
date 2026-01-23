package com.ble1st.connectias.plugin

import com.ble1st.connectias.plugin.ui.UIStateData
import com.ble1st.connectias.plugin.ui.UserAction

/**
 * Base interface for all Connectias plugins.
 * Plugins MUST implement this interface.
 *
 * Three-Process UI Architecture:
 * - onRenderUI(): Called to generate UI state for rendering in UI Process
 * - onUserAction(): Called when user interacts with plugin UI
 * - onUILifecycle(): Called when plugin UI lifecycle changes
 */
interface IPlugin {

    /**
     * Plugin metadata
     */
    fun getMetadata(): PluginMetadata

    /**
     * Called when plugin is loaded
     */
    fun onLoad(context: PluginContext): Boolean

    /**
     * Called when plugin is enabled
     */
    fun onEnable(): Boolean

    /**
     * Called when plugin is disabled
     */
    fun onDisable(): Boolean

    /**
     * Called before plugin is unloaded
     */
    fun onUnload(): Boolean

    /**
     * Lifecycle: App was moved to background
     */
    fun onPause() {}

    /**
     * Lifecycle: App is active again
     */
    fun onResume() {}

    /**
     * THREE-PROCESS UI: Renders UI state for the given screen.
     *
     * This method is called when the UI Process needs to render or update
     * the plugin's UI. The plugin should return a UIStateData object that
     * describes the current UI state.
     *
     * The UI state is sent from Sandbox Process to UI Process via IPC,
     * where it's rendered using Jetpack Compose.
     *
     * @param screenId The screen identifier to render
     * @return UIStateData describing the UI, or null if no UI for this screen
     */
    fun onRenderUI(screenId: String): UIStateData? {
        return null // Default: No UI
    }

    /**
     * THREE-PROCESS UI: Handles user actions from the UI.
     *
     * This method is called when the user interacts with the plugin's UI.
     * User actions are generated in the UI Process and sent to the Sandbox
     * Process via IPC.
     *
     * The plugin should handle the action (e.g., update internal state) and
     * can trigger a UI update by calling the UI controller.
     *
     * @param action The user action that occurred
     */
    fun onUserAction(action: UserAction) {
        // Default: Do nothing
    }

    /**
     * THREE-PROCESS UI: Handles UI lifecycle events.
     *
     * This method is called when the plugin's UI undergoes lifecycle changes
     * (onCreate, onResume, onPause, onDestroy).
     *
     * Lifecycle events are sent from UI Process to Sandbox Process via IPC.
     *
     * @param event The lifecycle event (see UILifecycleEvent constants)
     */
    fun onUILifecycle(event: String) {
        // Default: Do nothing
    }
}
