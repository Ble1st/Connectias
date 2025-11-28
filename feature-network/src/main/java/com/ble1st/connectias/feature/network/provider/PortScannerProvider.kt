package com.ble1st.connectias.feature.network.provider

import com.ble1st.connectias.feature.network.models.NetworkResult
import com.ble1st.connectias.feature.network.models.ParcelableList
import com.ble1st.connectias.feature.network.models.PortScanResult
import com.ble1st.connectias.feature.network.scanner.PortScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for port scanning operations.
 */
@Singleton
class PortScannerProvider @Inject constructor(
    private val portScanner: PortScanner
) {
    
    /**
     * Scans ports on a host.
     * 
     * @param host The host IP address
     * @param ports List of ports to scan (null for common ports)
     * @param timeoutMs Timeout per port in milliseconds
     * @return NetworkResult with scan results
     */
    suspend fun scanHost(
        host: String,
        ports: List<Int>? = null,
        timeoutMs: Int = 1000
    ): NetworkResult<ParcelableList<PortScanResult>> = withContext(Dispatchers.IO) {
        try {
            if (host.isBlank()) {
                return@withContext NetworkResult.Error(
                    message = "Host cannot be empty",
                    errorType = com.ble1st.connectias.feature.network.models.ErrorType.ConfigurationUnavailable
                )
            }

            val portsToScan = ports ?: portScanner.COMMON_PORTS
            Timber.d("Scanning ${portsToScan.size} ports on $host")
            
            val results = if (portsToScan.size <= 20) {
                // For small port lists, scan common ports
                portScanner.scanCommonPorts(host, timeoutMs)
            } else {
                portScanner.scanPorts(host, portsToScan, timeoutMs)
            }
            
            val openPorts = results.filter { it.isOpen }
            Timber.d("Found ${openPorts.size} open ports on $host")
            
            NetworkResult.Success(ParcelableList(results))
        } catch (e: Exception) {
            Timber.e(e, "Port scan failed for host $host")
            NetworkResult.Error(
                message = e.message ?: "Port scan failed",
                errorType = com.ble1st.connectias.feature.network.models.ErrorType.NetworkError
            )
        }
    }
}

