package com.ble1st.connectias.feature.network.provider

import android.Manifest
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.ble1st.connectias.core.services.NetworkService
import com.ble1st.connectias.feature.network.BuildConfig
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
 * Provider for scanning WiFi networks.
 * Handles Android version differences for WiFi scanning APIs.
 */
@Singleton
class WifiScannerProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkService: NetworkService
) {
    private val wifiManager: WifiManager? by lazy {
        val manager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (manager == null) {
            Timber.w("WifiManager is null - Context.WIFI_SERVICE unavailable. Wi‑Fi scanning will return empty results.")
        }
        manager
    }
    
    /**
     * Scans for available WiFi networks.
     * Note: Requires ACCESS_FINE_LOCATION permission on Android 6.0+
     */
    suspend fun scanWifiNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        Timber.d("Wi‑Fi scan started")
        try {
            if (wifiManager?.isWifiEnabled != true) {
                Timber.d("WiFi is disabled, cannot scan")
                return@withContext emptyList()
            }
            
            Timber.d("WiFi is enabled, starting scan (Android SDK: ${Build.VERSION.SDK_INT})")
            
            // Android 9+ (API 28+) Limitation: startScan() has restrictions
            // Try to trigger scan on all API levels, then read results
            val scanStarted = wifiManager?.startScan() == true
            Timber.d("Wi‑Fi scan start result: $scanStarted")
            if (!scanStarted && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                Timber.w("Wi‑Fi scan start failed on Android < 9")
            }
            
            // Wait for scan results (Android P+ throttles to 4 scans per 2 minutes)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                Timber.d("Waiting 2000ms for scan results (Android < 9)")
                delay(2000) // Wait for scan results on older Android versions
            } else {
                Timber.d("Using cached scan results (Android 9+)")
            }
            
            return@withContext getAvailableWifiNetworks().also { networks ->
                Timber.d("Wi‑Fi scan completed: found ${networks.size} networks")
            }        } catch (e: SecurityException) {
            Timber.e(e, "Wi‑Fi scan failed - missing permissions")
            throw PermissionDeniedException("Location permission is required for Wi‑Fi scanning", e)
        } catch (e: Exception) {
            Timber.e(e, "Wi‑Fi scan failed")
            throw e // Propagate critical exceptions
        }
    }
    
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private suspend fun getAvailableWifiNetworks(): List<WifiNetwork> {
        // Uses scan results (may be cached on Android 9+)
        // Consider WifiNetworkSuggestion API for future enhancement
        val scanResults = wifiManager?.scanResults ?: run {
            Timber.d("scanResults is null - scan may not have completed")
            return emptyList()
        }
        
        Timber.d("Processing ${scanResults.size} raw scan results")
        
        // Check freshness of scan results (optional - can be enhanced later)
        var validCount = 0
        var invalidCount = 0
        val networks = scanResults.mapNotNull { scanResult ->
            try {
                val network = WifiNetwork(
                    ssid = scanResult.SSID,
                    bssid = scanResult.BSSID,
                    signalStrength = scanResult.level,
                    frequency = scanResult.frequency,
                    encryptionType = determineEncryptionType(scanResult.capabilities),
                    capabilities = scanResult.capabilities
                )
                validCount++
                Timber.d(
                    "Valid network: ${formatSsidForLog(network.ssid)} (${network.signalStrength} dBm, ${network.encryptionType.displayName})"
                )
                network
            } catch (e: IllegalArgumentException) {
                invalidCount++
                Timber.w(e, "Invalid Wi‑Fi network data: ${formatSsidForLog(scanResult.SSID)}")
                null // Skip invalid networks
            }
        }
        
        Timber.d("Network processing complete: $validCount valid, $invalidCount invalid out of ${scanResults.size} total")
        
        // Return empty list if no valid networks found
        return networks
    }    
    private fun determineEncryptionType(capabilities: String): EncryptionType {
        val encryptionType = when {
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
        Timber.v("Determined encryption type: ${encryptionType.displayName} from capabilities: $capabilities")
        return encryptionType
    }

    private fun formatSsidForLog(ssid: String?): String {
        if (ssid.isNullOrBlank()) {
            return "[SSID:UNKNOWN]"
        }
        if (BuildConfig.DEBUG) {
            return ssid
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(ssid.toByteArray(StandardCharsets.UTF_8))
        val prefix = hashBytes.joinToString("") { byte -> "%02x".format(byte) }
            .take(8)
            .uppercase()
        return "[SSID:$prefix]"
    }
}

