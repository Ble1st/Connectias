// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui

/**
 * UI lifecycle events sent from UI Process to Sandbox Process.
 */
object UILifecycleEvent {
    const val ON_CREATE = "onCreate"
    const val ON_START = "onStart"
    const val ON_RESUME = "onResume"
    const val ON_PAUSE = "onPause"
    const val ON_STOP = "onStop"
    const val ON_DESTROY = "onDestroy"
}

