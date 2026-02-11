package com.ble1st.connectias.feature.dnstools.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.xbill.DNS.Type
import timber.log.Timber

/**
 * Rust-based high-performance DNS resolver.
 * 
 * This class provides a JNI bridge to the Rust DNS resolver implementation,
 * which offers better performance than dnsjava.
 */
class RustDnsResolver {
    
    companion object {
        private var libraryLoaded = false
        init {
            try {
                System.loadLibrary("connectias_dns_tools")
                libraryLoaded = true
                Timber.d("Rust DNS resolver library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to load Rust DNS resolver library, will use Kotlin fallback")
                libraryLoaded = false
            }
        }
    }

    /**
     * Native method to resolve DNS using Rust implementation.
     * 
     * @param domain Domain name to resolve
     * @param dnsType DNS record type (Type.A, Type.AAAA, etc.)
     * @param nameserver DNS nameserver IP (e.g., "8.8.8.8")
     * @return JSON string with DnsQueryResult
     */
    private external fun nativeResolveDns(domain: String, dnsType: Int, nameserver: String): String

    /**
     * Initialize Rust logging (Android-specific)
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
     * Resolve DNS query.
     * 
     * @param domain Domain name to resolve
     * @param type DNS record type (default: Type.A)
     * @param nameserver DNS nameserver IP (default: "8.8.8.8")
     * @return DnsQueryResult with query results
     */
    suspend fun resolveDns(domain: String, type: Int = Type.A, nameserver: String = "8.8.8.8"): DnsQueryResult = withContext(Dispatchers.IO) {
        val rustStartTime = System.currentTimeMillis()
        
        if (!libraryLoaded) {
            Timber.w("⚠️ [RustDnsResolver] Rust library not loaded, cannot perform native resolution.")
            throw RuntimeException("Rust library not loaded")
        }
        
        ensureInitialized()
        
        try {
            Timber.d("🔴 [RustDnsResolver] Calling native Rust implementation")
            
            // Call native Rust implementation
            val jsonResult = nativeResolveDns(domain, type, nameserver)
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustDnsResolver] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResult = json.decodeFromString<RustDnsQueryResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustDnsResolver] JSON parsing completed in ${parseDuration}ms")

            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            
            val result = DnsQueryResult(
                domain = rustResult.domain,
                type = rustResult.type,
                records = rustResult.records,
                error = rustResult.error
            )
            
            Timber.i("🔴 [RustDnsResolver] DNS resolution completed in ${totalRustDuration}ms - Domain: ${result.domain}, Records: ${result.records.size}")
            if (result.error != null) {
                Timber.w("⚠️ [RustDnsResolver] DNS resolution error: ${result.error}")
            }
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustDnsResolver] Rust DNS resolution failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
}

/**
 * Rust DNS query result (matches Rust struct)
 */
@Serializable
private data class RustDnsQueryResult(
    val domain: String,
    @SerialName("type")
    val type: String,
    val records: List<String>,
    val error: String? = null
)

