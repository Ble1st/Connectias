// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

/**
 * Describes a UI event from Sandbox to UI Process.
 *
 * Three-Process Architecture:
 * - Sandbox generates UI events (navigate, show dialog, etc.)
 * - Serialized into UIEventParcel
 * - Sent to UI Process via IPluginUIController.sendUIEvent()
 * - UI Process executes event (navigation, dialog display, etc.)
 */
parcelable UIEventParcel {
    String eventType;          // Event type: "navigate", "dialog", "toast", etc.
    android.os.Bundle payload; // Event-specific payload
    long timestamp;            // Timestamp of event
}
