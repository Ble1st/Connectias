package com.ble1st.connectias.core.security.emulator

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Detects if the device is running in an emulator environment.
 * Uses multiple heuristics including Build properties, system properties,
 * file system checks, hardware sensors, and telephony information.
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
        
        // 2. Check system properties via reflection
        checkSystemProperties(detectionMethods)
        
        // 3. Check for emulator-specific files
        checkEmulatorFiles(detectionMethods)
        
        // 4. Check telephony (emulators often lack proper telephony)
        if (context != null) {
            checkTelephony(detectionMethods, context)
        }
        
        // 5. Check CPU/ABI anomalies
        checkCPUABI(detectionMethods)
        
        return@withContext EmulatorDetectionResult(
            isEmulator = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
    }
    
    /**
     * Checks Build properties for emulator indicators.
     */
    private fun checkBuildProperties(detectionMethods: MutableList<String>) {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()
        val device = Build.DEVICE.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val brand = Build.BRAND.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        
        // Check model
        if (model.contains("sdk") || model.contains("emulator") || 
            model.contains("google_sdk") || model.contains("droid4x") ||
            model.contains("genymotion") || model.contains("vbox")) {
            detectionMethods.add("Emulator model detected: ${Build.MODEL}")
        }
        
        // Check manufacturer
        if (manufacturer.contains("unknown") || manufacturer.contains("generic") ||
            manufacturer.contains("genymotion") || manufacturer.contains("vbox")) {
            detectionMethods.add("Generic/suspicious manufacturer: $manufacturer")
        }
        
        // Check product
        if (product.contains("sdk") || product.contains("emulator") ||
            product.contains("google_sdk") || product.contains("vbox") ||
            product.contains("genymotion")) {
            detectionMethods.add("Emulator product detected: $product")
        }
        
        // Check device
        if (device.contains("generic") || device.contains("emulator") ||
            device.contains("vbox") || device.contains("genymotion")) {
            detectionMethods.add("Emulator device detected: $device")
        }
        
        // Check hardware
        if (hardware.contains("goldfish") || hardware.contains("ranchu") ||
            hardware.contains("vbox")) {
            detectionMethods.add("Emulator hardware detected: $hardware")
        }
        
        // Check brand
        if (brand.contains("generic") || brand.contains("unknown")) {
            detectionMethods.add("Generic brand: $brand")
        }
        
        // Check fingerprint
        if (fingerprint.contains("generic") || fingerprint.contains("unknown") ||
            fingerprint.contains("vbox") || fingerprint.contains("test-keys")) {
            detectionMethods.add("Suspicious fingerprint: $fingerprint")
        }
    }
    
    /**
     * Checks system properties via reflection for emulator indicators.
     */
    private fun checkSystemProperties(detectionMethods: MutableList<String>) {
        try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            
            // Check ro.kernel.qemu
            val qemu = getMethod.invoke(null, "ro.kernel.qemu") as? String
            if (qemu == "1") {
                detectionMethods.add("System property ro.kernel.qemu = 1 (QEMU emulator)")
            }
            
            // Check ro.hardware
            val hardware = getMethod.invoke(null, "ro.hardware") as? String
            hardware?.let {
                if (it.contains("goldfish", ignoreCase = true) ||
                    it.contains("ranchu", ignoreCase = true) ||
                    it.contains("vbox", ignoreCase = true)) {
                    detectionMethods.add("Emulator hardware property: $it")
                }
            }
            
            // Check ro.product.model
            val productModel = getMethod.invoke(null, "ro.product.model") as? String
            productModel?.let {
                if (it.contains("sdk", ignoreCase = true) ||
                    it.contains("emulator", ignoreCase = true) ||
                    it.contains("generic", ignoreCase = true)) {
                    detectionMethods.add("Emulator product model property: $it")
                }
            }
            
            // Check ro.build.characteristics
            val characteristics = getMethod.invoke(null, "ro.build.characteristics") as? String
            characteristics?.let {
                if (it.contains("emulator", ignoreCase = true)) {
                    detectionMethods.add("Build characteristics indicate emulator: $it")
                }
            }
        } catch (e: Exception) {
            // Reflection may fail, silently ignore
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
                
                // Check for IMEI/IMSI (emulators often return null or default values)
                try {
                    val imei = tm.imei
                    if (imei == null || imei.isEmpty() || 
                        imei == "000000000000000" || imei == "012345678901234") {
                        detectionMethods.add("Suspicious or missing IMEI: $imei")
                    }
                } catch (e: SecurityException) {
                    // Permission denied, ignore
                } catch (e: Exception) {
                    // Other errors, ignore
                }
                
                try {
                    val subscriberId = tm.subscriberId
                    if (subscriberId == null || subscriberId.isEmpty() ||
                        subscriberId == "310260000000000") {
                        detectionMethods.add("Suspicious or missing IMSI")
                    }
                } catch (e: SecurityException) {
                    // Permission denied, ignore
                } catch (e: Exception) {
                    // Other errors, ignore
                }
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
            
            // Check for unexpected architectures
            val abis = supportedABIs.joinToString(", ")
            if (abis.contains("x86") && !abis.contains("armeabi") && !abis.contains("arm64")) {
                // x86 without ARM might indicate emulator (though some real devices are x86)
                detectionMethods.add("x86 architecture without ARM support: $abis")
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
}

data class EmulatorDetectionResult(
    val isEmulator: Boolean,
    val detectionMethods: List<String>
)
