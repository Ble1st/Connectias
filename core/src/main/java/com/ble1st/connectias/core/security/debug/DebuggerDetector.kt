package com.ble1st.connectias.core.security.debug

import android.os.Debug
import java.io.File

data class DebuggerDetectionResult(
    val isDebuggerAttached: Boolean,
    val detectionMethods: List<String>
)

class DebuggerDetector {
    fun detectDebugger(): DebuggerDetectionResult {
        val detectionMethods = mutableListOf<String>()

        // Check if debugger is attached
        if (Debug.isDebuggerConnected()) {
            detectionMethods.add("Debugger connected via Debug.isDebuggerConnected()")
        }

        // Check TracerPid in /proc/self/status
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
        } catch (e: Exception) {
            // Ignore
        }

        return DebuggerDetectionResult(
            isDebuggerAttached = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
    }
}

