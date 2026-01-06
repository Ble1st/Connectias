package com.ble1st.connectias.core.security.root

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance root detector.
 * 
 * This class provides a JNI bridge to the Rust root detector implementation,
 * which offers better performance and security than RootBeer library.
 */
class RustRootDetector(private val context: Context? = null) {
    
    companion object {
        init {
            try {
                System.loadLibrary("connectias_root_detector")
                Timber.d("Rust root detector library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load Rust root detector library")
                throw RuntimeException("Rust root detector library not available", e)
            }
        }
    }

    /**
     * Native method to detect root using Rust implementation.
     * 
     * @param packageNames Array of installed package names to check for root apps
     * @return JSON string with RootDetectionResult
     */
    private external fun nativeDetectRoot(packageNames: Array<String>?): String

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
     * Detect root access.
     * 
     * @return RootDetectionResult with detection status and methods
     */
    suspend fun detectRoot(): RootDetectionResult = withContext(Dispatchers.IO) {
        val rustStartTime = System.currentTimeMillis()
        
        try {
            // Check for root apps in Kotlin (package manager checks)
            val rootAppMethods = mutableListOf<String>()
            if (context != null) {
                try {
                    val packageManager = context.packageManager
                    val rootAppPackages = listOf(
                        "com.noshufou.android.su",
                        "com.noshufou.android.su.elite",
                        "eu.chainfire.supersu",
                        "com.koushikdutta.superuser",
                        "com.thirdparty.superuser",
                        "com.yellowes.su",
                        "com.topjohnwu.magisk",
                        "com.topjohnwu.magisk.debug",
                        "com.kingroot.kinguser",
                        "com.kingo.root",
                        "de.robv.android.xposed.installer",
                        "org.meowcat.edxposed.manager",
                        "org.lsposed.manager"
                    )
                    
                    rootAppPackages.forEach { packageName ->
                        try {
                            packageManager.getPackageInfo(packageName, 0)
                            rootAppMethods.add("Root management app detected: $packageName")
                        } catch (e: PackageManager.NameNotFoundException) {
                            // Package not found, continue
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to check root apps")
                }
            }

            Timber.d("🔴 [RustRootDetector] Calling native Rust implementation (file system checks)")
            
            // Call native Rust implementation (file system checks only)
            // Package checks are done in Kotlin layer above
            val jsonResult = nativeDetectRoot(null)
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustRootDetector] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResult = json.decodeFromString<RustRootDetectionResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustRootDetector] JSON parsing completed in ${parseDuration}ms")

            // Combine Rust results with Kotlin package checks
            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            val allMethods = rustResult.detection_methods + rootAppMethods
            val isRooted = rustResult.is_rooted || rootAppMethods.isNotEmpty()
            
            val result = RootDetectionResult(
                isRooted = isRooted,
                detectionMethods = allMethods
            )
            
            Timber.i("🔴 [RustRootDetector] Root detection completed in ${totalRustDuration}ms - Rooted: ${result.isRooted}, Methods: ${result.detectionMethods.size}")
            if (result.detectionMethods.isNotEmpty()) {
                Timber.w("⚠️ [RustRootDetector] Root detected! Methods: ${result.detectionMethods.joinToString(", ")}")
            }
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustRootDetector] Rust root detection failed after ${rustDuration}ms")
            // Fallback to RootBeer
            throw e
        }
    }
}

/**
 * Rust root detection result (matches Rust struct)
 */
@Serializable
private data class RustRootDetectionResult(
    val is_rooted: Boolean,
    val detection_methods: List<String>
)

