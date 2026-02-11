// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.servicestate

/**
 * Stable IDs for toggleable app services (Service Dashboard).
 */
object ServiceIds {
    const val PLUGIN_SANDBOX = "plugin_sandbox"
    const val HARDWARE_BRIDGE = "hardware_bridge"
    const val FILE_SYSTEM_BRIDGE = "file_system_bridge"
    const val PLUGIN_MESSAGING = "plugin_messaging"
    const val PLUGIN_UI = "plugin_ui"

    /** All service IDs in display order. */
    val ALL: List<String> = listOf(
        PLUGIN_SANDBOX,
        HARDWARE_BRIDGE,
        FILE_SYSTEM_BRIDGE,
        PLUGIN_MESSAGING,
        PLUGIN_UI
    )
}
