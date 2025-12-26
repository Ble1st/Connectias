package com.ble1st.connectias.feature.network.wifi

import android.annotation.SuppressLint
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import com.ble1st.connectias.feature.network.model.WifiNetwork
import com.ble1st.connectias.feature.network.model.securityTypeFromCapabilities
import com.ble1st.connectias.feature.network.model.wifiChannelFromFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Suppress("DEPRECATION")
class WifiScanner(
    private val wifiManager: WifiManager
) {

    @SuppressLint("MissingPermission")
    suspend fun scan(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        runCatching { wifiManager.startScan() }
            .onFailure { Timber.w(it, "Wifi scan start failed") }

        val results = runCatching { wifiManager.scanResults.orEmpty() }
            .getOrElse { throwable ->
                Timber.e(throwable, "Failed to read wifi scan results")
                emptyList()
            }

        results.map { toWifiNetwork(it) }
    }

    private fun toWifiNetwork(result: ScanResult): WifiNetwork {
        val security = securityTypeFromCapabilities(result.capabilities ?: "")
        return WifiNetwork(
            ssid = result.SSID,
            bssid = result.BSSID,
            rssi = result.level,
            frequency = result.frequency,
            channel = wifiChannelFromFrequency(result.frequency),
            security = security,
            capabilities = result.capabilities ?: "UNKNOWN"
        )
    }

}
