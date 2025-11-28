package com.ble1st.connectias.feature.network.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LAN discovery service based on the IT-Tools implementation.
 * Performs configurable IP sweep within the provided subnet with parallel scanning.
 */
@Singleton
class NetworkDiscoveryService @Inject constructor() {

    data class DiscoveredDevice(
        val ipAddress: String,
        val hostname: String?,
        val manufacturer: String? = null
    )

    /**
     * Discovers devices in the given subnet using default configuration.
     */
    suspend fun discoverDevices(subnet: String): List<DiscoveredDevice> {
        return discoverDevices(subnet, NetworkScanConfig.DEFAULT)
    }

    /**
     * Discovers devices in the given subnet using the provided configuration.
     * 
     * @param subnet Subnet in CIDR notation (e.g., "192.168.1.0/24")
     * @param config Configuration for scan behavior
     * @return List of discovered devices
     */
    suspend fun discoverDevices(
        subnet: String,
        config: NetworkScanConfig
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        Timber.d("Discovering devices in subnet: $subnet with config: maxHosts=${config.maxHostsToScan}, timeout=${config.hostTimeoutMs}ms, parallel=${config.maxParallelScans}")

        val baseIp = subnet.substringBeforeLast(".")
        val devices = mutableListOf<DiscoveredDevice>()
        val hostsToScan = config.maxHostsToScan.coerceAtMost(254)

        coroutineScope {
            val scanJobs = (1..hostsToScan).chunked(config.maxParallelScans).flatMap { chunk ->
                chunk.map { lastOctet ->
                    async {
                        val ip = "$baseIp.$lastOctet"
                        checkHost(ip, config)
                    }
                }
            }

            for (job in scanJobs) {
                val device = job.await()
                if (device != null) {
                    devices.add(device)
                    Timber.d("Found device: ${device.ipAddress} (${device.hostname ?: "unknown hostname"})")
                    
                    // Stop early if configured
                    if (config.stopAfterDevicesFound != null && devices.size >= config.stopAfterDevicesFound) {
                        Timber.d("Reached stopAfterDevicesFound limit (${config.stopAfterDevicesFound}), cancelling remaining scans")
                        break
                    }
                }
            }
        }

        Timber.i("Found ${devices.size} devices in subnet $subnet (scanned $hostsToScan hosts)")
        devices
    }

    private suspend fun checkHost(
        ip: String,
        config: NetworkScanConfig
    ): DiscoveredDevice? = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(ip)
            val isReachable = withTimeoutOrNull(config.hostTimeoutMs.toLong()) {
                address.isReachable(config.hostTimeoutMs)
            } ?: false

            if (isReachable) {
                val hostname = if (config.performReverseDns) {
                    try {
                        address.hostName.takeIf { it != ip }
                    } catch (e: Exception) {
                        Timber.v("Reverse DNS lookup failed for $ip: ${e.message}")
                        null
                    }
                } else {
                    null
                }

                DiscoveredDevice(
                    ipAddress = ip,
                    hostname = hostname,
                    manufacturer = null // Would require MAC address lookup
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.v("IP $ip check failed: ${e.message}")
            null
        }
    }
}

