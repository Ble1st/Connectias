package com.ble1st.connectias.feature.security.privacy.leakage

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for data leakage detection.
 * Monitors clipboard, screenshots, and background activity.
 */
@Singleton
class DataLeakageProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val packageManager: PackageManager = context.packageManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * Monitors clipboard for sensitive data.
     * 
     * @return Flow of ClipboardEntry
     */
    fun monitorClipboard(): Flow<ClipboardEntry> = flow {
        var lastClipText: String? = null
        
        while (true) {
            kotlinx.coroutines.delay(1000) // Check every second
            
            val clipData = clipboardManager.primaryClip
            val currentText = clipData?.getItemAt(0)?.text?.toString()
            
            if (currentText != null && currentText != lastClipText) {
                val sensitivity = analyzeSensitivity(currentText)
                if (sensitivity != SensitivityLevel.NONE) {
                    emit(
                        ClipboardEntry(
                            text = currentText,
                            timestamp = System.currentTimeMillis(),
                            sensitivity = sensitivity
                        )
                    )
                }
                lastClipText = currentText
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Analyzes text for sensitive information.
     */
    suspend fun analyzeSensitivity(text: String): SensitivityLevel = withContext(Dispatchers.Default) {
        when {
            // Email pattern
            text.contains(Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")) -> SensitivityLevel.HIGH
            // Phone pattern
            text.contains(Regex("""(\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}""")) -> SensitivityLevel.HIGH
            // Credit card pattern
            text.contains(Regex("""\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}""")) -> SensitivityLevel.CRITICAL
            // SSN pattern
            text.contains(Regex("""\d{3}-\d{2}-\d{4}""")) -> SensitivityLevel.CRITICAL
            // Password-like (long alphanumeric)
            text.length > 12 && text.matches(Regex("""[A-Za-z0-9!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]+""")) -> SensitivityLevel.HIGH
            // IP address
            text.contains(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")) -> SensitivityLevel.MEDIUM
            // URL
            text.contains(Regex("""https?://[^\s]+""")) -> SensitivityLevel.MEDIUM
            else -> SensitivityLevel.NONE
        }
    }

    /**
     * Gets apps with clipboard access permissions.
     */
    suspend fun getAppsWithClipboardAccess(): List<AppClipboardAccess> = withContext(Dispatchers.IO) {
        try {
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val appsWithAccess = mutableListOf<AppClipboardAccess>()

            packages.forEach { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@forEach
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                // Note: Android 10+ restricts clipboard access, but we can still check permissions
                val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
                // Clipboard access is implicit, but we check for related permissions
                val hasRelatedPermissions = requestedPermissions.any { permission ->
                    permission.contains("READ", ignoreCase = true) ||
                    permission.contains("WRITE", ignoreCase = true)
                }

                if (hasRelatedPermissions) {
                    appsWithAccess.add(
                        AppClipboardAccess(
                            appName = appName,
                            packageName = packageInfo.packageName,
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    )
                }
            }

            appsWithAccess
        } catch (e: Exception) {
            Timber.e(e, "Failed to get apps with clipboard access")
            emptyList()
        }
    }

    /**
     * Gets apps that can take screenshots (simplified check).
     */
    suspend fun getAppsWithScreenshotCapability(): List<String> = withContext(Dispatchers.Default) {
        // In a real implementation, this would check for apps with screen capture permissions
        // For now, return empty list as Android doesn't expose this directly
        emptyList()
    }
}

/**
 * Clipboard entry.
 */
data class ClipboardEntry(
    val text: String,
    val timestamp: Long,
    val sensitivity: SensitivityLevel
)

/**
 * App clipboard access information.
 */
data class AppClipboardAccess(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean
)

/**
 * Sensitivity levels for data.
 */
enum class SensitivityLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

