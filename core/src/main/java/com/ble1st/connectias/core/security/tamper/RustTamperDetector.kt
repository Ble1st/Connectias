package com.ble1st.connectias.core.security.tamper

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance tamper detector.
 * 
 * This class provides a JNI bridge to the Rust tamper detector implementation,
 * which offers better performance and security than Kotlin implementation.
 */
class RustTamperDetector(private val context: Context? = null) {
    
    companion object {
        init {
            try {
                System.loadLibrary("connectias_root_detector")
                Timber.d("Rust tamper detector library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load Rust tamper detector library")
                throw RuntimeException("Rust tamper detector library not available", e)
            }
        }
    }

    /**
     * Native method to detect tampering using Rust implementation.
     * Implementation is in Rust (libconnectias_root_detector.so), not C/C++.
     * 
     * @param packageNames Array of installed package names to check for hooking apps
     * @return JSON string with TamperDetectionResult
     */
    @Suppress("JNI_MISSING_IMPLEMENTATION")
    private external fun nativeDetectTampering(packageNames: Array<String>?): String

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
     * Detect tampering.
     * 
     * @return TamperDetectionResult with detection status and methods
     */
    suspend fun detectTampering(): TamperDetectionResult = withContext(Dispatchers.IO) {
        val rustStartTime = System.currentTimeMillis()
        
        try {
            // Check for hooking apps in Kotlin (package manager checks)
            val hookingAppMethods = mutableListOf<String>()
            if (context != null) {
                try {
                    val packageManager = context.packageManager
                    val hookingApps = listOf(
                        "de.robv.android.xposed.installer" to "Xposed Installer",
                        "org.meowcat.edxposed.manager" to "EdXposed Manager",
                        "org.lsposed.manager" to "LSPosed Manager",
                        "com.saurik.substrate" to "Substrate"
                    )
                    
                    hookingApps.forEach { (packageName, appName) ->
                        try {
                            packageManager.getPackageInfo(packageName, 0)
                            hookingAppMethods.add("Hooking app installed: $appName ($packageName)")
                        } catch (e: PackageManager.NameNotFoundException) {
                            // Package not found, continue
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to check hooking apps")
                }
            }

            Timber.d("🔴 [RustTamperDetector] Calling native Rust implementation (file system checks)")
            
            // Ensure Rust logging is initialized
            ensureInitialized()
            
            // Call native Rust implementation (file system checks only)
            // Package checks are done in Kotlin layer above
            // For now, we don't pass package names to Rust (Rust focuses on file system checks)
            val jsonResult = nativeDetectTampering(null)
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustTamperDetector] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResult = json.decodeFromString<RustTamperDetectionResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustTamperDetector] JSON parsing completed in ${parseDuration}ms")

            // Combine Rust results with Kotlin package checks
            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            val allMethods = rustResult.detection_methods + hookingAppMethods
            val isTampered = rustResult.is_tampered || hookingAppMethods.isNotEmpty()
            
            val result = TamperDetectionResult(
                isTampered = isTampered,
                detectionMethods = allMethods
            )
            
            Timber.i("🔴 [RustTamperDetector] Tamper detection completed in ${totalRustDuration}ms - Tampered: ${result.isTampered}, Methods: ${result.detectionMethods.size}")
            if (result.detectionMethods.isNotEmpty()) {
                Timber.w("⚠️ [RustTamperDetector] Tampering detected! Methods: ${result.detectionMethods.joinToString(", ")}")
            }
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustTamperDetector] Rust tamper detection failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
}

/**
 * Rust tamper detection result (matches Rust struct)
 */
@Serializable
private data class RustTamperDetectionResult(
    val is_tampered: Boolean,
    val detection_methods: List<String>
)

