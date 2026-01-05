package com.ble1st.connectias.feature.bluetooth.model

data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

object RssiNormalizer {
    private const val MIN_RSSI = -90
    private const val MAX_RSSI = -35

    fun toFillLevel(rssi: Int): Float {
        val clamped = rssi.coerceIn(MIN_RSSI, MAX_RSSI)
        val normalized = (clamped - MIN_RSSI).toFloat() / (MAX_RSSI - MIN_RSSI).toFloat()
        return normalized.coerceIn(0f, 1f)
    }
}
