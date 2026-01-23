// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

import com.ble1st.connectias.plugin.ui.UIComponentParcel;

/**
 * Describes the complete UI state of a plugin screen.
 * Sent from Sandbox Process to UI Process.
 */
parcelable UIStateParcel {
    String screenId;
    String title;
    android.os.Bundle data;
    UIComponentParcel[] components;
    long timestamp;
}
