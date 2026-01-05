package com.ble1st.connectias.core.security.emulator

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.ble1st.connectias.core.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Detects if the device is running in an emulator environment.
 * Uses multiple heuristics including Build properties, system properties,
 * file system checks, hardware sensors, and telephony information.
 * 
 * Note: This is a client-side heuristic detector. For production security,
 * consider integrating Google Play Integrity API or SafetyNet attestation
 * and performing server-side verification of attestation tokens combined
 * with these client-side signals.
 */
class EmulatorDetector(private val context: Context? = null) {
    
    /**
     * Detects emulator using multiple heuristics.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun detectEmulator(): EmulatorDetectionResult = withContext(Dispatchers.IO) {
        val detectionMethods = mutableListOf<String>()
        
        // 1. Check Build properties
        checkBuildProperties(detectionMethods)
        
        // 3. Check system properties via reflection
        checkSystemProperties(detectionMethods)
        
        // 4. Check for emulator-specific files
        checkEmulatorFiles(detectionMethods)
        
        // 5. Check telephony (emulators often lack proper telephony)
        if (context != null) {
            checkTelephony(detectionMethods, context)
        }
        
        // 6. Check CPU/ABI anomalies
        checkCPUABI(detectionMethods)
        
        // Build debug details map (only for debug builds, contains PII)
        val debugDetails = if (BuildConfig.DEBUG) {
            buildDebugDetails()
        } else {
            null
        }
        
        return@withContext EmulatorDetectionResult(
            isEmulator = detectionMethods.isNotEmpty(),
            detectionMethodNames = detectionMethods,
            debugDetails = debugDetails
        )
    }
    
    /**
     * Checks Build properties for emulator indicators.
     * Note: Detection messages are sanitized to avoid exposing PII (device identifiers).
     */
    private fun checkBuildProperties(detectionMethods: MutableList<String>) {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()
        val device = Build.DEVICE.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        
        // Check model (sanitized - no PII)
        if (model.contains("sdk") || model.contains("emulator") || 
            model.contains("google_sdk") || model.contains("droid4x") ||
            model.contains("genymotion") || model.contains("vbox")) {
            detectionMethods.add("BUILD_MODEL_CHECK")
        }
        
        // Check manufacturer (sanitized - no PII)
        if (manufacturer.contains("unknown") || manufacturer.contains("generic") ||
            manufacturer.contains("genymotion") || manufacturer.contains("vbox")) {
            detectionMethods.add("BUILD_MANUFACTURER_CHECK")
        }
        
        // Check product (sanitized - no PII)
        if (product.contains("sdk") || product.contains("emulator") ||
            product.contains("google_sdk") || product.contains("vbox") ||
            product.contains("genymotion")) {
            detectionMethods.add("BUILD_PRODUCT_CHECK")
        }
        
        // Check device (sanitized - no PII)
        if (device.contains("generic") || device.contains("emulator") ||
            device.contains("vbox") || device.contains("genymotion")) {
            detectionMethods.add("BUILD_DEVICE_CHECK")
        }
        
        // Check hardware (sanitized - no PII)
        if (hardware.contains("goldfish") || hardware.contains("ranchu") ||
            hardware.contains("vbox")) {
            detectionMethods.add("BUILD_HARDWARE_CHECK")
        }
        
        // Check brand (sanitized - no PII)
        if (brand.contains("generic") || brand.contains("unknown")) {
            detectionMethods.add("BUILD_BRAND_CHECK")
        }
        
        // Check fingerprint (sanitized - no PII)
        if (fingerprint.contains("generic") || fingerprint.contains("unknown") ||
            fingerprint.contains("vbox") || fingerprint.contains("test-keys")) {
            detectionMethods.add("BUILD_FINGERPRINT_CHECK")
        }
    }
    
    /**
     * Checks system properties via reflection for emulator indicators.
     * 
     * Note: Uses reflection to access android.os.SystemProperties, which is an internal API.
     * This is necessary because Android does not provide a public API to read system properties
     * like ro.kernel.qemu, ro.hardware, etc. that are essential for emulator detection.
     * The reflection is wrapped in try-catch to handle cases where the API is unavailable.
     */
    @SuppressLint("PrivateApi")
    private fun checkSystemProperties(detectionMethods: MutableList<String>) {
        try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            
            // Check ro.kernel.qemu (sanitized - no PII)
            val qemu = getMethod.invoke(null, "ro.kernel.qemu") as? String
            if (qemu == "1") {
                detectionMethods.add("SYSTEM_PROP_QEMU_CHECK")
            }
            
            // Check ro.hardware (sanitized - no PII)
            val hardware = getMethod.invoke(null, "ro.hardware") as? String
            hardware?.let {
                if (it.contains("goldfish", ignoreCase = true) ||
                    it.contains("ranchu", ignoreCase = true) ||
                    it.contains("vbox", ignoreCase = true)) {
                    detectionMethods.add("SYSTEM_PROP_HARDWARE_CHECK")
                }
            }
            
            // Check ro.product.model (sanitized - no PII)
            val productModel = getMethod.invoke(null, "ro.product.model") as? String
            productModel?.let {
                if (it.contains("sdk", ignoreCase = true) ||
                    it.contains("emulator", ignoreCase = true) ||
                    it.contains("generic", ignoreCase = true)) {
                    detectionMethods.add("SYSTEM_PROP_PRODUCT_MODEL_CHECK")
                }
            }
            
            // Check ro.build.characteristics (sanitized - no PII)
            val characteristics = getMethod.invoke(null, "ro.build.characteristics") as? String
            characteristics?.let {
                if (it.contains("emulator", ignoreCase = true)) {
                    detectionMethods.add("SYSTEM_PROP_CHARACTERISTICS_CHECK")
                }
            }
        } catch (e: ClassNotFoundException) {
            // SystemProperties not available
        } catch (e: NoSuchMethodException) {
            // Method signature changed
        }
    }    
    /**
     * Checks for emulator-specific files and libraries.
     */
    private fun checkEmulatorFiles(detectionMethods: MutableList<String>) {
        val emulatorFiles = listOf(
            "/system/lib/libqemu.so",
            "/system/lib64/libqemu.so",
            "/system/lib/libhoudini.so",
            "/system/lib64/libhoudini.so",
            "/system/bin/qemu-props",
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/sys/qemu_trace",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/bus/platform/drivers/qemu_pipe",
            "/dev/socket/baseband_genyd"
        )
        
        emulatorFiles.forEach { path ->
            try {
                if (File(path).exists()) {
                    detectionMethods.add("Emulator file detected: $path")
                }
            } catch (e: Exception) {
                // Silently ignore permission errors
            }
        }
        
        // Check for QEMU-related files in /init.rc patterns
        try {
            val initRc = File("/init.rc")
            if (initRc.exists()) {
                val content = initRc.readText()
                if (content.contains("qemu", ignoreCase = true)) {
                    detectionMethods.add("QEMU references found in /init.rc")
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Checks telephony information. Emulators often lack proper telephony.
     */
    private fun checkTelephony(detectionMethods: MutableList<String>, context: Context) {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.let { tm ->
                // Check phone type
                val phoneType = tm.phoneType
                if (phoneType == TelephonyManager.PHONE_TYPE_NONE) {
                    detectionMethods.add("No telephony support (PHONE_TYPE_NONE)")
                }
                
                // IMSI check removed: TelephonyManager.subscriberId is deprecated and IMSI is PII
                // Use SubscriptionManager/SubscriptionInfo for privacy-respecting alternatives if needed
            }
        } catch (e: Exception) {
            // Ignore telephony check errors
        }
    }
    
    /**
     * Checks for CPU/ABI anomalies that might indicate emulation.
     */
    private fun checkCPUABI(detectionMethods: MutableList<String>) {
        try {
            // Check supported ABIs
            val supportedABIs = Build.SUPPORTED_ABIS
            if (supportedABIs.isEmpty()) {
                detectionMethods.add("No supported ABIs detected")
            }
            
            // Check for unexpected architectures (sanitized - no PII)
            val abis = supportedABIs.joinToString(", ")
            if (abis.contains("x86") && !abis.contains("armeabi") && !abis.contains("arm64")) {
                // x86 without ARM might indicate emulator (though some real devices are x86)
                detectionMethods.add("CPU_ABI_ANOMALY_CHECK")
            }
            
            // Try to read /proc/cpuinfo
            try {
                val cpuInfo = File("/proc/cpuinfo")
                if (cpuInfo.exists()) {
                    val content = cpuInfo.readText()
                    if (content.contains("goldfish", ignoreCase = true) ||
                        content.contains("qemu", ignoreCase = true) ||
                        content.contains("vbox", ignoreCase = true)) {
                        detectionMethods.add("Emulator indicators in /proc/cpuinfo")
                    }
                }
            } catch (e: Exception) {
                // Ignore /proc/cpuinfo read errors
            }
        } catch (e: Exception) {
            // Ignore CPU/ABI check errors
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
            
            // System properties
            // Note: Uses reflection to access android.os.SystemProperties (internal API)
            // This is necessary for emulator detection as there's no public API for system properties
            try {
                @SuppressLint("PrivateApi")
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

data class EmulatorDetectionResult(
    val isEmulator: Boolean,
    val detectionMethodNames: List<String>, // Non-PII identifiers
    val debugDetails: Map<String, String>? = null // PII - only populated in debug builds
)
