// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

/**
 * Describes a user action from the UI Process.
 *
 * Three-Process Architecture:
 * - UI Process captures user input (click, swipe, text input, etc.)
 * - Serialized into UserActionParcel
 * - Sent to Sandbox via IPluginUIBridge.onUserAction()
 * - Sandbox processes action and updates state
 */
parcelable UserActionParcel {
    String actionType;         // Action type: "click", "longpress", "swipe", etc.
    String targetId;           // ID of the target component
    android.os.Bundle data;    // Action-specific data
    long timestamp;            // Timestamp of action
}
