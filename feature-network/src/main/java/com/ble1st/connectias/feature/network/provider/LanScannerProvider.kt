package com.ble1st.connectias.feature.network.provider

import com.ble1st.connectias.core.services.NetworkService
import com.ble1st.connectias.feature.network.models.DeviceType
import com.ble1st.connectias.feature.network.models.ErrorType
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.NetworkResult
import com.ble1st.connectias.feature.network.models.ParcelableList
import com.ble1st.connectias.feature.network.scanner.NetworkDiscoveryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for scanning devices on the local network.
 * Performs parallel IP scanning with timeout for each host.
 * Returns structured results with proper error handling.
 */
@Singleton
class LanScannerProvider @Inject constructor(
    private val networkService: NetworkService,
    private val discoveryService: NetworkDiscoveryService
) {
    
    /**
     * Scans the local network for reachable devices.
     * Requires gateway information from NetworkService.
     * @return NetworkResult containing ParcelableList of devices or error information
     */
    suspend fun scanLocalNetwork(): NetworkResult<ParcelableList<NetworkDevice>> = withContext(Dispatchers.IO) {
        Timber.d("LAN scan started")
        val gateway = networkService.getGateway()
        if (gateway.isNullOrBlank()) {
            Timber.w("No gateway available, cannot perform LAN scan")
            return@withContext NetworkResult.Error(
                message = "Network gateway is unavailable. Please ensure you are connected to a network.",
                errorType = ErrorType.ConfigurationUnavailable
            )
        }
        
        val subnet = calculateSubnet(gateway)
        if (subnet.isNullOrBlank()) {
            Timber.w("Unable to derive subnet from gateway: $gateway")
            return@withContext NetworkResult.Error(
                message = "Unable to determine network configuration from gateway: $gateway",
                errorType = ErrorType.ConfigurationUnavailable
            )
        }

        return@withContext try {
            val devices = discoveryService.discoverDevices(subnet)
                .mapNotNull { device ->
                    NetworkDevice.create(
                        ipAddress = device.ipAddress,
                        hostname = device.hostname ?: device.ipAddress,
                        macAddress = null,
                        deviceType = determineDeviceType(device.hostname ?: device.ipAddress),
                        isReachable = true
                    ).onFailure {
                        Timber.w(it, "Invalid device returned from discovery ${device.ipAddress}")
                    }.getOrNull()
                }
            Timber.d("LAN scan completed: found ${devices.size} devices")
            NetworkResult.Success(ParcelableList(devices))
        } catch (e: Exception) {
            Timber.e(e, "LAN scan failed")
            NetworkResult.Error(
                message = e.message ?: "LAN scan failed",
                errorType = ErrorType.NetworkError
            )
        }
    }

    private fun calculateSubnet(gateway: String): String? {
        Timber.d("Calculating subnet from gateway: $gateway")
        // Example: 192.168.1.1 -> 192.168.1.0/24
        val parts = gateway.split(".")
        if (parts.size != 4) {
            Timber.w("Invalid gateway format: $gateway (expected IPv4 address, got ${parts.size} parts)")
            return null
        }
        
        return try {
            parts.forEachIndexed { index, part ->
                val num = part.toInt()
                if (num < 0 || num > 255) {
                    Timber.w("Invalid gateway IP part[$index]: $part (must be 0-255)")
                    return null
                }
            }
            "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
        } catch (e: NumberFormatException) {
            Timber.w(e, "Invalid gateway IP format: $gateway")
            return null
        }
    }
    
    private fun determineDeviceType(hostname: String): DeviceType {
        return when {
            hostname.contains("router", ignoreCase = true) -> DeviceType.ROUTER
            hostname.contains("printer", ignoreCase = true) -> DeviceType.PRINTER
            hostname.contains("android", ignoreCase = true) || 
            hostname.contains("iphone", ignoreCase = true) -> DeviceType.SMARTPHONE
            hostname.contains("ipad", ignoreCase = true) ||
            hostname.contains("tablet", ignoreCase = true) -> DeviceType.TABLET
            hostname.contains("iot", ignoreCase = true) || 
            hostname.contains("smart", ignoreCase = true) -> DeviceType.IOT_DEVICE
            hostname.contains("pc", ignoreCase = true) || 
            hostname.contains("computer", ignoreCase = true) || 
            hostname.contains("laptop", ignoreCase = true) -> DeviceType.COMPUTER
            else -> DeviceType.UNKNOWN
        }
    }
}

