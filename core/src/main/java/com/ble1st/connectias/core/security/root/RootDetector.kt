package com.ble1st.connectias.core.security.root

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Detects root access on Android devices using RootBeer library and additional heuristic checks.
 * 
 * This detector uses a combination of:
 * - RootBeer library (comprehensive root detection with native checks)
 * - File system checks (su binaries, Magisk paths, Xposed frameworks)
 * - Build properties (test-keys, release-keys)
 * - SELinux status
 * - Package manager checks (known root apps)
 * 
 * Note: Root-hiding tools like Magisk Hide can bypass some of these checks.
 * This is a best-effort detection and should be combined with server-side validation.
 */
class RootDetector(private val context: Context? = null) {
    
    private val rootBeer: RootBeer? by lazy {
        context?.let { RootBeer(it) }
    }
    
    /**
     * Detects root access using RootBeer library (primary) and additional specific heuristics.
     * RootBeer covers most common root detection methods, so custom checks focus on
     * specific cases like Magisk and Xposed that may not be fully covered by RootBeer.
     * 
     * This method performs blocking I/O and should be called from a background thread.
     * 
     * @return RootDetectionResult with detection status and methods
     */
    suspend fun detectRoot(): RootDetectionResult = withContext(Dispatchers.IO) {
        val detectionMethods = mutableListOf<String>()
        
        // 1. RootBeer comprehensive check (primary method - replaces most custom checks)
        // RootBeer already checks: su binaries, root apps, dangerous props, SELinux, etc.
        if (context != null && rootBeer != null) {
            checkRootBeer(detectionMethods)
        }
        
        // 2. Additional specific checks (complement RootBeer for edge cases)
        // These focus on specific tools that RootBeer might not fully detect:
        
        // Check for Magisk using specific heuristics (RootBeer may not catch all Magisk variants)
        checkMagisk(detectionMethods)
        
        // Check for Xposed frameworks (RootBeer doesn't specifically check for Xposed)
        checkXposed(detectionMethods)
        
        // Note: The following checks are now handled by RootBeer:
        // - checkSuBinaries() - RootBeer checks su binaries
        // - checkRootApps() - RootBeer checks root management apps
        // - checkBuildTags() - RootBeer checks build properties
        // - checkSELinux() - RootBeer checks SELinux status
        
        return@withContext RootDetectionResult(
            isRooted = detectionMethods.isNotEmpty(),
            detectionMethods = detectionMethods
        )
    }
    
    /**
     * Checks for root using RootBeer library (primary detection method).
     * RootBeer performs comprehensive checks including:
     * - SU binaries in common locations
     * - Root management apps
     * - Dangerous system properties
     * - SELinux status
     * - Native checks via JNI
     * - Build properties (test-keys)
     */
    private fun checkRootBeer(detectionMethods: MutableList<String>) {
        try {
            rootBeer?.let { rb ->
                // Primary check: isRooted() - comprehensive root detection
                // This covers most common root detection methods
                if (rb.isRooted) {
                    detectionMethods.add("RootBeer: Root detected (comprehensive check)")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "RootBeer check failed")
            // Continue with other checks even if RootBeer fails
        }
    }
    
    /**
     * Checks for Magisk using multiple heuristics:
     * - Multiple known Magisk paths
     * - Magisk-related processes
     * - System properties
     * - Installed packages (Magisk Manager)
     */
    private fun checkMagisk(detectionMethods: MutableList<String>) {
        // Check multiple Magisk paths
        val magiskPaths = listOf(
            "/data/adb/magisk",
            "/sbin/.magisk",
            "/sbin/magisk",
            "/sbin/magiskhide",
            "/system/bin/magisk",
            "/data/adb/modules",
            "/cache/magisk.log",
            "/data/magisk/magisk.db"
        )

        magiskPaths.forEach { path ->
            try {
                if (File(path).exists()) {
                    detectionMethods.add("Magisk path detected: $path")
                }
            } catch (e: Exception) {
                // Silently ignore
            }
        }
        
        // Check for Magisk modules directory structure
        try {
            val modulesDir = File("/data/adb/modules")
            if (modulesDir.exists() && modulesDir.isDirectory) {
                val modules = modulesDir.listFiles()
                if (modules != null && modules.isNotEmpty()) {
                    detectionMethods.add("Magisk modules directory found with ${modules.size} modules")
                }
            }
        } catch (e: Exception) {
            // Silently ignore
        }
        
        // Note: /proc scanning for Magisk processes removed - resource intensive and redundant
        // with file path and package checks above
        
        // Check for Magisk Manager package (if context available)
        if (context != null) {
            try {
                val packageManager = context.packageManager
                val magiskPackages = listOf(
                    "com.topjohnwu.magisk",
                    "com.topjohnwu.magisk.debug"
                )
                
                magiskPackages.forEach { packageName ->
                    try {
                        packageManager.getPackageInfo(packageName, 0)
                        detectionMethods.add("Magisk Manager package installed: $packageName")
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
    
    /**
     * Checks for Xposed frameworks including classic Xposed, EdXposed, and LSPosed.
     */
    private fun checkXposed(detectionMethods: MutableList<String>) {
        // Classic Xposed paths
        val xposedPaths = listOf(
            "/system/framework/XposedBridge.jar",
            "/system/bin/app_process32_xposed",
            "/system/bin/app_process64_xposed",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so"
        )
        
        // EdXposed/LSPosed paths
        val modernXposedPaths = listOf(
            "/data/adb/modules/edxposed",
            "/data/adb/modules/lsposed",
            "/data/adb/modules/riru_edxposed",
            "/data/adb/modules/riru_lsposed",
            "/data/xposed.prop",
            "/system/lib/libedxposed.so",
            "/system/lib64/libedxposed.so",
            "/vendor/lib/libedxposed.so",
            "/vendor/lib64/libedxposed.so",
            "/system/lib/libsupol.so",
            "/system/lib64/libsupol.so"
        )
        
        // Check all paths
        val allXposedPaths = xposedPaths + modernXposedPaths
        allXposedPaths.forEach { path ->
            try {
                if (File(path).exists()) {
                    when {
                        path.contains("edxposed", ignoreCase = true) -> {
                            detectionMethods.add("EdXposed framework detected: $path")
                        }
                        path.contains("lsposed", ignoreCase = true) || path.contains("lsp", ignoreCase = true) -> {
                            detectionMethods.add("LSPosed framework detected: $path")
                        }
                        else -> {
                            detectionMethods.add("Xposed framework detected: $path")
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore
            }
        }
        
        // Note: /proc scanning for Xposed processes removed - resource intensive and redundant
        // with file path and package checks above
        
        // Check for Xposed-related packages (if context available)
        if (context != null) {
            try {
                val packageManager = context.packageManager
                val xposedPackages = listOf(
                    "de.robv.android.xposed.installer",
                    "org.meowcat.edxposed.manager",
                    "org.lsposed.manager"
                )
                
                xposedPackages.forEach { packageName ->
                    try {
                        packageManager.getPackageInfo(packageName, 0)
                        detectionMethods.add("Xposed-related package installed: $packageName")
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
    
    /**
     * Checks Build.TAGS for test-keys (indicates custom/rooted ROM).
     */
    private fun checkBuildTags(detectionMethods: MutableList<String>) {
        try {
            val tags = Build.TAGS
            if (tags != null) {
                when {
                    tags.contains("test-keys") -> {
                        detectionMethods.add("Build.TAGS contains 'test-keys' (custom ROM)")
                    }
                    !tags.contains("release-keys") -> {
                        detectionMethods.add("Build.TAGS missing 'release-keys' (suspicious)")
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Checks SELinux status. Non-enforcing SELinux can indicate root access.
     */
    private fun checkSELinux(detectionMethods: MutableList<String>) {
        try {
            // Try to read SELinux enforce status
            val enforceFile = File("/sys/fs/selinux/enforce")
            if (enforceFile.exists()) {
                val enforceStatus = enforceFile.readText().trim()
                if (enforceStatus == "0") {
                    detectionMethods.add("SELinux is not enforcing (status: $enforceStatus)")
                }
            }
        } catch (e: Exception) {
            // Silently ignore - SELinux status may not be accessible
        }
        
        // Try using Android API if available (API 23+)
        // Note: Uses reflection to access android.os.SELinux, which is an internal API.
        // This is necessary because Android does not provide a public API to check SELinux
        // enforcement status, which is important for root detection. The reflection is wrapped
        // in try-catch to handle cases where the API is unavailable.
        @SuppressLint("PrivateApi")
        try {
            val selinux = Class.forName("android.os.SELinux")
            val isEnforced = selinux.getMethod("isSELinuxEnforced").invoke(null) as? Boolean
            if (isEnforced == false) {
                detectionMethods.add("SELinux is not enforcing (via API)")
            }
        } catch (e: Exception) {
            // Reflection may fail, ignore
        }
    }
    
}

data class RootDetectionResult(
    val isRooted: Boolean,
    val detectionMethods: List<String>
)
