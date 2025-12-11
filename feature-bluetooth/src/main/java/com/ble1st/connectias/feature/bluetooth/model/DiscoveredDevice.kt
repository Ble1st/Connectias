package com.ble1st.connectias.feature.bluetooth.model

data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

object RssiNormalizer {
    private const val minRssi = -90
    private const val maxRssi = -35

    fun toFillLevel(rssi: Int): Float {
        val clamped = rssi.coerceIn(minRssi, maxRssi)
        val normalized = (clamped - minRssi).toFloat() / (maxRssi - minRssi).toFloat()
        return normalized.coerceIn(0f, 1f)
    }
}
