package com.ble1st.connectias.core.security.tamper

import java.io.File

data class TamperDetectionResult(
    val isTampered: Boolean,
    val detectionMethods: List<String>
)

class TamperDetector {
    fun detectTampering(): TamperDetectionResult {
        val detectionMethods = mutableListOf<String>()

        // Check for hook frameworks
        val hookIndicators = listOf(
            "/system/xbin/xposed" to "Xposed framework detected",
            "/system/lib/libxposed_art.so" to "Xposed library (32-bit) detected",
            "/system/lib64/libxposed_art.so" to "Xposed library (64-bit) detected"
        )

        hookIndicators.forEach { (path, message) ->
            if (File(path).exists()) {
                detectionMethods.add(message)
            }
        }

        return TamperDetectionResult(
            isTampered = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
    }
}

