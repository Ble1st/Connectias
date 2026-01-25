package com.ble1st.connectias.plugin.sdk

import com.ble1st.connectias.plugin.ui.UIStateParcel
import com.ble1st.connectias.plugin.ui.UserActionParcel

/**
 * Base interface that all Connectias plugins must implement.
 *
 * IMPORTANT: Method order must match the plugin SDK version to ensure binary compatibility.
 * Plugins are compiled against the SDK version, so the interface signature must match exactly.
 *
 * Three-Process UI Architecture:
 * - onRenderUI(): Called to generate UI state for rendering in UI Process
 * - onUserAction(): Called when user interacts with plugin UI
 * - onUILifecycle(): Called when plugin UI lifecycle changes
 */
interface IPlugin {

    /**
     * Get plugin metadata.
     * Must be first method to match SDK interface order.
     */
    fun getMetadata(): PluginMetadata

    /**
     * Called when the plugin is loaded.
     * @return true if successful, false otherwise
     */
    fun onLoad(context: PluginContext): Boolean

    /**
     * Called when the plugin is enabled.
     * @return true if successful, false otherwise
     */
    fun onEnable(): Boolean

    /**
     * Called when the plugin is disabled.
     * @return true if successful, false otherwise
     */
    fun onDisable(): Boolean

    /**
     * Called when the plugin is unloaded.
     * @return true if successful, false otherwise
     */
    fun onUnload(): Boolean

    /**
     * Lifecycle: App was moved to background.
     * Default implementation does nothing - plugins can override if needed.
     */
    fun onPause() {}

    /**
     * Lifecycle: App is active again.
     * Default implementation does nothing - plugins can override if needed.
     */
    fun onResume() {}

    /**
     * THREE-PROCESS UI: Renders UI state for the given screen.
     *
     * This method is called when the UI Process needs to render or update
     * the plugin's UI. The plugin should return a UIStateParcel object that
     * describes the current UI state.
     *
     * The UI state is sent from Sandbox Process to UI Process via IPC,
     * where it's rendered using Jetpack Compose.
     *
     * @param screenId The screen identifier to render
     * @return UIStateParcel describing the UI, or null if no UI for this screen
     */
    fun onRenderUI(screenId: String): UIStateParcel? {
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
    fun onUserAction(action: UserActionParcel) {
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
     * @param event The lifecycle event (onCreate, onResume, onPause, onDestroy)
     */
    fun onUILifecycle(event: String) {
        // Default: Do nothing
    }
}

