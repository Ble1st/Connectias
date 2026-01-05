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
 */
class DebuggerDetector {
    
    /**
     * Detects debugger attachment using multiple methods.
     * This is a suspend function that performs I/O on Dispatchers.IO to avoid blocking.
     * 
     * @return DebuggerDetectionResult with detection status and methods
     */
    suspend fun detectDebugger(): DebuggerDetectionResult = withContext(Dispatchers.IO) {
        val detectionMethods = mutableListOf<String>()
        
        // 1. Check if debugger is attached via Android API
        // Debug.isDebuggerConnected() does not throw exceptions, no try-catch needed
        if (Debug.isDebuggerConnected()) {
            detectionMethods.add("Debugger connected via Debug.isDebuggerConnected()")
        }
        
        // 2. Check TracerPid in /proc/self/status
        checkTracerPid(detectionMethods)
        
        return@withContext DebuggerDetectionResult(
            isDebuggerAttached = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
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
