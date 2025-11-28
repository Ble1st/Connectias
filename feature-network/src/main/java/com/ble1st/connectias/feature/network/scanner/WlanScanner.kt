package com.ble1st.connectias.feature.network.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.ble1st.connectias.feature.network.exceptions.PermissionDeniedException
import com.ble1st.connectias.feature.network.models.EncryptionType
import com.ble1st.connectias.feature.network.models.WifiNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of the IT-Tools WLAN scanner that performs real Wi-Fi scans with proper permission checks.
 */
@Singleton
class WlanScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    /**
     * Runs a Wi-Fi scan and returns all visible networks.
     */
    @SuppressLint("MissingPermission")
    suspend fun scan(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        Timber.d("Starting WLAN scan (SDK ${Build.VERSION.SDK_INT})")

        ensurePermissions()

        if (!wifiManager.isWifiEnabled) {
            Timber.w("Wi-Fi is disabled - returning empty result")
            return@withContext emptyList()
        }

        val scanStarted = wifiManager.startScan()
        Timber.d("wifiManager.startScan() returned=$scanStarted")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            delay(2000) // give hardware time on old devices
        }

        return@withContext readScanResults()
    }

    @SuppressLint("MissingPermission")
    fun getCurrentNetwork(): WifiNetwork? {
        if (!hasWifiPermissions()) {
            Timber.w("Missing Wi-Fi permissions, cannot read current connection")
            return null
        }
        return try {
            val info = wifiManager.connectionInfo ?: return null
            if (info.ssid.isNullOrBlank() || info.ssid == "<unknown ssid>") {
                return null
            }
            WifiNetwork(
                ssid = info.ssid.removeSurrounding("\""),
                bssid = info.bssid ?: "",
                signalStrength = info.rssi,
                frequency = info.frequency,
                encryptionType = EncryptionType.Open,
                capabilities = ""
            )
        } catch (t: Throwable) {
            Timber.e(t, "Failed to get current Wi-Fi network")
            null
        }
    }

    private fun ensurePermissions() {
        if (!hasWifiPermissions()) {
            Timber.e("Missing Wi-Fi permissions, throwing PermissionDeniedException")
            throw PermissionDeniedException()
        }
    }

    private fun hasWifiPermissions(): Boolean {
        val required = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        return required.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun readScanResults(): List<WifiNetwork> {
        val scanResults = wifiManager.scanResults
        if (scanResults.isEmpty()) {
            Timber.i("Wi-Fi scan completed with no results")
            return emptyList()
        }

        Timber.d("Processing ${scanResults.size} scan results")
        return scanResults.mapNotNull { scan ->
            runCatching {
                WifiNetwork(
                    ssid = scan.SSID,
                    bssid = scan.BSSID,
                    signalStrength = scan.level,
                    frequency = scan.frequency,
                    encryptionType = determineEncryptionType(scan.capabilities),
                    capabilities = scan.capabilities
                )
            }.onFailure {
                Timber.w(it, "Invalid Wi-Fi network entry: ${maskSsid(scan.SSID)}")
            }.getOrNull()
        }
    }

    private fun determineEncryptionType(capabilities: String): EncryptionType {
        return when {
            capabilities.contains("WPA3") && capabilities.contains("WPA2") -> EncryptionType.WPA2_WPA3_TRANSITION
            capabilities.contains("WPA3") -> EncryptionType.WPA3
            capabilities.contains("OWE") -> EncryptionType.OWE
            capabilities.contains("WPA2") && capabilities.contains("WPA") -> EncryptionType.WPA_WPA2_MIXED
            capabilities.contains("WPA2") -> EncryptionType.WPA2
            capabilities.contains("WPA") && (capabilities.contains("EAP") || capabilities.contains("802.1X")) -> EncryptionType.WPA_ENTERPRISE
            capabilities.contains("WPA") -> EncryptionType.WPA
            capabilities.contains("WEP") -> EncryptionType.WEP
            else -> EncryptionType.Open
        }
    }

    private fun maskSsid(ssid: String?): String {
        if (ssid.isNullOrBlank()) {
            return "[SSID:UNKNOWN]"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val prefix = digest.digest(ssid.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(8)
            .uppercase()
        return "[SSID:$prefix]"
    }
}

