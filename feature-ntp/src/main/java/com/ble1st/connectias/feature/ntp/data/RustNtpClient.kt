package com.ble1st.connectias.feature.ntp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance NTP client.
 * 
 * This class provides a JNI bridge to the Rust NTP client implementation,
 * which offers better performance than Apache Commons Net.
 */
class RustNtpClient {
    
    companion object {
        private var libraryLoaded = false
        
        init {
            try {
                System.loadLibrary("connectias_ntp")
                libraryLoaded = true
                Timber.d("Rust NTP client library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to load Rust NTP client library, will use Kotlin fallback")
                libraryLoaded = false
            }
        }
        
        fun isAvailable(): Boolean = libraryLoaded
    }

    /**
     * Native method to query NTP server using Rust implementation.
     * 
     * @param server NTP server hostname or IP
     * @return JSON string with NtpResult
     */
    private external fun nativeQueryNtp(server: String): String

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
                // Don't set isInitialized = true, so we don't keep trying
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize Rust logging (non-critical)")
                isInitialized = true // Set to true to avoid repeated attempts
            }
        }
    }

    /**
     * Query NTP server for time offset.
     * 
     * @param server NTP server hostname or IP
     * @return NtpResult with offset, delay, stratum, and reference ID
     */
    suspend fun queryOffset(server: String): NtpResult = withContext(Dispatchers.IO) {
        val rustStartTime = System.currentTimeMillis()
        
        if (!libraryLoaded) {
            throw UnsupportedOperationException("Rust NTP client library not available")
        }
        
        try {
            Timber.d("🔴 [RustNtpClient] Calling native Rust implementation")
            
            // Ensure Rust logging is initialized
            ensureInitialized()
            
            // Call native Rust implementation
            val jsonResult = nativeQueryNtp(server)
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustNtpClient] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResult = json.decodeFromString<RustNtpResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustNtpClient] JSON parsing completed in ${parseDuration}ms")

            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            
            val result = NtpResult(
                server = rustResult.server,
                offsetMs = rustResult.offset_ms,
                delayMs = rustResult.delay_ms,
                stratum = rustResult.stratum.toInt(),
                referenceId = rustResult.reference_id,
                error = rustResult.error
            )
            
            Timber.i("🔴 [RustNtpClient] NTP query completed in ${totalRustDuration}ms - Server: ${result.server}, Offset: ${result.offsetMs}ms")
            if (result.error != null) {
                Timber.w("⚠️ [RustNtpClient] NTP query error: ${result.error}")
            }
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustNtpClient] Rust NTP query failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
}

/**
 * Rust NTP result (matches Rust struct)
 */
@Serializable
private data class RustNtpResult(
    val server: String,
    val offset_ms: Long,
    val delay_ms: Long,
    val stratum: UByte,
    val reference_id: String,
    val error: String? = null
)

