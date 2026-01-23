// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui

import android.os.Bundle

/**
 * Represents a user action in the plugin UI (SDK-side).
 *
 * This is the SDK-side representation that will be converted to/from
 * UserActionParcel for IPC communication.
 *
 * User actions are generated in the UI Process when users interact
 * with plugin UI components, then sent to the Sandbox Process for handling.
 */
data class UserAction(
    val actionType: String,
    val targetId: String,
    val data: Bundle = Bundle(),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Action types
        const val ACTION_CLICK = "click"
        const val ACTION_LONG_PRESS = "longpress"
        const val ACTION_TEXT_CHANGED = "text_changed"
        const val ACTION_ITEM_SELECTED = "item_selected"
        const val ACTION_CHECKBOX_CHANGED = "checkbox_changed"
        const val ACTION_SWIPE = "swipe"
    }
}

/**
 * UI lifecycle events.
 */
object UILifecycleEvent {
    const val ON_CREATE = "onCreate"
    const val ON_START = "onStart"
    const val ON_RESUME = "onResume"
    const val ON_PAUSE = "onPause"
    const val ON_STOP = "onStop"
    const val ON_DESTROY = "onDestroy"
}
