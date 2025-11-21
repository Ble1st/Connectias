package com.ble1st.connectias.core.security.emulator

import android.os.Build
import java.io.File

data class EmulatorDetectionResult(
    val isEmulator: Boolean,
    val detectionMethods: List<String>
)

class EmulatorDetector {
    fun detectEmulator(): EmulatorDetectionResult {
        val detectionMethods = mutableListOf<String>()

        // Check for emulator-specific properties
        val emulatorProperties = mapOf(
            "ro.kernel.qemu" to "QEMU kernel detected",
            "ro.hardware" to "Hardware check",
            "ro.product.model" to "Product model check"
        )

        // Check Build properties
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()

        if (model.contains("sdk") || model.contains("emulator")) {
            detectionMethods.add("Emulator model detected: $model")
        }

        if (manufacturer.contains("unknown") || manufacturer.contains("generic")) {
            detectionMethods.add("Generic manufacturer detected: $manufacturer")
        }

        if (product.contains("sdk") || product.contains("emulator")) {
            detectionMethods.add("Emulator product detected: $product")
        }

        // Check for QEMU files
        if (File("/system/lib/libqemu.so").exists()) {
            detectionMethods.add("QEMU library detected")
        }

        return EmulatorDetectionResult(
            isEmulator = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
    }
}

