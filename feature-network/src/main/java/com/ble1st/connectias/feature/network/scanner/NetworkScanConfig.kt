package com.ble1st.connectias.feature.network.scanner

/**
 * Configuration for network scanning operations.
 * Allows customization of scan behavior for performance and resource management.
 */
data class NetworkScanConfig(
    /**
     * Maximum number of IP addresses to scan in a subnet.
     * Default: 254 (full /24 subnet)
     */
    val maxHostsToScan: Int = 254,
    
    /**
     * Timeout in milliseconds for each individual host check.
     * Default: 1000ms
     */
    val hostTimeoutMs: Int = 1000,
    
    /**
     * Maximum number of parallel scan operations.
     * Default: 50 (to avoid overwhelming the system)
     */
    val maxParallelScans: Int = 50,
    
    /**
     * Stop scanning after finding this many devices.
     * If null, scan all hosts in range.
     * Default: null (scan all)
     */
    val stopAfterDevicesFound: Int? = null,
    
    /**
     * Whether to perform reverse DNS lookup for hostnames.
     * Can be slow but provides more information.
     * Default: false
     */
    val performReverseDns: Boolean = false
) {
    init {
        require(maxHostsToScan > 0) { "maxHostsToScan must be positive" }
        require(hostTimeoutMs > 0) { "hostTimeoutMs must be positive" }
        require(maxParallelScans > 0) { "maxParallelScans must be positive" }
        stopAfterDevicesFound?.let {
            require(it > 0) { "stopAfterDevicesFound must be positive if set" }
        }
    }
    
    companion object {
        /**
         * Default configuration for standard network scans.
         */
        val DEFAULT = NetworkScanConfig()
        
        /**
         * Fast configuration for quick scans (fewer hosts, shorter timeout).
         */
        val FAST = NetworkScanConfig(
            maxHostsToScan = 64,
            hostTimeoutMs = 500,
            maxParallelScans = 30,
            stopAfterDevicesFound = 10
        )
        
        /**
         * Thorough configuration for complete scans (all hosts, longer timeout).
         */
        val THOROUGH = NetworkScanConfig(
            maxHostsToScan = 254,
            hostTimeoutMs = 2000,
            maxParallelScans = 100,
            performReverseDns = true
        )
    }
}

