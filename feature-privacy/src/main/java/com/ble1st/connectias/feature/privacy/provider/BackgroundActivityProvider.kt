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
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    private val packageManager: PackageManager by lazy {
        context.packageManager
    }

    private val appOpsManager: AppOpsManager? by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    }

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    /**
     * Gets background activity information.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getBackgroundActivityInfo(): BackgroundActivityInfo = withContext(Dispatchers.IO) {
        try {
            val runningServices = getRunningServices()
            val appsWithBackgroundRestrictions = getAppsWithBackgroundRestrictions()
            val appsIgnoringBatteryOptimization = getAppsIgnoringBatteryOptimization()

            BackgroundActivityInfo(
                runningServices = runningServices,
                appsWithBackgroundRestrictions = appsWithBackgroundRestrictions,
                appsIgnoringBatteryOptimization = appsIgnoringBatteryOptimization,
                totalRunningServices = runningServices.size
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting background activity info")
            BackgroundActivityInfo(
                runningServices = emptyList(),
                appsWithBackgroundRestrictions = emptyList(),
                appsIgnoringBatteryOptimization = emptyList(),
                totalRunningServices = 0
            )
        }
    }

    private fun getRunningServices(): List<RunningService> {
        return try {
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // getRunningServices is deprecated on API 26+, but we use it for compatibility
                @Suppress("DEPRECATION")
                activityManager.getRunningServices(Integer.MAX_VALUE)
            } else {
                @Suppress("DEPRECATION")
                activityManager.getRunningServices(Integer.MAX_VALUE)
            }

            services
                .filter { it.service.packageName != context.packageName } // Exclude our own app
                .map { serviceInfo ->
                    val packageName = serviceInfo.service.packageName
                    val appName = try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        appInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: packageName
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

    private fun getAppsWithBackgroundRestrictions(): List<String> {
        return try {
            if (appOpsManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return emptyList()
            }

            val installedPackages = packageManager.getInstalledPackages(0)
            installedPackages
                .mapNotNull { packageInfo ->
                    try {
                        val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            appOpsManager!!.unsafeCheckOpNoThrow(
                                "android:run_in_background",
                                appInfo.uid,
                                packageInfo.packageName
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            @Suppress("DEPRECATION")
                            appOpsManager!!.checkOpNoThrow(
                                "android:run_in_background",
                                appInfo.uid,
                                packageInfo.packageName
                            )
                        } else {
                            AppOpsManager.MODE_ALLOWED
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

    private fun getAppsIgnoringBatteryOptimization(): List<String> {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return emptyList()
            }

            val installedPackages = packageManager.getInstalledPackages(0)
            installedPackages
                .mapNotNull { packageInfo ->
                    try {
                        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageInfo.packageName)
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

