// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

import com.ble1st.connectias.plugin.ui.UIComponentParcel;

/**
 * Describes a single UI component (button, text field, list, etc.).
 *
 * Three-Process Architecture:
 * - Sandbox defines components via PluginUIBuilder DSL
 * - Serialized into UIComponentParcel
 * - UI Process renders components with Jetpack Compose
 */
parcelable UIComponentParcel {
    String id;                 // Unique component ID
    String type;               // Component type: "button", "textfield", "list", etc.
    android.os.Bundle properties;  // Component-specific properties
    UIComponentParcel[] children;  // Nested components (for layout containers)
}

