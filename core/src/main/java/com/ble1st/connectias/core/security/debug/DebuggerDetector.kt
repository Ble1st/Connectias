package com.ble1st.connectias.core.security.debug

import android.os.Debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Detects if a debugger is attached to the application.
 * 
 * This detector uses:
 * - Android Debug API (Debug.isDebuggerConnected())
 * - /proc/self/status TracerPid check
 * 
 * Note: This is a suspend function that performs blocking I/O on Dispatchers.IO.
 * Must be called from a coroutine context.
 * 
 * Uses Rust implementation (primary) with fallback to Kotlin implementation.
 */
class DebuggerDetector {
    
    private val rustDetector = try {
        RustDebuggerDetector()
    } catch (e: Exception) {
        null // Fallback to Kotlin if Rust not available
    }
    
    /**
     * Detects debugger attachment using Rust implementation (primary) with fallback to Kotlin.
     * This is a suspend function that performs I/O on Dispatchers.IO to avoid blocking.
     * 
     * @return DebuggerDetectionResult with detection status and methods
     */
    suspend fun detectDebugger(): DebuggerDetectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster and more secure)
        if (rustDetector != null) {
            try {
                Timber.i("🔴 [DebuggerDetector] Using RUST implementation")
                val rustStartTime = System.currentTimeMillis()
                
                val result = rustDetector.detectDebugger()
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [DebuggerDetector] RUST detection completed - Attached: ${result.isDebuggerAttached}, Methods: ${result.detectionMethods.size} | Duration: ${rustDuration}ms")
                Timber.d("📊 [DebuggerDetector] Total time (including overhead): ${totalDuration}ms")
                
                return@withContext result
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [DebuggerDetector] RUST detection failed after ${rustDuration}ms, falling back to Kotlin")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [DebuggerDetector] Rust detector not available, using Kotlin")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [DebuggerDetector] Using KOTLIN implementation")
        val kotlinStartTime = System.currentTimeMillis()
        
        val detectionMethods = mutableListOf<String>()
        
        // 1. Check if debugger is attached via Android API
        // Debug.isDebuggerConnected() does not throw exceptions, no try-catch needed
        if (Debug.isDebuggerConnected()) {
            detectionMethods.add("Debugger connected via Debug.isDebuggerConnected()")
        }
        
        // 2. Check TracerPid in /proc/self/status
        checkTracerPid(detectionMethods)
        
        val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
        val totalDuration = System.currentTimeMillis() - startTime
        
        val result = DebuggerDetectionResult(
            isDebuggerAttached = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
        
        Timber.i("✅ [DebuggerDetector] KOTLIN detection completed - Attached: ${result.isDebuggerAttached}, Methods: ${result.detectionMethods.size} | Duration: ${kotlinDuration}ms")
        Timber.d("📊 [DebuggerDetector] Total time (including overhead): ${totalDuration}ms")
        
        return@withContext result
    }
    
    /**
     * Checks TracerPid in /proc/self/status.
     * A non-zero TracerPid indicates a debugger is attached.
     */
    private fun checkTracerPid(detectionMethods: MutableList<String>) {
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                val statusContent = statusFile.readText()
                val tracerPidLine = statusContent.lines().find { it.startsWith("TracerPid:") }
                tracerPidLine?.let { line ->
                    val tracerPid = line.split(":").getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    if (tracerPid != 0) {
                        detectionMethods.add("TracerPid detected: $tracerPid")
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission denied, silently ignore
        } catch (e: Exception) {
            Timber.w(e, "Error reading /proc/self/status")
        }
    }
}

data class DebuggerDetectionResult(
    val isDebuggerAttached: Boolean,
    val detectionMethods: List<String>
)
