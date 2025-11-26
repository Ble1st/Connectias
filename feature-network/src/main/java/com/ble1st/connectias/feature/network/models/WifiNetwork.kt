package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a Wi‑Fi network discovered during scanning.
 */
@Parcelize
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int, // RSSI in dBm
    val frequency: Int, // MHz
    val encryptionType: EncryptionType,
    val capabilities: String // Raw capabilities string from scan result (e.g., "[WPA2-PSK-CCMP][ESS]")
) : Parcelable {
    init {
        require(signalStrength in -100..0) {
            "Invalid signal strength: $signalStrength dBm (must be between -100 and 0)"
        }
        require(frequency in 2400..2500 || frequency in 5150..5875 || frequency in 5925..7125) {
            "Invalid frequency: $frequency MHz (must be in Wi‑Fi bands: 2.4 GHz (2400-2500), 5 GHz (5150-5875), or 6 GHz (5925-7125))"
        }
    }}

