package com.ble1st.connectias.feature.network.provider

import android.content.Context
import com.ble1st.connectias.core.services.NetworkService
import com.ble1st.connectias.feature.network.models.DeviceType
import com.ble1st.connectias.feature.network.models.NetworkDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for scanning devices on the local network.
 * Performs parallel IP scanning with timeout for each host.
 */
@Singleton
class LanScannerProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkService: NetworkService
) {
    companion object {
        private const val SCAN_TIMEOUT_MS = 500L // Timeout per IP check (increased for reliability)
        private const val MAX_SCAN_IPS = 254 // Max IPs in subnet
    }
    
    /**
     * Scans the local network for reachable devices.
     * Requires gateway information from NetworkService.
     */
    suspend fun scanLocalNetwork(): List<NetworkDevice> = withContext(Dispatchers.IO) {
        Timber.d("LAN scan started")
        try {
            // 1. Get gateway IP via NetworkService
            Timber.d("Fetching gateway IP from NetworkService")
            val gateway = networkService.getGateway()
            if (gateway == null) {
                Timber.w("Gateway is null, cannot determine subnet")
                return@withContext emptyList()
            }
            Timber.d("Gateway IP: $gateway")
            
            // 2. Calculate subnet (e.g., 192.168.1.0/24)
            Timber.d("Calculating subnet from gateway: $gateway")
            val subnet = calculateSubnet(gateway)
            if (subnet == null) {
                Timber.w("Failed to calculate subnet from gateway: $gateway")
                return@withContext emptyList()
            }
            Timber.d("Calculated subnet: $subnet")
            
            // 3. Parallel scanning of all IPs in subnet
            Timber.d("Starting subnet scan: $subnet")
            val devices = scanSubnet(subnet)
            Timber.d("LAN scan completed: found ${devices.size} devices")
            return@withContext devices
        } catch (e: Exception) {
            Timber.e(e, "LAN scan failed")
            return@withContext emptyList()
        }
    }
    
    /**
     * Calculates subnet from gateway IP address.
     * Currently assumes /24 subnet. Future enhancement: read actual subnet mask from network stack.
     * @param gateway Gateway IP address (e.g., "192.168.1.1")
     * @return Subnet in CIDR notation (e.g., "192.168.1.0/24") or null if invalid format
     */
    private fun calculateSubnet(gateway: String): String? {
        Timber.d("Calculating subnet from gateway: $gateway")
        // Example: 192.168.1.1 -> 192.168.1.0/24
        val parts = gateway.split(".")
        if (parts.size != 4) {
            Timber.w("Invalid gateway format: $gateway (expected IPv4 address, got ${parts.size} parts)")
            return null
        }
        
        // Validate that all parts are valid integers
        try {
            parts.forEachIndexed { index, part ->
                val num = part.toInt()
                if (num < 0 || num > 255) {
                    Timber.w("Invalid gateway IP part[$index]: $part (must be 0-255)")
                    return null
                }
            }
            val subnet = "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
            Timber.d("Calculated subnet: $subnet from gateway: $gateway")
            return subnet
        } catch (e: NumberFormatException) {
            Timber.w(e, "Invalid gateway IP format: $gateway")
            return null
        }
    }
    
    /**
     * Scans a subnet for reachable devices.
     * @param subnet Subnet in CIDR notation (e.g., "192.168.1.0/24")
     * @return List of reachable network devices
     */
    private suspend fun scanSubnet(subnet: String): List<NetworkDevice> = coroutineScope {
        Timber.d("Starting subnet scan: $subnet")
        // Validate subnet format: must contain "/" and have exactly 2 parts
        val subnetParts = subnet.split("/")
        if (subnetParts.size != 2) {
            Timber.e("Invalid subnet format: $subnet (expected CIDR notation like '192.168.1.0/24')")
            return@coroutineScope emptyList()
        }
        
        val baseIp = subnetParts[0]
        val prefix = subnetParts[1]
        Timber.d("Subnet base IP: $baseIp, prefix: $prefix")
        
        // Validate IP address format: must have exactly 4 parts
        val baseParts = baseIp.split(".")
        if (baseParts.size != 4) {
            Timber.e("Invalid IP address format: $baseIp (expected IPv4 address, got ${baseParts.size} parts)")
            return@coroutineScope emptyList()
        }
        
        // Validate and convert IP parts to integers
        val baseIpInt = try {
            val part0 = baseParts[0].toInt()
            val part1 = baseParts[1].toInt()
            val part2 = baseParts[2].toInt()
            val part3 = baseParts[3].toInt()
            
            // Validate IP address range (0-255 for each part)
            if (part0 !in 0..255 || part1 !in 0..255 || part2 !in 0..255 || part3 !in 0..255) {
                Timber.e("Invalid IP address range: $baseIp (each part must be 0-255)")
                return@coroutineScope emptyList()
            }
            
            (part0 shl 24) or (part1 shl 16) or (part2 shl 8) or part3
        } catch (e: NumberFormatException) {
            Timber.e(e, "Invalid IP address format: $baseIp")
            return@coroutineScope emptyList()
        }
        
        // Parse CIDR prefix (currently always /24, but prepared for future enhancement)
        val prefixInt = prefix.toIntOrNull()
        if (prefixInt == null) {
            Timber.e("Invalid CIDR prefix: $prefix (not a number)")
            return@coroutineScope emptyList()
        }
        if (prefixInt !in 1..32) {
            Timber.e("Invalid CIDR prefix: $prefix (must be 1-32)")
            return@coroutineScope emptyList()
        }
        
        val hostBits = 32 - prefixInt
        val maxHosts = (1 shl hostBits) - 2 // Exclude network and broadcast
        val hostsToScan = maxHosts.coerceAtMost(MAX_SCAN_IPS)
        Timber.d("Subnet calculation: prefix=$prefixInt, hostBits=$hostBits, maxHosts=$maxHosts, hostsToScan=$hostsToScan")
        
        // Calculate network base address
        val networkMask = (-1 shl hostBits).toInt()
        val networkBase = baseIpInt and networkMask
        Timber.d("Network base address: $networkBase (mask: $networkMask)")
        
        // Parallel scanning with async
        Timber.d("Creating $hostsToScan parallel scan jobs")
        val scanJobs = (1..hostsToScan).map { hostOffset ->
            async {
                val hostIp = networkBase + hostOffset
                val ip = String.format(
                    "%d.%d.%d.%d",
                    (hostIp shr 24) and 0xFF,
                    (hostIp shr 16) and 0xFF,
                    (hostIp shr 8) and 0xFF,
                    hostIp and 0xFF
                )
                checkHost(ip)
            }
        }
        
        Timber.d("Waiting for all scan jobs to complete")
        // Wait for all scans with timeout
        val devices = scanJobs.awaitAll().filterNotNull()
        
        Timber.d("Subnet scan completed: found ${devices.size} reachable devices out of $hostsToScan scanned")
        // Return empty list if no devices found (benign condition, not an error)
        devices
    }
    
    private suspend fun checkHost(ip: String): NetworkDevice? = withContext(Dispatchers.IO) {
        Timber.v("Checking host: $ip")
        try {
            // Parse IP address directly from bytes to avoid DNS resolution
            val ipParts = ip.split(".")
            if (ipParts.size != 4) {
                Timber.v("Invalid IP format for $ip: ${ipParts.size} parts")
                return@withContext null
            }
            
            val ipBytes = ByteArray(4) {
                ipParts[it].toInt().coerceIn(0, 255).toByte()
            }
            
            val address = InetAddress.getByAddress(ipBytes)
            Timber.v("Created InetAddress for $ip, checking reachability (timeout: ${SCAN_TIMEOUT_MS}ms)")
            
            // Only apply timeout to the blocking isReachable call
            val isReachable = withTimeout(SCAN_TIMEOUT_MS) {
                address.isReachable(SCAN_TIMEOUT_MS.toInt())
            }
            
            if (isReachable) {
                Timber.d("Host $ip is reachable")
                // Use IP as hostname to avoid expensive reverse DNS lookup
                // Reverse DNS can be performed separately if needed
                val hostname = ip
                val deviceType = determineDeviceType(hostname)
                
                val deviceResult = NetworkDevice.create(
                    ipAddress = ip,
                    hostname = hostname,
                    macAddress = null, // MAC address not available without root
                    deviceType = deviceType,
                    isReachable = true
                )
                
                return@withContext deviceResult.fold(
                    onSuccess = { device ->
                        Timber.d("Created NetworkDevice: $ip (${device.deviceType.name})")
                        device
                    },
                    onFailure = { error ->
                        Timber.w(error, "Failed to create NetworkDevice for $ip: ${error.message}")
                        null
                    }
                )
            } else {
                Timber.v("Host $ip is not reachable")
                null
            }
        } catch (e: TimeoutCancellationException) {
            Timber.v("Host $ip check timed out (normal for unreachable IPs)")
            null // Timeout is normal for unreachable IPs
        } catch (e: Exception) {
            Timber.d("Failed to check host $ip: ${e.message}")
            null
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
            else -> DeviceType.UNKNOWN        }
    }
}

