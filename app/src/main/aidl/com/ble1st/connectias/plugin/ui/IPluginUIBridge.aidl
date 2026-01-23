// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

import com.ble1st.connectias.plugin.ui.UserActionParcel;
import android.os.Bundle;

/**
 * Interface for user interactions from UI Process to Sandbox Process.
 *
 * Three-Process Architecture:
 * - UI Process: Captures user input and forwards to sandbox
 * - Sandbox Process: Processes user actions and updates state
 * - Main Process: Orchestrates lifecycle
 *
 * The UI process forwards all user events to the sandbox via this interface.
 * The sandbox processes them and sends state updates back via IPluginUIController.
 */
interface IPluginUIBridge {
    /**
     * User clicked a button.
     *
     * @param pluginId Plugin identifier
     * @param buttonId Button component ID
     * @param extras Additional data from button
     */
    void onButtonClick(String pluginId, String buttonId, in Bundle extras);

    /**
     * Text in an input field changed.
     *
     * @param pluginId Plugin identifier
     * @param fieldId TextField component ID
     * @param value New text value
     */
    void onTextChanged(String pluginId, String fieldId, String value);

    /**
     * User selected a list item.
     *
     * @param pluginId Plugin identifier
     * @param listId List component ID
     * @param position Item position
     * @param itemData Item-specific data
     */
    void onItemSelected(String pluginId, String listId, int position, in Bundle itemData);

    /**
     * Fragment lifecycle event from UI process.
     *
     * Events: "onCreate", "onResume", "onPause", "onDestroy"
     *
     * @param pluginId Plugin identifier
     * @param event Lifecycle event name
     */
    void onLifecycleEvent(String pluginId, String event);

    /**
     * Generic user action (e.g., swipe, long press, etc.).
     *
     * @param pluginId Plugin identifier
     * @param action User action data
     */
    void onUserAction(String pluginId, in UserActionParcel action);

    /**
     * Permission result from UI process.
     *
     * @param pluginId Plugin identifier
     * @param permission Permission name
     * @param granted true if granted, false if denied
     */
    void onPermissionResult(String pluginId, String permission, boolean granted);
}
