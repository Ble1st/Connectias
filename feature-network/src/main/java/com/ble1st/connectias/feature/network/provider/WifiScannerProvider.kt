package com.ble1st.connectias.feature.network.provider

import com.ble1st.connectias.feature.network.exceptions.PermissionDeniedException
import com.ble1st.connectias.feature.network.models.ErrorType
import com.ble1st.connectias.feature.network.models.NetworkResult
import com.ble1st.connectias.feature.network.models.ParcelableList
import com.ble1st.connectias.feature.network.models.WifiNetwork
import com.ble1st.connectias.feature.network.scanner.WlanScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for scanning WiFi networks.
 * Handles Android version differences for WiFi scanning APIs.
 * Returns structured results with proper error handling.
 */
@Singleton
class WifiScannerProvider @Inject constructor(
    private val wlanScanner: WlanScanner
) {
    
    /**
     * Scans for available WiFi networks.
     * Note: Requires ACCESS_FINE_LOCATION permission on Android 6.0+
     * @return NetworkResult containing ParcelableList of networks or error information
     */
    suspend fun scanWifiNetworks(): NetworkResult<ParcelableList<WifiNetwork>> = withContext(Dispatchers.IO) {
        Timber.d("Delegating Wi‑Fi scan to WlanScanner")
        try {
            val networks = wlanScanner.scan()
            Timber.d("WlanScanner returned ${networks.size} networks")
            NetworkResult.Success(ParcelableList(networks))
        } catch (e: PermissionDeniedException) {
            Timber.w(e, "Permission denied for Wi‑Fi scan")
            NetworkResult.Error(
                message = "Location permission is required for Wi‑Fi scanning",
                errorType = ErrorType.PermissionDenied
            )
        } catch (e: Exception) {
            Timber.e(e, "Wi‑Fi scan failed")
            NetworkResult.Error(
                message = e.message ?: "Wi‑Fi scan failed",
                errorType = ErrorType.NetworkError
            )
        }
    }
}

