package com.ble1st.connectias.plugin.sdk

import android.os.Bundle
import com.ble1st.connectias.plugin.ui.UIEventParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel

/**
 * UI controller for the Three-Process UI Architecture.
 *
 * This interface is intentionally AIDL-free for plugin developers.
 * The host provides an implementation in the sandbox process that forwards calls
 * to the actual UI process via IPC.
 */
interface PluginUIController {

    /**
     * Push a complete UI state for the current plugin.
     */
    fun updateUIState(state: UIStateParcel)

    /**
     * Show a dialog in the UI process.
     *
     * @param dialogType 0=INFO, 1=WARNING, 2=ERROR, 3=CONFIRM
     */
    fun showDialog(title: String, message: String, dialogType: Int = 0)

    /**
     * Show a toast in the UI process.
     *
     * @param duration 0=SHORT, 1=LONG
     */
    fun showToast(message: String, duration: Int = 0)

    /**
     * Navigate to another screen within the plugin UI.
     */
    fun navigateToScreen(screenId: String, args: Bundle = Bundle())

    /**
     * Navigate back in the plugin UI.
     */
    fun navigateBack()

    /**
     * Toggle a loading indicator in the UI process.
     */
    fun setLoading(loading: Boolean, message: String = "")

    /**
     * Send a generic UI event to the UI process.
     */
    fun sendUIEvent(event: UIEventParcel)
}

