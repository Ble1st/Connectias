// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui;

import com.ble1st.connectias.plugin.ui.MotionEventParcel;
import com.ble1st.connectias.plugin.ui.UIComponentParcel;
import com.ble1st.connectias.plugin.ui.UIEventParcel;
import com.ble1st.connectias.plugin.ui.UIStateParcel;
import com.ble1st.connectias.plugin.ui.UserActionParcel;

/**
 * Build-time marker interface.
 *
 * This interface exists to ensure the SDK's structured AIDL parcelables are compiled and
 * exported transitively to consuming modules (e.g., :app) via the AAR AIDL pipeline.
 *
 * It is not used at runtime by the host app or plugins.
 */
interface ISdkAidlMarker {
    void acceptMotionEvent(in MotionEventParcel event);
    void acceptUIComponent(in UIComponentParcel component);
    void acceptUIEvent(in UIEventParcel event);
    void acceptUIState(in UIStateParcel state);
    void acceptUserAction(in UserActionParcel action);
}

