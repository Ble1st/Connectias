package com.ble1st.connectias.feature.security.firewall

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for firewall and network permission analysis.
 * Analyzes app network permissions and blocked connections.
 */
@Singleton
class FirewallAnalyzerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val packageManager: PackageManager = context.packageManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Gets network permissions for all installed apps.
     * 
     * @return List of AppNetworkInfo
     */
    suspend fun getAppNetworkPermissions(): List<AppNetworkInfo> = withContext(Dispatchers.IO) {
        try {
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val networkPermissions = listOf(
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                android.Manifest.permission.CHANGE_NETWORK_STATE,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.CHANGE_WIFI_STATE
            )

            packages.mapNotNull { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
                val hasInternet = requestedPermissions.contains(android.Manifest.permission.INTERNET)
                val hasNetworkState = requestedPermissions.contains(android.Manifest.permission.ACCESS_NETWORK_STATE)
                val hasWifiState = requestedPermissions.contains(android.Manifest.permission.ACCESS_WIFI_STATE)
                
                val grantedPermissions = requestedPermissions.filter { permission ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                }

                if (hasInternet || hasNetworkState || hasWifiState) {
                    AppNetworkInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        hasInternetPermission = hasInternet,
                        hasNetworkStatePermission = hasNetworkState,
                        hasWifiStatePermission = hasWifiState,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        grantedPermissions = grantedPermissions
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get app network permissions")
            emptyList()
        }
    }

    /**
     * Analyzes network permission risks.
     * 
     * @param appNetworkInfo List of app network info
     * @return List of risky apps
     */
    suspend fun analyzeRiskyApps(appNetworkInfo: List<AppNetworkInfo>): List<RiskyApp> = withContext(Dispatchers.Default) {
        appNetworkInfo.mapNotNull { app ->
            val riskLevel = calculateRiskLevel(app)
            if (riskLevel != RiskLevel.LOW) {
                RiskyApp(
                    appInfo = app,
                    riskLevel = riskLevel,
                    reasons = getRiskReasons(app, riskLevel)
                )
            } else {
                null
            }
        }
    }

    /**
     * Calculates risk level for an app.
     */
    private fun calculateRiskLevel(app: AppNetworkInfo): RiskLevel {
        var riskScore = 0

        // System apps are generally safer
        if (!app.isSystemApp) {
            riskScore += 1
        }

        // Multiple network permissions increase risk
        val permissionCount = listOf(
            app.hasInternetPermission,
            app.hasNetworkStatePermission,
            app.hasWifiStatePermission
        ).count { it }
        
        if (permissionCount >= 2) {
            riskScore += 1
        }

        return when {
            riskScore >= 2 -> RiskLevel.HIGH
            riskScore == 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    /**
     * Gets risk reasons for an app.
     */
    private fun getRiskReasons(app: AppNetworkInfo, riskLevel: RiskLevel): List<String> {
        val reasons = mutableListOf<String>()
        
        if (!app.isSystemApp) {
            reasons.add("Third-party app")
        }
        
        val permissionCount = listOf(
            app.hasInternetPermission,
            app.hasNetworkStatePermission,
            app.hasWifiStatePermission
        ).count { it }
        
        if (permissionCount >= 2) {
            reasons.add("Multiple network permissions")
        }
        
        if (app.hasInternetPermission) {
            reasons.add("Has internet access")
        }
        
        return reasons
    }
}

/**
 * App network information.
 */
data class AppNetworkInfo(
    val packageName: String,
    val appName: String,
    val hasInternetPermission: Boolean,
    val hasNetworkStatePermission: Boolean,
    val hasWifiStatePermission: Boolean,
    val isSystemApp: Boolean,
    val grantedPermissions: List<String>
)

/**
 * Risky app information.
 */
data class RiskyApp(
    val appInfo: AppNetworkInfo,
    val riskLevel: RiskLevel,
    val reasons: List<String>
)

/**
 * Risk levels.
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

