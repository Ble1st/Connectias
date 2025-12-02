package com.ble1st.connectias.feature.utilities.ipcalc

import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Provider for IP address calculations.
 */
@Singleton
class IpCalculatorProvider @Inject constructor() {

    /**
     * Calculates subnet information from IP and CIDR.
     */
    fun calculateSubnet(ipAddress: String, cidr: Int): SubnetInfo {
        val ip = ipToLong(ipAddress)
        val subnetMask = cidrToMask(cidr)
        val networkAddress = ip and subnetMask
        val broadcastAddress = networkAddress or subnetMask.inv()
        val firstHost = networkAddress + 1
        val lastHost = broadcastAddress - 1
        val totalHosts = 2.0.pow(32 - cidr).toLong() - 2

        return SubnetInfo(
            ipAddress = ipAddress,
            cidr = cidr,
            subnetMask = longToIp(subnetMask),
            wildcardMask = longToIp(subnetMask.inv()),
            networkAddress = longToIp(networkAddress),
            broadcastAddress = longToIp(broadcastAddress),
            firstUsableHost = longToIp(firstHost),
            lastUsableHost = longToIp(lastHost),
            totalHosts = maxOf(0, totalHosts),
            ipClass = getIpClass(ip),
            isPrivate = isPrivateIp(ip),
            binaryIp = ipToBinary(ipAddress),
            binaryMask = ipToBinary(longToIp(subnetMask))
        )
    }

    /**
     * Calculates subnet from IP and subnet mask.
     */
    fun calculateFromMask(ipAddress: String, subnetMask: String): SubnetInfo {
        val cidr = maskToCidr(subnetMask)
        return calculateSubnet(ipAddress, cidr)
    }

    /**
     * Divides a subnet into smaller subnets.
     */
    fun divideSubnet(networkAddress: String, originalCidr: Int, newCidr: Int): List<SubnetInfo> {
        if (newCidr <= originalCidr) return emptyList()

        val subnets = mutableListOf<SubnetInfo>()
        val numSubnets = 2.0.pow(newCidr - originalCidr).toInt()
        val subnetSize = 2.0.pow(32 - newCidr).toLong()

        var currentNetwork = ipToLong(networkAddress)
        repeat(numSubnets) {
            subnets.add(calculateSubnet(longToIp(currentNetwork), newCidr))
            currentNetwork += subnetSize
        }

        return subnets
    }

    /**
     * Summarizes multiple subnets into a supernet.
     */
    fun summarizeSubnets(subnets: List<Pair<String, Int>>): SubnetInfo? {
        if (subnets.isEmpty()) return null

        val networks = subnets.map { ipToLong(it.first) to it.second }
        val minIp = networks.minOf { it.first }
        val maxIp = networks.maxOf { 
            it.first + (2.0.pow(32 - it.second).toLong() - 1)
        }

        // Find common bits
        var commonBits = 32
        for (i in 31 downTo 0) {
            val bit = 1L shl i
            val minBit = (minIp and bit) != 0L
            val maxBit = (maxIp and bit) != 0L
            if (minBit != maxBit) {
                commonBits = 31 - i
                break
            }
        }

        val mask = cidrToMask(commonBits)
        val networkAddr = minIp and mask

        return calculateSubnet(longToIp(networkAddr), commonBits)
    }

    /**
     * Checks if an IP is within a subnet.
     */
    fun isIpInSubnet(ipAddress: String, networkAddress: String, cidr: Int): Boolean {
        val ip = ipToLong(ipAddress)
        val network = ipToLong(networkAddress)
        val mask = cidrToMask(cidr)
        return (ip and mask) == (network and mask)
    }

    /**
     * Gets overlapping subnets.
     */
    fun findOverlap(subnet1: Pair<String, Int>, subnet2: Pair<String, Int>): Boolean {
        val (net1, cidr1) = subnet1
        val (net2, cidr2) = subnet2

        val network1 = ipToLong(net1)
        val network2 = ipToLong(net2)
        val mask1 = cidrToMask(cidr1)
        val mask2 = cidrToMask(cidr2)

        val broadcast1 = network1 or mask1.inv()
        val broadcast2 = network2 or mask2.inv()

        return !(network1 > broadcast2 || network2 > broadcast1)
    }

    /**
     * Converts IPv4 to IPv6.
     */
    fun ipv4ToIpv6(ipv4: String): String {
        val parts = ipv4.split(".")
        if (parts.size != 4) return "Invalid IPv4"

        val hex1 = String.format("%02x%02x", parts[0].toInt(), parts[1].toInt())
        val hex2 = String.format("%02x%02x", parts[2].toInt(), parts[3].toInt())
        
        return "::ffff:$hex1:$hex2"
    }

    /**
     * Converts decimal to IP.
     */
    fun decimalToIp(decimal: Long): String = longToIp(decimal)

    /**
     * Converts IP to decimal.
     */
    fun ipToDecimal(ipAddress: String): Long = ipToLong(ipAddress)

    // Helper functions

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0
        return parts.fold(0L) { acc, part ->
            (acc shl 8) or (part.toIntOrNull()?.toLong() ?: 0)
        }
    }

    private fun longToIp(value: Long): String {
        return listOf(
            (value shr 24) and 0xFF,
            (value shr 16) and 0xFF,
            (value shr 8) and 0xFF,
            value and 0xFF
        ).joinToString(".")
    }

    private fun cidrToMask(cidr: Int): Long {
        return if (cidr == 0) 0L else (0xFFFFFFFFL shl (32 - cidr)) and 0xFFFFFFFFL
    }

    private fun maskToCidr(mask: String): Int {
        val maskLong = ipToLong(mask)
        var cidr = 0
        var m = maskLong
        while (m and 0x80000000L != 0L) {
            cidr++
            m = m shl 1
        }
        return cidr
    }

    private fun ipToBinary(ip: String): String {
        return ip.split(".").joinToString(".") { part ->
            String.format("%8s", Integer.toBinaryString(part.toInt())).replace(' ', '0')
        }
    }

    private fun getIpClass(ip: Long): String {
        val firstOctet = (ip shr 24) and 0xFF
        return when {
            firstOctet < 128 -> "A"
            firstOctet < 192 -> "B"
            firstOctet < 224 -> "C"
            firstOctet < 240 -> "D (Multicast)"
            else -> "E (Reserved)"
        }
    }

    private fun isPrivateIp(ip: Long): Boolean {
        val firstOctet = (ip shr 24) and 0xFF
        val secondOctet = (ip shr 16) and 0xFF
        
        return when {
            firstOctet == 10L -> true // 10.0.0.0/8
            firstOctet == 172L && secondOctet in 16..31 -> true // 172.16.0.0/12
            firstOctet == 192L && secondOctet == 168L -> true // 192.168.0.0/16
            else -> false
        }
    }
}

/**
 * Subnet calculation result.
 */
@Serializable
data class SubnetInfo(
    val ipAddress: String,
    val cidr: Int,
    val subnetMask: String,
    val wildcardMask: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val firstUsableHost: String,
    val lastUsableHost: String,
    val totalHosts: Long,
    val ipClass: String,
    val isPrivate: Boolean,
    val binaryIp: String,
    val binaryMask: String
)
