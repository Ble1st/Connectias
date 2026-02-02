@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.ble1st.connectias.core.security.emulator

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.ble1st.connectias.core.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance emulator detector.
 * 
 * This class provides a JNI bridge to the Rust emulator detector implementation,
 * which offers better performance and security than Kotlin implementation.
 * 
 * Note: Telephony checks are done in Kotlin layer (Android API).
 */
class RustEmulatorDetector(private val context: Context? = null) {
    
    companion object {
        init {
            try {
                System.loadLibrary("connectias_root_detector")
                Timber.d("Rust emulator detector library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load Rust emulator detector library")
                throw RuntimeException("Rust emulator detector library not available", e)
            }
        }
    }

    /**
     * Native method to detect emulator using Rust implementation.
     * Implementation is in Rust (libconnectias_root_detector.so), not C/C++.
     * 
     * @param model Build.MODEL
     * @param manufacturer Build.MANUFACTURER
     * @param product Build.PRODUCT
     * @param device Build.DEVICE
     * @param hardware Build.HARDWARE
     * @param brand Build.BRAND
     * @param fingerprint Build.FINGERPRINT
     * @return JSON string with EmulatorDetectionResult
     */
    @Suppress("JNI_MISSING_IMPLEMENTATION")
    private external fun nativeDetectEmulator(
        model: String,
        manufacturer: String,
        product: String,
        device: String,
        hardware: String,
        brand: String,
        fingerprint: String
    ): String

    /**
     * Initialize Rust logging (Android-specific)
     * Implementation is in Rust (libconnectias_root_detector.so), not C/C++.
     * Note: This is called lazily when needed, not in init block
     */
    @Suppress("JNI_MISSING_IMPLEMENTATION")
    private external fun nativeInit()
    
    private var isInitialized = false
    
    private fun ensureInitialized() {
        if (!isInitialized) {
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
     * Detect emulator.
     * 
     * @return EmulatorDetectionResult with detection status and methods
     */
    suspend fun detectEmulator(): EmulatorDetectionResult = withContext(Dispatchers.IO) {
        val rustStartTime = System.currentTimeMillis()
        
        try {
            // Check telephony in Kotlin (Android API)
            val telephonyMethods = mutableListOf<String>()
            if (context != null) {
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    telephonyManager?.let { tm ->
                        val phoneType = tm.phoneType
                        if (phoneType == TelephonyManager.PHONE_TYPE_NONE) {
                            telephonyMethods.add("No telephony support (PHONE_TYPE_NONE)")
                        }
                    }
                } catch (e: Exception) {
                    // Ignore telephony check errors
                }
            }

            Timber.d("🔴 [RustEmulatorDetector] Calling native Rust implementation")
            
            // Ensure Rust logging is initialized
            ensureInitialized()
            
            // Call native Rust implementation with Build properties
            val jsonResult = nativeDetectEmulator(
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                product = Build.PRODUCT,
                device = Build.DEVICE,
                hardware = Build.HARDWARE,
                brand = Build.BRAND,
                fingerprint = Build.FINGERPRINT
            )
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustEmulatorDetector] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResult = json.decodeFromString<RustEmulatorDetectionResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustEmulatorDetector] JSON parsing completed in ${parseDuration}ms")

            // Combine Rust results with Kotlin telephony checks
            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            val allMethods = rustResult.detection_method_names + telephonyMethods
            val isEmulator = rustResult.is_emulator || telephonyMethods.isNotEmpty()
            
            // Build debug details (only for debug builds, contains PII)
            val debugDetails = if (BuildConfig.DEBUG) {
                buildDebugDetails()
            } else {
                null
            }
            
            val result = EmulatorDetectionResult(
                isEmulator = isEmulator,
                detectionMethodNames = allMethods,
                debugDetails = debugDetails
            )
            
            Timber.i("🔴 [RustEmulatorDetector] Emulator detection completed in ${totalRustDuration}ms - Emulator: ${result.isEmulator}, Methods: ${result.detectionMethodNames.size}")
            if (result.detectionMethodNames.isNotEmpty()) {
                Timber.w("⚠️ [RustEmulatorDetector] Emulator detected! Methods: ${result.detectionMethodNames.joinToString(", ")}")
            }
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustEmulatorDetector] Rust emulator detection failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
    
    /**
     * Builds debug details map containing PII (only for debug builds).
     * This should never be logged or transmitted in production.
     */
    private fun buildDebugDetails(): Map<String, String> {
        val details = mutableMapOf<String, String>()
        
        try {
            details["build_model"] = Build.MODEL
            details["build_manufacturer"] = Build.MANUFACTURER
            details["build_product"] = Build.PRODUCT
            details["build_device"] = Build.DEVICE
            details["build_hardware"] = Build.HARDWARE
            details["build_brand"] = Build.BRAND
            details["build_fingerprint"] = Build.FINGERPRINT
            
            // System properties are read in Rust, but we can add them here for debug
            // Note: This uses reflection to access android.os.SystemProperties (internal API).
            // This is necessary for emulator detection debug details as there's no public API
            // for system properties. The reflection is wrapped in try-catch to handle cases
            // where the API is unavailable. Only used in debug builds.
            @SuppressLint("PrivateApi")
            try {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java)
                
                val qemu = getMethod.invoke(null, "ro.kernel.qemu") as? String
                details["ro.kernel.qemu"] = qemu ?: "null"
                
                val hardware = getMethod.invoke(null, "ro.hardware") as? String
                details["ro.hardware"] = hardware ?: "null"
                
                val productModel = getMethod.invoke(null, "ro.product.model") as? String
                details["ro.product.model"] = productModel ?: "null"
            } catch (e: Exception) {
                // Ignore reflection errors
            }
        } catch (e: Exception) {
            Timber.w(e, "Error building debug details")
        }
        
        return details
    }
}

/**
 * Rust emulator detection result (matches Rust struct)
 */
@Serializable
private data class RustEmulatorDetectionResult(
    val is_emulator: Boolean,
    val detection_method_names: List<String>
)

