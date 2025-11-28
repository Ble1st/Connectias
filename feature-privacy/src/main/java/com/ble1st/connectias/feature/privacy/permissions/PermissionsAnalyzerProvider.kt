package com.ble1st.connectias.feature.privacy.permissions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for app permissions analysis.
 * Analyzes permissions usage and provides recommendations.
 */
@Singleton
class PermissionsAnalyzerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Risky permissions that should be reviewed.
     */
    private val riskyPermissions = setOf(
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.READ_CALL_LOG
    )

    /**
     * Gets permissions for all installed apps.
     * 
     * @return List of AppPermissions
     */
    suspend fun getAllAppPermissions(): List<AppPermissions> = withContext(Dispatchers.IO) {
        try {
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            packages.mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
                val grantedPermissions = requestedPermissions.filter { permission ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                }

                val riskyGranted = grantedPermissions.filter { it in riskyPermissions }
                
                AppPermissions(
                    appName = appName,
                    packageName = packageInfo.packageName,
                    requestedPermissions = requestedPermissions.toList(),
                    grantedPermissions = grantedPermissions,
                    riskyPermissions = riskyGranted,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get app permissions")
            emptyList()
        }
    }

    /**
     * Gets apps with risky permissions.
     */
    suspend fun getAppsWithRiskyPermissions(): List<AppPermissions> = withContext(Dispatchers.Default) {
        getAllAppPermissions().filter { it.riskyPermissions.isNotEmpty() }
    }

    /**
     * Gets permission usage history (simplified - would need actual usage tracking in production).
     */
    suspend fun getPermissionUsageHistory(packageName: String): List<PermissionUsage> = withContext(Dispatchers.Default) {
        // In a real implementation, this would track actual permission usage
        // For now, return empty list
        emptyList()
    }

    /**
     * Provides recommendations for permissions.
     */
    suspend fun getRecommendations(appPermissions: AppPermissions): List<PermissionRecommendation> = withContext(Dispatchers.Default) {
        val recommendations = mutableListOf<PermissionRecommendation>()
        
        appPermissions.riskyPermissions.forEach { permission ->
            val recommendation = when (permission) {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION -> {
                    PermissionRecommendation(
                        permission = permission,
                        recommendation = "Consider using approximate location instead of precise location",
                        riskLevel = RiskLevel.HIGH
                    )
                }
                android.Manifest.permission.READ_CONTACTS -> {
                    PermissionRecommendation(
                        permission = permission,
                        recommendation = "Review if app really needs access to all contacts",
                        riskLevel = RiskLevel.HIGH
                    )
                }
                android.Manifest.permission.CAMERA -> {
                    PermissionRecommendation(
                        permission = permission,
                        recommendation = "Camera access should only be granted when actively using camera features",
                        riskLevel = RiskLevel.MEDIUM
                    )
                }
                android.Manifest.permission.RECORD_AUDIO -> {
                    PermissionRecommendation(
                        permission = permission,
                        recommendation = "Microphone access should only be granted when actively recording",
                        riskLevel = RiskLevel.HIGH
                    )
                }
                else -> {
                    PermissionRecommendation(
                        permission = permission,
                        recommendation = "Review if this permission is necessary for app functionality",
                        riskLevel = RiskLevel.MEDIUM
                    )
                }
            }
            recommendations.add(recommendation)
        }
        
        recommendations
    }
}

/**
 * App permissions information.
 */
data class AppPermissions(
    val appName: String,
    val packageName: String,
    val requestedPermissions: List<String>,
    val grantedPermissions: List<String>,
    val riskyPermissions: List<String>,
    val isSystemApp: Boolean
)

/**
 * Permission usage history entry.
 */
data class PermissionUsage(
    val permission: String,
    val timestamp: Long,
    val usageCount: Int
)

/**
 * Permission recommendation.
 */
data class PermissionRecommendation(
    val permission: String,
    val recommendation: String,
    val riskLevel: RiskLevel
)

/**
 * Risk levels.
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

