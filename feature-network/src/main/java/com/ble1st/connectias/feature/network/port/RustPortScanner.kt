package com.ble1st.connectias.feature.network.port

import com.ble1st.connectias.feature.network.model.PortResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance port scanner.
 * 
 * This class provides a JNI bridge to the Rust port scanner implementation,
 * which offers significantly better performance than the Kotlin implementation,
 * especially for large port ranges.
 */
class RustPortScanner {
    
    companion object {
        init {
            try {
                System.loadLibrary("connectias_port_scanner")
                Timber.d("Rust port scanner library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load Rust port scanner library")
                throw RuntimeException("Rust port scanner library not available", e)
            }
        }
    }

    /**
     * Native method to scan ports using Rust implementation.
     * 
     * @param host Target hostname or IP address
     * @param startPort Start port (1-65535)
     * @param endPort End port (1-65535, must be >= startPort)
     * @param timeoutMs Connection timeout in milliseconds
     * @param maxConcurrency Maximum concurrent connections
     * @return JSON string with scan results
     */
    private external fun nativeScanPorts(
        host: String,
        startPort: Int,
        endPort: Int,
        timeoutMs: Long,
        maxConcurrency: Int
    ): String

    /**
     * Initialize Rust logging (Android-specific)
     */
    private external fun nativeInit()

    init {
        try {
            nativeInit()
        } catch (e: Exception) {
            Timber.w(e, "Failed to initialize Rust logging (non-critical)")
        }
    }

    /**
     * Scan a range of ports.
     * 
     * @param host Target hostname or IP address
     * @param startPort Start port
     * @param endPort End port
     * @param timeoutMs Connection timeout in milliseconds (default: 200)
     * @param maxConcurrency Maximum concurrent connections (default: 128)
     * @return List of open ports with their results
     */
    suspend fun scan(
        host: String,
        startPort: Int,
        endPort: Int,
        timeoutMs: Int = 200,
        maxConcurrency: Int = 128
    ): List<PortResult> = withContext(Dispatchers.IO) {
        val rustNativeStartTime = System.currentTimeMillis()
        
        try {
            // Validate input
            if (startPort < 1 || endPort > 65535 || endPort < startPort) {
                throw IllegalArgumentException("Invalid port range $startPort-$endPort")
            }

            Timber.d("🔴 [RustPortScanner] Calling native Rust implementation - Host: $host, Ports: $startPort-$endPort, Timeout: ${timeoutMs}ms, Concurrency: $maxConcurrency")
            
            // Call native Rust implementation
            val jsonResult = nativeScanPorts(
                host = host,
                startPort = startPort,
                endPort = endPort,
                timeoutMs = timeoutMs.toLong(),
                maxConcurrency = maxConcurrency
            )
            
            val rustNativeDuration = System.currentTimeMillis() - rustNativeStartTime
            Timber.d("🔴 [RustPortScanner] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResults = json.decodeFromString<List<RustPortScanResult>>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustPortScanner] JSON parsing completed in ${parseDuration}ms - ${rustResults.size} results")

            // Convert to Kotlin PortResult
            val totalRustDuration = System.currentTimeMillis() - rustNativeStartTime
            val results = rustResults.map { rustResult ->
                PortResult(
                    port = rustResult.port,
                    isOpen = rustResult.is_open,
                    service = rustResult.service,
                    banner = rustResult.banner
                )
            }
            
            Timber.d("🔴 [RustPortScanner] Total Rust processing time: ${totalRustDuration}ms (Native: ${rustNativeDuration}ms, Parse: ${parseDuration}ms)")
            
            results
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustNativeStartTime
            Timber.e(e, "❌ [RustPortScanner] Rust port scan failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
}

/**
 * Rust port scan result (matches Rust struct)
 */
@Serializable
private data class RustPortScanResult(
    val port: Int,
    val is_open: Boolean,
    val service: String? = null,
    val banner: String? = null
)

