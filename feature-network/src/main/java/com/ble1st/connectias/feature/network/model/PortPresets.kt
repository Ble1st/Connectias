package com.ble1st.connectias.feature.network.model

object PortPresets {
    val presets = listOf(
        PortRangePreset(label = "1-1024 (Well-known)", start = 1, end = 1024),
        PortRangePreset(label = "1025-49151 (Registered)", start = 1025, end = 49151),
        PortRangePreset(label = "Top 100", start = 1, end = 1024),
        PortRangePreset(label = "Custom", start = 0, end = 0)
    )
}
