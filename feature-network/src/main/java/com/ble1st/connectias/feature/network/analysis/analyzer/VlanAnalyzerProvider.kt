package com.ble1st.connectias.feature.network.analysis.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ble1st.connectias.feature.network.analysis.models.SubnetInfo
import com.ble1st.connectias.feature.network.analysis.models.VlanInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for VLAN analysis.
 * Since Android doesn't provide direct VLAN tag access, VLANs are inferred from subnet segmentation.
 */
@Singleton
class VlanAnalyzerProvider @Inject constructor(
    private val subnetAnalyzerProvider: SubnetAnalyzerProvider
) {

    /**
     * Analyzes VLANs from a list of IP addresses.
     * Groups devices by subnet and infers VLANs based on subnet isolation.
     * 
     * @param ipAddresses List of IP addresses with their associated information
     * @return List of inferred VLANs
     */
    suspend fun analyzeVlans(ipAddresses: List<String>): List<VlanInfo> = withContext(Dispatchers.IO) {
        try {
            // Discover subnets from IP addresses
            val subnets = subnetAnalyzerProvider.discoverSubnets(ipAddresses)

            // Optimize: Build map from subnet CIDR to list of IPs in one pass
            // This reduces complexity from O(subnets × ipAddresses) to O(ipAddresses × subnets)
            // by checking each IP against all subnets once
            val subnetToIps = mutableMapOf<String, MutableList<String>>()
            
            // Initialize map with all discovered subnets
            subnets.forEach { subnet ->
                subnetToIps[subnet.cidr] = mutableListOf()
            }
            
            // For each IP, determine which subnet(s) it belongs to
            ipAddresses.forEach { ip ->
                subnets.forEach { subnet ->
                    try {
                        if (subnetAnalyzerProvider.isIpInSubnet(ip, subnet.cidr)) {
                            subnetToIps[subnet.cidr]?.add(ip)
                        }
                    } catch (e: Exception) {
                        // Skip invalid IPs
                    }
                }
            }

            // Transform subnets into VlanInfo using the pre-computed device lists
            val subnetsWithDevices = subnets.map { subnet ->
                val devicesInSubnet = subnetToIps[subnet.cidr] ?: emptyList()
                VlanInfo(
                    vlanId = extractVlanIdFromSubnet(subnet),
                    subnetInfo = subnet,
                    devices = devicesInSubnet
                )
            }

            Timber.d("Analyzed ${subnetsWithDevices.size} VLANs from ${ipAddresses.size} IP addresses")
            subnetsWithDevices
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze VLANs")
            emptyList()
        }
    }

    /**
     * Extracts a potential VLAN ID from subnet information.
     * This is a heuristic based on common VLAN numbering schemes.
     * 
     * @param subnet Subnet information
     * @return Potential VLAN ID or null if cannot be determined
     */
    private fun extractVlanIdFromSubnet(subnet: SubnetInfo): Int? {
        // Common heuristic: Use the third octet of the network address for /24 subnets
        // This is a simplification and may not always be accurate
        return try {
            val parts = subnet.networkAddress.split(".")
            if (parts.size == 4 && subnet.cidr.endsWith("/24")) {
                parts[2].toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Groups devices by VLAN based on their IP addresses.
     * 
     * @param devices Map of device identifiers to their IP addresses
     * @return Map of VLAN ID to list of device identifiers
     */
    suspend fun groupDevicesByVlan(
        devices: Map<String, String> // deviceId -> ipAddress
    ): Map<Int?, List<String>> = withContext(Dispatchers.IO) {
        try {
            val ipAddresses = devices.values.toList()
            val vlans = analyzeVlans(ipAddresses)

            val result = mutableMapOf<Int?, MutableList<String>>()

            vlans.forEach { vlan ->
                val deviceIds = devices.filter { (_, ip) ->
                    vlan.devices.contains(ip)
                }.keys.toList()

                result.getOrPut(vlan.vlanId) { mutableListOf() }.addAll(deviceIds)
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to group devices by VLAN")
            emptyMap()
        }
    }
}
