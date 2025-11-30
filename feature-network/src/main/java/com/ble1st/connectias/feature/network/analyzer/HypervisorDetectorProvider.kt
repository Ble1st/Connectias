package com.ble1st.connectias.feature.network.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ble1st.connectias.feature.network.models.HypervisorInfo
import com.ble1st.connectias.feature.network.models.HypervisorType
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.VmInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for hypervisor and VM detection.
 * Detects VMs based on MAC address OUI lookup and hostname patterns.
 * 
 * Note: Requires OUI lookup from feature-network-analysis module.
 * This is a simplified implementation that uses heuristics.
 */
@Singleton
class HypervisorDetectorProvider @Inject constructor() {

    /**
     * Normalizes MAC address to a consistent format (uppercase, colon-separated).
     * Handles various input formats: colon-separated, dash-separated, or no separators.
     * 
     * @param macAddress MAC address in any format
     * @return Normalized MAC address (uppercase, colon-separated) or null if invalid
     */
    private fun normalizeMacAddress(macAddress: String?): String? {
        if (macAddress == null) return null
        
        // Remove all separators and convert to uppercase
        val cleaned = macAddress.replace(":", "").replace("-", "").replace(".", "").uppercase()
        
        // Validate format (should be 12 hex characters)
        if (cleaned.length != 12 || !cleaned.matches(Regex("^[0-9A-F]{12}$"))) {
            return null
        }
        
        // Format as colon-separated: XX:XX:XX:XX:XX:XX
        return cleaned.chunked(2).joinToString(":")
    }

    /**
     * Detects hypervisors and VMs from discovered devices.
     * 
     * @param devices List of discovered network devices
     * @param macManufacturers Map of MAC addresses to manufacturer names (from OUI lookup)
     * @return List of detected hypervisor information
     */
    suspend fun detectHypervisors(
        devices: List<NetworkDevice>,
        macManufacturers: Map<String, String?> = emptyMap()
    ): List<HypervisorInfo> = withContext(Dispatchers.IO) {
        try {
            val hypervisors = mutableMapOf<HypervisorType, MutableList<NetworkDevice>>()

            devices.forEach { device ->
                val hypervisorType = detectHypervisorType(device, macManufacturers)
                if (hypervisorType != HypervisorType.UNKNOWN) {
                    hypervisors.getOrPut(hypervisorType) { mutableListOf() }.add(device)
                }
            }

            hypervisors.map { (type, vms) ->
                val vmInfos = vms.map { device ->
                    VmInfo(
                        ipAddress = device.ipAddress,
                        hostname = device.hostname,
                        macAddress = device.macAddress,
                        hypervisorType = type,
                        hostIp = null // Would require additional detection
                    )
                }

                HypervisorInfo(
                    type = type,
                    hostIp = null, // Would require additional detection
                    hostname = null,
                    detectedVms = vmInfos
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to detect hypervisors")
            emptyList()
        }
    }

    /**
     * Detects hypervisor type for a device based on MAC address and hostname patterns.
     */
    private fun detectHypervisorType(
        device: NetworkDevice,
        macManufacturers: Map<String, String?>
    ): HypervisorType {
        // Check MAC address manufacturer - normalize MAC before lookup
        device.macAddress?.let { mac ->
            val normalizedMac = normalizeMacAddress(mac) ?: return@let
            // Normalize keys in macManufacturers map for lookup
            val normalizedManufacturers = macManufacturers.mapKeys { normalizeMacAddress(it.key) ?: it.key }
            val manufacturer = normalizedManufacturers[normalizedMac]?.lowercase() ?: ""
            when {
                manufacturer.contains("vmware") -> return HypervisorType.VMWARE
                manufacturer.contains("parallels") -> return HypervisorType.PARALLELS
                manufacturer.contains("microsoft") -> return HypervisorType.HYPER_V
            }
        }

        // Check hostname patterns
        val hostname = device.hostname?.lowercase() ?: ""
        when {
            hostname.contains("vmware") || hostname.contains("esxi") -> return HypervisorType.VMWARE
            hostname.contains("virtualbox") -> return HypervisorType.VIRTUALBOX
            hostname.contains("kvm") || hostname.contains("qemu") -> return HypervisorType.KVM
            hostname.contains("hyper-v") || hostname.contains("hyperv") -> return HypervisorType.HYPER_V
            hostname.contains("parallels") -> return HypervisorType.PARALLELS
        }

        // Check MAC address patterns (common VM MAC prefixes) - normalize before checking
        device.macAddress?.let { mac ->
            val normalizedMac = normalizeMacAddress(mac) ?: return@let
            // Normalize prefixes for comparison (remove separators, uppercase)
            val normalizedPrefixes = mapOf(
                "000C29" to HypervisorType.VMWARE,
                "005056" to HypervisorType.VMWARE,
                "080027" to HypervisorType.VIRTUALBOX,
                "001C42" to HypervisorType.PARALLELS
            )
            
            // Check if normalized MAC starts with any normalized prefix
            val macWithoutSeparators = normalizedMac.replace(":", "")
            normalizedPrefixes.forEach { (prefix, type) ->
                if (macWithoutSeparators.startsWith(prefix)) {
                    return type
                }
            }
        }

        return HypervisorType.UNKNOWN
    }

    /**
     * Detects containers (Docker, LXC) based on hostname patterns.
     */
    suspend fun detectContainers(devices: List<NetworkDevice>): List<NetworkDevice> = withContext(Dispatchers.IO) {
        devices.filter { device ->
            val hostname = device.hostname?.lowercase() ?: ""
            hostname.contains("docker") || 
            hostname.contains("container") ||
            hostname.contains("lxc") ||
            hostname.matches(Regex("^[a-z0-9_-]+-[a-f0-9]{12}$")) // More restrictive pattern
        }
    }
}
