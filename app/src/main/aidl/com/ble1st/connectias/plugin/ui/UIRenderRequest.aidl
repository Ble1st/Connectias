// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

/**
 * Request to render plugin UI in the sandbox process.
 *
 * Contains all information needed to create a VirtualDisplay
 * and render the plugin's fragment.
 */
parcelable UIRenderRequest {
    /**
     * Plugin ID whose UI should be rendered
     */
    String pluginId;

    /**
     * Width of the display in pixels
     */
    int width;

    /**
     * Height of the display in pixels
     */
    int height;

    /**
     * Display density (e.g., 1.0, 2.0, 3.0 for mdpi, xhdpi, xxhdpi)
     */
    float density;

    /**
     * Display DPI (dots per inch)
     */
    int densityDpi;

    /**
     * Whether to enable hardware acceleration for rendering
     */
    boolean hardwareAccelerated = true;

    /**
     * Initial fragment arguments (serialized as JSON string)
     */
    String fragmentArgs;

    /**
     * Whether this is a Jetpack Compose UI or traditional View-based
     */
    boolean isCompose = false;
}
