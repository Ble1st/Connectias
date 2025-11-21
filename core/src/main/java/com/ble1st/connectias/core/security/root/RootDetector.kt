package com.ble1st.connectias.core.security.root

import android.os.Build
import java.io.File

data class RootDetectionResult(
    val isRooted: Boolean,
    val detectionMethods: List<String>
)

class RootDetector {
    fun detectRoot(): RootDetectionResult {
        val detectionMethods = mutableListOf<String>()

        // Check for su binary in common locations
        val suPaths = listOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/sbin/su",
            "/system/bin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )

        suPaths.forEach { path ->
            if (File(path).exists()) {
                detectionMethods.add("SU binary found at: $path")
            }
        }

        // Check for Magisk
        if (File("/data/adb/magisk").exists()) {
            detectionMethods.add("Magisk detected")
        }

        // Check for Xposed
        if (File("/system/framework/XposedBridge.jar").exists()) {
            detectionMethods.add("Xposed framework detected")
        }

        return RootDetectionResult(
            isRooted = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
    }
}

