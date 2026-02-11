package com.ble1st.connectias.feature.network.network

import com.ble1st.connectias.feature.network.model.DeviceType
import com.ble1st.connectias.feature.network.model.HostInfo
import com.ble1st.connectias.feature.network.model.NetworkEnvironment
import com.ble1st.connectias.feature.network.model.deviceTypeFromHostname
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance network scanner.
 * 
 * This class provides a JNI bridge to the Rust network scanner implementation,
 * which offers better performance than Kotlin implementation.
 */
class RustNetworkScanner {
    
    companion object {
        private var libraryLoaded = false
        init {
            try {
                System.loadLibrary("connectias_port_scanner")
                libraryLoaded = true
                Timber.d("Rust network scanner library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to load Rust network scanner library, will use Kotlin fallback")
                libraryLoaded = false
            }
        }
    }

    /**
     * Native method to scan hosts using Rust implementation.
     * 
     * @param cidr CIDR notation (e.g., "192.168.1.0/24")
     * @param timeoutMs Timeout in milliseconds per host
     * @param maxConcurrency Maximum concurrent scans
     * @param maxHosts Maximum number of hosts to scan
     * @return JSON string with array of HostScanResult
     */
    private external fun nativeScanHosts(
        cidr: String,
        timeoutMs: Long,
        maxConcurrency: Int,
        maxHosts: Int
    ): String

    /**
     * Native method to read ARP entry using Rust implementation.
     * 
     * @param ip IP address
     * @return MAC address or empty string
     */
    private external fun nativeReadArpEntry(ip: String): String

    /**
     * Initialize Rust logging (Android-specific)
     * Note: This is called lazily when needed, not in init block
     */
    private external fun nativeInit()
    
    private var isInitialized = false
    
    private fun ensureInitialized() {
        if (!isInitialized && libraryLoaded) {
            try {
                nativeInit()
                isInitialized = true
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to initialize Rust logging (non-critical) - function not found")
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize Rust logging (non-critical)")
            }
        }
    }

    /**
     * Scan hosts in network.
     * 
     * @param environment Network environment with CIDR
     * @param timeoutMs Timeout in milliseconds per host
     * @param maxConcurrency Maximum concurrent scans
     * @param maxHosts Maximum number of hosts to scan
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return List of HostInfo for reachable hosts
     */
    suspend fun scanHosts(
        environment: NetworkEnvironment,
        timeoutMs: Int = 400,
        maxConcurrency: Int = 64,
        maxHosts: Int = 512,
        onProgress: (Float) -> Unit = {}
    ): List<HostInfo> = withContext(Dispatchers.IO) {
        val rustStartTime = System.currentTimeMillis()
        
        if (!libraryLoaded) {
            Timber.w("⚠️ [RustNetworkScanner] Rust library not loaded, cannot perform native scan.")
            throw RuntimeException("Rust library not loaded")
        }
        
        try {
            Timber.d("🔴 [RustNetworkScanner] Calling native Rust implementation")
            
            // Ensure Rust logging is initialized
            ensureInitialized()
            
            // Call native Rust implementation
            val jsonResult = nativeScanHosts(
                environment.cidr,
                timeoutMs.toLong(),
                maxConcurrency,
                maxHosts
            )
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustNetworkScanner] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResults = json.decodeFromString<List<RustHostScanResult>>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustNetworkScanner] JSON parsing completed in ${parseDuration}ms")

            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            
            // Convert to HostInfo
            val results = rustResults.map { rustResult ->
                HostInfo(
                    ip = rustResult.ip,
                    hostname = rustResult.hostname,
                    mac = rustResult.mac,
                    deviceType = deviceTypeFromHostname(rustResult.hostname),
                    isReachable = rustResult.is_reachable,
                    pingMs = rustResult.ping_ms
                )
            }
            
            Timber.i("🔴 [RustNetworkScanner] Network scan completed in ${totalRustDuration}ms - Found ${results.size} hosts")
            
            onProgress(1.0f)
            results
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustNetworkScanner] Rust network scan failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }

    /**
     * Read ARP entry for IP address.
     * 
     * @param ip IP address
     * @return MAC address or null
     */
    suspend fun readArpEntry(ip: String): String? = withContext(Dispatchers.IO) {
        if (!libraryLoaded) {
            Timber.w("⚠️ [RustNetworkScanner] Rust library not loaded, cannot read ARP entry.")
            return@withContext null
        }
        
        try {
            // Ensure Rust logging is initialized
            ensureInitialized()
            
            val mac = nativeReadArpEntry(ip)
            if (mac.isEmpty()) null else mac
        } catch (e: Exception) {
            Timber.w(e, "Failed to read ARP entry for $ip")
            null
        }
    }
}

/**
 * Rust host scan result (matches Rust struct)
 */
@Serializable
private data class RustHostScanResult(
    val ip: String,
    val hostname: String? = null,
    val mac: String? = null,
    val device_type: String,
    val is_reachable: Boolean,
    val ping_ms: Long? = null
)

