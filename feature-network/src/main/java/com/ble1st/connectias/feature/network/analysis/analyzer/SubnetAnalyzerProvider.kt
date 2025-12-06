package com.ble1st.connectias.feature.network.analysis.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ble1st.connectias.feature.network.analysis.models.SubnetInfo
import com.ble1st.connectias.feature.network.analysis.models.CidrInfo
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for subnet analysis and CIDR calculations.
 */
@Singleton
class SubnetAnalyzerProvider @Inject constructor() {

    /**
     * Parses CIDR notation (e.g., "192.168.1.0/24").
     * 
     * @param cidr CIDR notation string
     * @return CidrInfo or null if invalid
     */
    suspend fun parseCidr(cidr: String): CidrInfo? = withContext(Dispatchers.IO) {
        try {
            val parts = cidr.split("/")
            if (parts.size != 2) return@withContext null

            val ipAddress = parts[0].trim()
            val prefixLength = parts[1].trim().toIntOrNull() ?: return@withContext null

            if (prefixLength < 0 || prefixLength > 32) return@withContext null

            // Validate IP address - must be IPv4
            val inetAddress = InetAddress.getByName(ipAddress)
            if (inetAddress !is Inet4Address) {
                Timber.w("IPv6 address not supported: $ipAddress")
                return@withContext null
            }

            CidrInfo(
                ipAddress = ipAddress,
                prefixLength = prefixLength
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse CIDR: $cidr")
            null
        }
    }

    /**
     * Calculates subnet information from CIDR notation.
     * 
     * @param cidr CIDR notation string (e.g., "192.168.1.0/24")
     * @return SubnetInfo or null if invalid
     */
    suspend fun calculateSubnet(cidr: String): SubnetInfo? = withContext(Dispatchers.IO) {
        try {
            val cidrInfo = parseCidr(cidr) ?: return@withContext null
            val ipAddress = cidrInfo.ipAddress
            val prefixLength = cidrInfo.prefixLength

            val ip = InetAddress.getByName(ipAddress)
            val ipBytes = ip.address

            // Calculate subnet mask
            val subnetMaskBytes = ByteArray(4)
            for (i in 0 until 4) {
                val bits = (prefixLength - i * 8).coerceIn(0, 8)
                subnetMaskBytes[i] = ((0xFF shl (8 - bits)) and 0xFF).toByte()
            }
            val subnetMask = InetAddress.getByAddress(subnetMaskBytes).hostAddress

            // Calculate network address
            val networkBytes = ByteArray(4)
            for (i in 0 until 4) {
                networkBytes[i] = (ipBytes[i].toInt() and subnetMaskBytes[i].toInt()).toByte()
            }
            val networkAddress = InetAddress.getByAddress(networkBytes).hostAddress

            // Calculate broadcast address
            val broadcastBytes = ByteArray(4)
            for (i in 0 until 4) {
                broadcastBytes[i] = (networkBytes[i].toInt() or (subnetMaskBytes[i].toInt().inv() and 0xFF)).toByte()
            }
            val broadcastAddress = InetAddress.getByAddress(broadcastBytes).hostAddress

            // Calculate first and last host with special handling for /31 and /32
            val firstHost: String
            val lastHost: String
            val usableHosts: Long
            
            when (prefixLength) {
                32 -> {
                    // /32 network: single host, network address is the only host
                    firstHost = networkAddress
                    lastHost = networkAddress
                    usableHosts = 1L
                }
                31 -> {
                    // /31 network: point-to-point, both addresses are usable hosts
                    firstHost = networkAddress
                    lastHost = broadcastAddress
                    usableHosts = 2L
                }
                else -> {
                    // Standard networks: network+1 to broadcast-1
                    val firstHostBytes = networkBytes.clone()
                    firstHostBytes[3] = (firstHostBytes[3].toInt() + 1).toByte()
                    firstHost = InetAddress.getByAddress(firstHostBytes).hostAddress

                    val lastHostBytes = broadcastBytes.clone()
                    lastHostBytes[3] = (lastHostBytes[3].toInt() - 1).toByte()
                    lastHost = InetAddress.getByAddress(lastHostBytes).hostAddress
                    
                    usableHosts = (1L shl (32 - prefixLength)) - 2 // Subtract network and broadcast
                }
            }

            // Calculate total hosts
            val totalHosts = (1L shl (32 - prefixLength))

            SubnetInfo(
                networkAddress = networkAddress,
                subnetMask = subnetMask,
                cidr = cidr,
                firstHost = firstHost,
                lastHost = lastHost,
                broadcastAddress = broadcastAddress,
                totalHosts = totalHosts,
                usableHosts = usableHosts
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate subnet for CIDR: $cidr")
            null
        }
    }

    /**
     * Validates if an IP address is in a given subnet.
     * 
     * @param ipAddress IP address to check
     * @param subnetCidr Subnet in CIDR notation
     * @return true if IP is in subnet
     */
    suspend fun isIpInSubnet(ipAddress: String, subnetCidr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val subnetInfo = calculateSubnet(subnetCidr) ?: return@withContext false
            val ip = InetAddress.getByName(ipAddress)
            // Ensure IPv4 addresses
            if (ip !is Inet4Address) return@withContext false
            
            val network = InetAddress.getByName(subnetInfo.networkAddress)
            if (network !is Inet4Address) return@withContext false
            
            val broadcast = InetAddress.getByName(subnetInfo.broadcastAddress)
            if (broadcast !is Inet4Address) return@withContext false

            val ipBytes = ip.address
            val networkBytes = network.address
            val broadcastBytes = broadcast.address

            for (i in 0 until 4) {
                val ipUnsigned = ipBytes[i].toInt() and 0xFF
                val networkUnsigned = networkBytes[i].toInt() and 0xFF
                val broadcastUnsigned = broadcastBytes[i].toInt() and 0xFF
                
                if (ipUnsigned < networkUnsigned || ipUnsigned > broadcastUnsigned) {
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if IP is in subnet")
            false
        }
    }

    /**
     * Discovers subnets from a list of IP addresses.
     * Groups IPs by their likely subnet based on common subnet masks.
     * 
     * @param ipAddresses List of IP addresses
     * @return List of discovered subnets
     */
    suspend fun discoverSubnets(ipAddresses: List<String>): List<SubnetInfo> = withContext(Dispatchers.IO) {
        val subnets = mutableSetOf<String>()

        // Try common subnet masks (/24, /16, /8)
        val commonPrefixes = listOf(24, 16, 8)
        
        for (ip in ipAddresses) {
            try {
                val ipAddr = InetAddress.getByName(ip)
                // Only process IPv4 addresses
                if (ipAddr !is Inet4Address) {
                    Timber.w("Skipping IPv6 address: $ip")
                    continue
                }
                val ipBytes = ipAddr.address

                for (prefix in commonPrefixes) {
                    val networkBytes = ByteArray(4)
                    val octetIndex = prefix / 8
                    val bitsInOctet = prefix % 8

                    for (i in 0 until 4) {
                        if (i < octetIndex) {
                            networkBytes[i] = ipBytes[i]
                        } else if (i == octetIndex) {
                            val mask = (0xFF shl (8 - bitsInOctet)) and 0xFF
                            networkBytes[i] = (ipBytes[i].toInt() and mask).toByte()
                        } else {
                            networkBytes[i] = 0
                        }
                    }

                    val networkAddress = InetAddress.getByAddress(networkBytes).hostAddress
                    val cidr = "$networkAddress/$prefix"
                    subnets.add(cidr)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to process IP: $ip")
            }
        }

        // Calculate subnet info for each discovered subnet
        subnets.mapNotNull { calculateSubnet(it) }
    }
}
