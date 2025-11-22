package com.ble1st.connectias.feature.privacy.provider

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.ble1st.connectias.feature.privacy.models.BackgroundActivityInfo
import com.ble1st.connectias.feature.privacy.models.RunningService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides background activity information including services and restrictions.
 */
@Singleton
class BackgroundActivityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager: ActivityManager? by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    private val packageManager: PackageManager by lazy {
        context.packageManager
    }

    private val appOpsManager: AppOpsManager? by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    }

    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    
    private companion object {
        // Fallback constant for minSdk < 28
        private const val OPSTR_RUN_IN_BACKGROUND_FALLBACK = "android:run_in_background"
    }

    /**
     * Gets background activity information.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getBackgroundActivityInfo(): BackgroundActivityInfo = withContext(Dispatchers.IO) {
        try {
            // Get installed packages once and reuse
            var isIncomplete = false
            val installedPackages = try {
                val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledPackages(0)
                }
                packages.filter { 
                    // Filter out system packages for better performance
                    (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0) ||
                    (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                }
            } catch (e: SecurityException) {
                // System API failure - treat as fatal, will be caught by outer catch
                Timber.e(e, "SecurityException getting installed packages - system API failure")
                throw e
            } catch (e: android.os.BadParcelableException) {
                Timber.e(e, "Binder transaction failed: too many packages. Returning empty list.")
                isIncomplete = true
                emptyList<android.content.pm.PackageInfo>()
            } catch (e: android.os.DeadSystemException) {
                Timber.e(e, "Binder transaction failed: system is dead. Returning empty list.")
                isIncomplete = true
                emptyList<android.content.pm.PackageInfo>()
            } catch (e: android.os.DeadObjectException) {
                Timber.e(e, "Binder transaction failed: remote process died or buffer full. Returning empty list.")
                isIncomplete = true
                emptyList<android.content.pm.PackageInfo>()
            } catch (e: RuntimeException) {
                // Catch DeadSystemRuntimeException and other runtime exceptions
                if (e.cause is android.os.DeadSystemException) {
                    Timber.e(e, "Binder transaction failed: system runtime is dead. Returning empty list.")
                } else {
                    Timber.e(e, "Runtime exception during binder transaction. Returning empty list.")
                }
                isIncomplete = true
                emptyList<android.content.pm.PackageInfo>()
            } catch (e: Exception) {
                // Recoverable error (e.g., filtering issues) - mark as incomplete
                Timber.e(e, "Error getting installed packages, falling back to empty list")
                isIncomplete = true
                emptyList<android.content.pm.PackageInfo>()
            }
            
            val runningServices = getRunningServices()
            val appsWithBackgroundRestrictions = getAppsWithBackgroundRestrictions(installedPackages)
            val appsIgnoringBatteryOptimization = getAppsIgnoringBatteryOptimization(installedPackages)

            BackgroundActivityInfo(
                runningServices = runningServices,
                appsWithBackgroundRestrictions = appsWithBackgroundRestrictions,
                appsIgnoringBatteryOptimization = appsIgnoringBatteryOptimization,
                totalRunningServices = runningServices.size,
                isIncomplete = isIncomplete
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting background activity info")
            BackgroundActivityInfo(
                runningServices = emptyList(),
                appsWithBackgroundRestrictions = emptyList(),
                appsIgnoringBatteryOptimization = emptyList(),
                totalRunningServices = 0,
                isIncomplete = true
            )
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun getRunningServices(): List<RunningService> {
        return try {
            val activityMgr = activityManager ?: return emptyList()
            // getRunningServices is deprecated on API 26+, but we use it for compatibility
            @Suppress("DEPRECATION")
            val services = activityMgr.getRunningServices(Integer.MAX_VALUE)

            services
                .filter { it.service.packageName != context.packageName } // Exclude our own app
                .map { serviceInfo ->
                    val packageName = serviceInfo.service.packageName
                    val appName = try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        packageName
                    }
                    RunningService(
                        packageName = packageName,
                        appName = appName,
                        serviceName = serviceInfo.service.className
                    )
                }
        } catch (e: Exception) {
            Timber.e(e, "Error getting running services")
            emptyList()
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun getAppsWithBackgroundRestrictions(installedPackages: List<android.content.pm.PackageInfo>): List<String> {
        return try {
            val appOpsMgr = appOpsManager ?: return emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return emptyList()
            }

            // OPSTR_RUN_IN_BACKGROUND is only available on API 28+, use string literal for compatibility
            val opString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "android:run_in_background"
            } else {
                OPSTR_RUN_IN_BACKGROUND_FALLBACK
            }

            installedPackages
                .mapNotNull { packageInfo ->
                    try {
                        val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            appOpsMgr.unsafeCheckOpNoThrow(
                                opString,
                                appInfo.uid,
                                packageInfo.packageName
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            appOpsMgr.checkOpNoThrow(
                                opString,
                                appInfo.uid,
                                packageInfo.packageName
                            )
                        }

                        if (mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED) {
                            packageInfo.packageName
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error getting apps with background restrictions")
            emptyList()
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun getAppsIgnoringBatteryOptimization(installedPackages: List<android.content.pm.PackageInfo>): List<String> {
        return try {
            val powerMgr = powerManager ?: return emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return emptyList()
            }

            installedPackages
                .mapNotNull { packageInfo ->
                    try {
                        val isIgnoring = powerMgr.isIgnoringBatteryOptimizations(packageInfo.packageName)
                        if (isIgnoring) {
                            packageInfo.packageName
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error getting apps ignoring battery optimization")
            emptyList()
        }
    }
}

