// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

/**
 * Callback interface implemented by the Main Process.
 * The UI Process calls this to request showing the IME (keyboard) in the Main Process,
 * because the IME does not "serve" windows on VirtualDisplay (Plugin UI runs in UI Process).
 *
 * Flow:
 * 1. User taps TextField in Plugin UI (UI Process, VirtualDisplay)
 * 2. UI Process calls requestShowIme(pluginId, componentId, initialText) on this callback
 * 3. Main Process shows an overlay EditText, focuses it, and shows the keyboard
 * 4. User types in Main Process; Main sends text via IPluginUIHost.sendImeText()
 * 5. When keyboard is dismissed, Main calls IPluginUIHost.onImeDismissed()
 */
interface IPluginUIMainCallback {
    /**
     * Requests the Main Process to show the IME (keyboard) for a plugin text field.
     * Main will display an overlay EditText with initialText and show the soft keyboard.
     *
     * @param pluginId Plugin identifier
     * @param componentId UI component ID (e.g. TextField id)
     * @param initialText Current text to display in the overlay
     */
    void requestShowIme(String pluginId, String componentId, String initialText);
}
