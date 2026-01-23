// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

import android.os.Bundle;
import android.os.IBinder;

/**
 * Interface for communication between Main Process and UI Process.
 *
 * Three-Process Architecture:
 * - Main Process: Orchestrates plugin lifecycle and manages UI process
 * - UI Process: Renders plugin UI based on state from sandbox
 * - Sandbox Process: Isolated plugin business logic
 *
 * Main process controls UI process lifecycle via this interface.
 */
interface IPluginUIHost {
    /**
     * Initializes UI for a plugin.
     *
     * @param pluginId Plugin identifier
     * @param configuration UI configuration (theme, size, etc.)
     * @return Fragment container ID, or -1 on error
     */
    int initializePluginUI(String pluginId, in Bundle configuration);

    /**
     * Destroys UI for a plugin.
     *
     * @param pluginId Plugin identifier
     */
    void destroyPluginUI(String pluginId);

    /**
     * Sets UI visibility.
     *
     * @param pluginId Plugin identifier
     * @param visible true to show, false to hide
     */
    void setUIVisibility(String pluginId, boolean visible);

    /**
     * Checks if UI process is ready.
     *
     * @return true if ready to render UI
     */
    boolean isUIProcessReady();

    /**
     * Registers callback for UI process events.
     *
     * @param callback Callback binder
     */
    void registerUICallback(IBinder callback);

    /**
     * Gets the UI Controller for receiving state updates from Sandbox Process.
     * The Main Process uses this to connect Sandbox → UI Process.
     *
     * @return IBinder for IPluginUIController in UI Process
     */
    IBinder getUIController();

    /**
     * Sets a Surface from Main Process for UI rendering in UI Process.
     * The UI Process will use this Surface for VirtualDisplay rendering.
     *
     * @param pluginId Plugin identifier
     * @param surface Surface from Main Process (SurfaceView)
     * @param width Surface width in pixels
     * @param height Surface height in pixels
     * @return True if Surface was set successfully
     */
    boolean setUISurface(String pluginId, in android.view.Surface surface, int width, int height);

    /**
     * Gets a Surface for displaying UI from UI Process in Main Process.
     * Creates a VirtualDisplay in UI Process and returns the Surface.
     *
     * @param pluginId Plugin identifier
     * @param width Surface width in pixels
     * @param height Surface height in pixels
     * @return Surface for rendering, or null on error
     * @deprecated Use setUISurface instead - Surface cannot be transferred via IPC
     */
    android.view.Surface getUISurface(String pluginId, int width, int height);

    /**
     * Dispatches a touch event to the UI Process.
     * The UI Process will forward it to the Sandbox Process via IPluginUIBridge.
     *
     * @param pluginId Plugin identifier
     * @param motionEvent MotionEvent data as parcel
     * @return True if event was consumed
     */
    boolean dispatchTouchEvent(String pluginId, in com.ble1st.connectias.plugin.ui.MotionEventParcel motionEvent);

    /**
     * Notifies UI Process about lifecycle events from Main Process.
     * The UI Process will forward these to the Sandbox Process.
     *
     * @param pluginId Plugin identifier
     * @param event Lifecycle event (onStart, onResume, onPause, onStop, onDestroy)
     */
    void notifyUILifecycle(String pluginId, String event);
}
