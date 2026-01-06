package com.ble1st.connectias.core.security.debug

import android.os.Debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Rust-based high-performance debugger detector.
 * 
 * This class provides a JNI bridge to the Rust debugger detector implementation,
 * which offers better performance and security than Kotlin implementation.
 * 
 * Note: Android Debug API (Debug.isDebuggerConnected()) is checked in Kotlin layer.
 */
class RustDebuggerDetector {
    
    companion object {
        init {
            try {
                System.loadLibrary("connectias_root_detector")
                Timber.d("Rust debugger detector library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load Rust debugger detector library")
                throw RuntimeException("Rust debugger detector library not available", e)
            }
        }
    }

    /**
     * Native method to detect debugger using Rust implementation.
     * 
     * @return JSON string with DebuggerDetectionResult
     */
    private external fun nativeDetectDebugger(): String

    /**
     * Initialize Rust logging (Android-specific)
     * Note: This is called lazily when needed, not in init block
     */
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
     * Detect debugger attachment.
     * 
     * @return DebuggerDetectionResult with detection status and methods
     */
    suspend fun detectDebugger(): DebuggerDetectionResult = withContext(Dispatchers.IO) {
        val rustStartTime = System.currentTimeMillis()
        
        try {
            // Check Android Debug API in Kotlin (cannot be done via JNI)
            val androidApiMethods = mutableListOf<String>()
            if (Debug.isDebuggerConnected()) {
                androidApiMethods.add("Debugger connected via Debug.isDebuggerConnected()")
            }

            Timber.d("🔴 [RustDebuggerDetector] Calling native Rust implementation (/proc/self/status)")
            
            // Ensure Rust logging is initialized
            ensureInitialized()
            
            // Call native Rust implementation (TracerPid check)
            val jsonResult = nativeDetectDebugger()
            
            val rustNativeDuration = System.currentTimeMillis() - rustStartTime
            Timber.d("🔴 [RustDebuggerDetector] Native call completed in ${rustNativeDuration}ms")

            // Parse JSON response
            val parseStartTime = System.currentTimeMillis()
            val json = Json { ignoreUnknownKeys = true }
            val rustResult = json.decodeFromString<RustDebuggerDetectionResult>(jsonResult)
            val parseDuration = System.currentTimeMillis() - parseStartTime
            
            Timber.d("🔴 [RustDebuggerDetector] JSON parsing completed in ${parseDuration}ms")

            // Combine Rust results with Kotlin Android API checks
            val totalRustDuration = System.currentTimeMillis() - rustStartTime
            val allMethods = rustResult.detection_methods + androidApiMethods
            val isDebuggerAttached = rustResult.is_debugger_attached || androidApiMethods.isNotEmpty()
            
            val result = DebuggerDetectionResult(
                isDebuggerAttached = isDebuggerAttached,
                detectionMethods = allMethods
            )
            
            Timber.i("🔴 [RustDebuggerDetector] Debugger detection completed in ${totalRustDuration}ms - Attached: ${result.isDebuggerAttached}, Methods: ${result.detectionMethods.size}")
            if (result.detectionMethods.isNotEmpty()) {
                Timber.w("⚠️ [RustDebuggerDetector] Debugger detected! Methods: ${result.detectionMethods.joinToString(", ")}")
            }
            
            result
        } catch (e: Exception) {
            val rustDuration = System.currentTimeMillis() - rustStartTime
            Timber.e(e, "❌ [RustDebuggerDetector] Rust debugger detection failed after ${rustDuration}ms")
            // Fallback to Kotlin implementation
            throw e
        }
    }
}

/**
 * Rust debugger detection result (matches Rust struct)
 */
@Serializable
private data class RustDebuggerDetectionResult(
    val is_debugger_attached: Boolean,
    val detection_methods: List<String>
)

