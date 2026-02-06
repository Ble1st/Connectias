// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

import com.ble1st.connectias.plugin.ui.UIStateParcel;
import com.ble1st.connectias.plugin.ui.UIEventParcel;
import android.os.Bundle;

/**
 * Interface for UI updates from Sandbox Process to UI Process.
 *
 * Three-Process Architecture:
 * - Sandbox Process: Manages plugin business logic and state
 * - UI Process: Renders UI based on state updates from sandbox
 * - Main Process: Orchestrates both processes
 *
 * The sandbox process sends state updates, the UI process renders them.
 * This enables true isolation: sandbox cannot access UI, UI cannot access business logic.
 */
interface IPluginUIController {
    /**
     * Updates the complete UI state for a plugin.
     * The UI process will render based on this state.
     *
     * @param pluginId Plugin identifier
     * @param state Complete UI state with components and data
     */
    void updateUIState(String pluginId, in UIStateParcel state);

    /**
     * Shows a dialog in the UI process.
     *
     * @param pluginId Plugin identifier
     * @param title Dialog title
     * @param message Dialog message
     * @param dialogType Type: 0=INFO, 1=WARNING, 2=ERROR, 3=CONFIRM
     */
    void showDialog(String pluginId, String title, String message, int dialogType);

    /**
     * Shows a toast in the UI process.
     *
     * @param pluginId Plugin identifier
     * @param message Toast message
     * @param duration Duration: 0=SHORT, 1=LONG
     */
    void showToast(String pluginId, String message, int duration);

    /**
     * Navigates to another screen within the plugin.
     *
     * @param pluginId Plugin identifier
     * @param screenId Target screen identifier
     * @param args Screen arguments
     */
    void navigateToScreen(String pluginId, String screenId, in Bundle args);

    /**
     * Navigates back (pops backstack).
     *
     * @param pluginId Plugin identifier
     */
    void navigateBack(String pluginId);

    /**
     * Shows/hides loading indicator.
     *
     * @param pluginId Plugin identifier
     * @param loading true to show, false to hide
     * @param message Loading message
     */
    void setLoading(String pluginId, boolean loading, String message);

    /**
     * Sends a generic UI event to the UI process.
     *
     * @param pluginId Plugin identifier
     * @param event UI event data
     */
    void sendUIEvent(String pluginId, in UIEventParcel event);
}
