package com.ble1st.connectias.feature.password.domain

import com.ble1st.connectias.feature.password.data.PasswordGeneratorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance password generator.
 * 
 * This class provides a JNI bridge to the Rust password generator implementation,
 * which offers better performance and security than Kotlin implementation.
 */
class RustPasswordGenerator {
    
    companion object {
        private var libraryLoaded = false
        init {
            try {
                System.loadLibrary("connectias_password_generator")
                libraryLoaded = true
                Timber.d("Rust password generator library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to load Rust password generator library, will use Kotlin fallback")
                libraryLoaded = false
            }
        }
    }

    /**
     * Native method to generate password using Rust implementation.
     * 
     * @param configJson JSON string with PasswordConfig
     * @return JSON string with PasswordResult
     */
    private external fun nativeGeneratePassword(configJson: String): String

    /**
     * Native method to analyze password strength using Rust implementation.
     * 
     * @param password Password to analyze
     * @return JSON string with PasswordAnalysisResult
     */
    private external fun nativeAnalyzePassword(password: String): String

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
     * Generate password.
     * 
     * @param config Password generation configuration
     * @return Generated password
     */
    suspend fun generatePassword(config: PasswordGeneratorConfig): String = withContext(Dispatchers.Default) {
        val rustStartTime = System.currentTimeMillis()
        
        if (!libraryLoaded) {
            Timber.w("⚠️ [RustPasswordGenerator] Rust library not loaded, cannot perform native generation.")
            throw RuntimeException("Rust library not loaded")
        }
        
        ensureInitialized()
        
        try {
            Timber.d("🔴 [RustPasswordGenerator] Calling native Rust implementation")
            
            // Serialize config to JSON
            val json = Json { ignoreUnknownKeys = true }
            val configJson = json.encodeToString(RustPasswordConfig.serializer(), RustPasswordConfig(
                length = config.length,
                include_lowercase = config.includeLowercase,
                include_uppercase = config.includeUppercase,
                include_digits = config.includeDigits,
                include_symbols = config.includeSymbols
            ))
            
            // Call native Rust implementation
            val jsonResult = nativeGeneratePassword(configJson)
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustPasswordGenerator] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val result = json.decodeFromString<RustPasswordResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustPasswordGenerator] JSON parsing completed in ${parseDuration}ms")

            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            Timber.i("🔴 [RustPasswordGenerator] Password generation completed in ${totalRustDuration}ms")
            
            result.password
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustPasswordGenerator] Rust password generation failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }

    /**
     * Analyze password strength.
     * 
     * @param password Password to analyze
     * @return Pair of PasswordStrength and score (0-100)
     */
    suspend fun analyzePasswordStrength(password: String): Pair<com.ble1st.connectias.feature.password.data.PasswordStrength, Int> = withContext(Dispatchers.Default) {
        val rustStartTime = System.currentTimeMillis()
        
        if (!libraryLoaded) {
            Timber.w("⚠️ [RustPasswordGenerator] Rust library not loaded, cannot perform native analysis.")
            throw RuntimeException("Rust library not loaded")
        }
        
        ensureInitialized()
        
        try {
            Timber.d("🔴 [RustPasswordGenerator] Calling native Rust implementation for analysis")
            
            // Call native Rust implementation
            val jsonResult = nativeAnalyzePassword(password)
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustPasswordGenerator] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val result = json.decodeFromString<RustPasswordAnalysisResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustPasswordGenerator] JSON parsing completed in ${parseDuration}ms")

            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            Timber.i("🔴 [RustPasswordGenerator] Password analysis completed in ${totalRustDuration}ms")
            
            val strength = when (result.strength) {
                "WEAK" -> com.ble1st.connectias.feature.password.data.PasswordStrength.WEAK
                "MEDIUM" -> com.ble1st.connectias.feature.password.data.PasswordStrength.MEDIUM
                "STRONG" -> com.ble1st.connectias.feature.password.data.PasswordStrength.STRONG
                "VERY_STRONG" -> com.ble1st.connectias.feature.password.data.PasswordStrength.VERY_STRONG
                else -> com.ble1st.connectias.feature.password.data.PasswordStrength.WEAK
            }
            
            Pair(strength, result.score)
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustPasswordGenerator] Rust password analysis failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
}

/**
 * Rust password config (matches Rust struct)
 */
@Serializable
private data class RustPasswordConfig(
    val length: Int,
    val include_lowercase: Boolean,
    val include_uppercase: Boolean,
    val include_digits: Boolean,
    val include_symbols: Boolean
)

/**
 * Rust password result (matches Rust struct)
 */
@Serializable
private data class RustPasswordResult(
    val password: String
)

/**
 * Rust password analysis result (matches Rust struct)
 */
@Serializable
private data class RustPasswordAnalysisResult(
    val score: Int,
    val strength: String
)

