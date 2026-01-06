package com.ble1st.connectias.core.security.tamper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File

/**
 * Detects tampering and hooking frameworks on the device.
 * 
 * This detector checks for:
 * - Xposed frameworks (classic, EdXposed, LSPosed)
 * - Frida server
 * - Other hooking frameworks
 * 
 * Note: Detection performs I/O operations on Dispatchers.IO automatically.
 * Call from any coroutine context; threading is handled internally.
 * 
 * Uses Rust implementation (primary) with fallback to Kotlin implementation.
 */
class TamperDetector(private val context: Context? = null) {
    
    private val rustDetector = try {
        RustTamperDetector(context)
    } catch (e: Exception) {
        null // Fallback to Kotlin if Rust not available
    }
    
    /**
     * Detects tampering using Rust implementation (primary) with fallback to Kotlin.
     * This is a suspend function that performs I/O on Dispatchers.IO to avoid blocking.
     * 
     * @return TamperDetectionResult with detection status and methods
     */
    suspend fun detectTampering(): TamperDetectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        // Try Rust implementation first (faster and more secure)
        if (rustDetector != null) {
            try {
                Timber.i("🔴 [TamperDetector] Using RUST implementation")
                val rustStartTime = System.currentTimeMillis()
                
                val result = rustDetector.detectTampering()
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [TamperDetector] RUST detection completed - Tampered: ${result.isTampered}, Methods: ${result.detectionMethods.size} | Duration: ${rustDuration}ms")
                Timber.d("📊 [TamperDetector] Total time (including overhead): ${totalDuration}ms")
                
                return@withContext result
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [TamperDetector] RUST detection failed after ${rustDuration}ms, falling back to Kotlin")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [TamperDetector] Rust detector not available, using Kotlin")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟡 [TamperDetector] Using KOTLIN implementation")
        val kotlinStartTime = System.currentTimeMillis()
        
        val detectionMethods = mutableListOf<String>()
        
        // 1. Check for hook frameworks (Xposed variants)
        checkHookFrameworks(detectionMethods)
        
        // 2. Check for Frida server
        checkFrida(detectionMethods)
        
        // 3. Check for other tampering indicators
        checkOtherTamperingIndicators(detectionMethods)
        
        // 4. Check for installed hooking apps (if context available)
        if (context != null) {
            checkHookingApps(detectionMethods, context)
        }
        
        val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
        val totalDuration = System.currentTimeMillis() - startTime
        
        val result = TamperDetectionResult(
            isTampered = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
        
        Timber.i("✅ [TamperDetector] KOTLIN detection completed - Tampered: ${result.isTampered}, Methods: ${result.detectionMethods.size} | Duration: ${kotlinDuration}ms")
        Timber.d("📊 [TamperDetector] Total time (including overhead): ${totalDuration}ms")
        
        return@withContext result
    }
    
    /**
     * Checks for hook frameworks including Xposed, EdXposed, and LSPosed.
     */
    private suspend fun checkHookFrameworks(detectionMethods: MutableList<String>) {
        val hookIndicators = listOf(
            "/system/xbin/xposed" to "Xposed framework detected",
            "/system/lib/libxposed_art.so" to "Xposed library (32-bit) detected",
            "/system/lib64/libxposed_art.so" to "Xposed library (64-bit) detected",
            "/system/framework/XposedBridge.jar" to "XposedBridge.jar detected",
            "/data/adb/modules/edxposed" to "EdXposed module detected",
            "/data/adb/modules/lsposed" to "LSPosed module detected",
            "/data/adb/modules/riru_edxposed" to "Riru EdXposed module detected",
            "/data/adb/modules/riru_lsposed" to "Riru LSPosed module detected",
            "/data/xposed.prop" to "Xposed properties file detected",
            "/system/lib/libedxposed.so" to "EdXposed library (32-bit) detected",
            "/system/lib64/libedxposed.so" to "EdXposed library (64-bit) detected",
            "/vendor/lib/libedxposed.so" to "EdXposed vendor library (32-bit) detected",
            "/vendor/lib64/libedxposed.so" to "EdXposed vendor library (64-bit) detected",
            "/system/lib/libsupol.so" to "Xposed supol library (32-bit) detected",
            "/system/lib64/libsupol.so" to "Xposed supol library (64-bit) detected"
        )
        
        hookIndicators.forEach { (path, message) ->
            try {
                if (File(path).exists()) {
                    detectionMethods.add(message)
                }
            } catch (e: Exception) {
                // Silently ignore permission errors
            }
        }
        
        // Check for Xposed-related processes (PII-redacted) with global timeout
        try {
            val procDir = File("/proc")
            if (procDir.exists()) {
                // Filter to only numeric PID directories and sort deterministically
                val pidDirs = procDir.listFiles()
                    ?.filter { it.name.all { char -> char.isDigit() } } // Only numeric PIDs
                    ?.sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE } // Sort by PID
                    ?: emptyList()
                
                // Apply global timeout to entire scan (5 seconds)
                withTimeoutOrNull(5000) {
                    pidDirs.forEach { pidDir ->
                        try {
                            val pid = pidDir.name.toIntOrNull() ?: return@forEach
                            
                            val cmdlineFile = File(pidDir, "cmdline")
                            if (cmdlineFile.exists()) {
                                val cmdline = cmdlineFile.readText().trim()
                                val lowerCmdline = cmdline.lowercase()
                                
                                // Extract process name (first token before null/space) for safer detection
                                val processName = cmdline.split('\u0000', ' ').firstOrNull()?.lowercase() ?: lowerCmdline
                                
                                when {
                                    processName.contains("edxposed") -> {
                                        // Redact cmdline to avoid PII exposure
                                        detectionMethods.add("EdXposed process detected (pid=$pid)")
                                    }
                                    processName.contains("lsposed") -> {
                                        // Use specific "lsposed" check instead of generic "lsp"
                                        detectionMethods.add("LSPosed process detected (pid=$pid)")
                                    }
                                    processName.contains("xposed") -> {
                                        detectionMethods.add("Xposed process detected (pid=$pid)")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore individual process read errors
                        }
                    }
                } ?: run {
                    Timber.w("Process scan timed out after 5 seconds")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error scanning /proc for hook frameworks")
        }
    }
    
    /**
     * Checks for Frida server indicators.
     */
    private suspend fun checkFrida(detectionMethods: MutableList<String>) {
        val fridaIndicators = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            File(Environment.getExternalStorageDirectory(), "frida-server").path,
            "/system/bin/frida-server",
            "/system/xbin/frida-server"
        )
        
        fridaIndicators.forEach { path ->
            try {
                if (File(path).exists()) {
                    detectionMethods.add("Frida server detected: $path")
                }
            } catch (e: Exception) {
                // Silently ignore
            }
        }
        
        // Check for Frida processes (PII-redacted) with global timeout
        try {
            val procDir = File("/proc")
            if (procDir.exists()) {
                // Filter to only numeric PID directories and sort deterministically
                val pidDirs = procDir.listFiles()
                    ?.filter { it.name.all { char -> char.isDigit() } } // Only numeric PIDs
                    ?.sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE } // Sort by PID
                    ?: emptyList()
                
                // Apply global timeout to entire scan (5 seconds)
                withTimeoutOrNull(5000) {
                    pidDirs.forEach { pidDir ->
                        try {
                            val pid = pidDir.name.toIntOrNull() ?: return@forEach
                            
                            val cmdlineFile = File(pidDir, "cmdline")
                            if (cmdlineFile.exists()) {
                                val cmdline = cmdlineFile.readText().trim()
                                if (cmdline.contains("frida", ignoreCase = true)) {
                                    // Redact cmdline to avoid PII exposure
                                    detectionMethods.add("Frida process detected (pid=$pid)")
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore individual process read errors
                        }
                    }
                } ?: run {
                    Timber.w("Process scan timed out after 5 seconds")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error scanning /proc for Frida processes")
        }
    }
    
    /**
     * Checks for other tampering indicators.
     */
    private fun checkOtherTamperingIndicators(detectionMethods: MutableList<String>) {
        val otherIndicators = listOf(
            "/system/lib/libsubstrate.so" to "Substrate framework detected",
            "/system/lib64/libsubstrate.so" to "Substrate framework (64-bit) detected",
            "/data/local/tmp/substrate" to "Substrate temporary files detected"
        )
        
        otherIndicators.forEach { (path, message) ->
            try {
                if (File(path).exists()) {
                    detectionMethods.add(message)
                }
            } catch (e: Exception) {
                // Silently ignore
            }
        }
    }
    
    /**
     * Checks for installed hooking apps via PackageManager.
     */
    private fun checkHookingApps(detectionMethods: MutableList<String>, context: Context) {
        val hookingApps = listOf(
            "de.robv.android.xposed.installer" to "Xposed Installer",
            "org.meowcat.edxposed.manager" to "EdXposed Manager",
            "org.lsposed.manager" to "LSPosed Manager",
            "com.saurik.substrate" to "Substrate"
        )
        
        try {
            val packageManager = context.packageManager
            hookingApps.forEach { (packageName, appName) ->
                try {
                    packageManager.getPackageInfo(packageName, 0)
                    detectionMethods.add("Hooking app installed: $appName ($packageName)")
                } catch (e: PackageManager.NameNotFoundException) {
                    // Package not found, continue
                } catch (e: Exception) {
                    // Other errors, ignore
                }
            }
        } catch (e: Exception) {
            // Ignore package manager errors
        }
    }
}

data class TamperDetectionResult(
    val isTampered: Boolean,
    val detectionMethods: List<String>
)