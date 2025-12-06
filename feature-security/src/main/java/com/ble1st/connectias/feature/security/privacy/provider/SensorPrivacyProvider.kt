package com.ble1st.connectias.feature.security.privacy.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Build
import com.ble1st.connectias.feature.security.privacy.models.SensorAccess
import com.ble1st.connectias.feature.security.privacy.models.SensorPrivacyInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides sensor privacy information including active sensor accesses.
 */
@Singleton
class SensorPrivacyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager by lazy {
        context.packageManager
    }

    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    /**
     * Gets sensor privacy information.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getSensorPrivacyInfo(): SensorPrivacyInfo = withContext(Dispatchers.IO) {
        try {
            val appsWithSensorAccess = getAppsWithSensorAccess()
            val potentialSensorAccesses = appsWithSensorAccess.map { appInfo ->
                createSensorAccess(appInfo)
            }

            SensorPrivacyInfo(
                potentialSensorAccesses = potentialSensorAccesses
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting sensor privacy info")
            SensorPrivacyInfo(
                potentialSensorAccesses = emptyList()
            )
        }
    }

    private fun getAppsWithSensorAccess(): List<ApplicationInfo> {
        return try {
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                ))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
            installedPackages
                .filter { it.requestedPermissions != null }
                .mapNotNull { packageInfo ->
                    try {
                        val relevantPermissions = listOf(
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.RECORD_AUDIO,
                            android.Manifest.permission.BODY_SENSORS,
                            android.Manifest.permission.ACTIVITY_RECOGNITION
                        )
                        
                        val hasSensorPermissions = relevantPermissions.any { permission ->
                            packageManager.checkPermission(permission, packageInfo.packageName) == PackageManager.PERMISSION_GRANTED
                        }

                        if (hasSensorPermissions) {
                            packageInfo.applicationInfo
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error processing package ${packageInfo.packageName} for sensor access")
                        null
                    }
                }
        } catch (e: android.os.BadParcelableException) {
            Timber.e(e, "Binder transaction failed: too many packages. Returning empty list.")
            emptyList()
        } catch (e: android.os.DeadSystemException) {
            Timber.e(e, "Binder transaction failed: system is dead. Returning empty list.")
            emptyList()
        } catch (e: android.os.DeadObjectException) {
            Timber.e(e, "Binder transaction failed: remote process died or buffer full. Returning empty list.")
            emptyList()
        } catch (e: RuntimeException) {
            // Catch DeadSystemRuntimeException and other runtime exceptions
            if (e.cause is android.os.DeadSystemException) {
                Timber.e(e, "Binder transaction failed: system runtime is dead. Returning empty list.")
            } else {
                Timber.e(e, "Runtime exception during binder transaction. Returning empty list.")
            }
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error getting apps with sensor access")
            emptyList()
        }
    }
    
    /**
     * Creates SensorAccess by inferring sensor types from granted permissions.
     * This is an inference based on declared/granted permissions, as Android doesn't
     * provide a direct API to query which sensors an app is actively using.
     */
    private fun createSensorAccess(appInfo: ApplicationInfo): SensorAccess {
        val packageName = appInfo.packageName
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        
        val appName = packageManager.getApplicationLabel(appInfo).toString()
        require(appName.isNotBlank()) { "App name cannot be blank" }

        val sensorTypes = mutableListOf<String>()
        
        // Permission-to-sensor mapping: infer sensor types from granted permissions
        // BODY_SENSORS permission indicates access to body sensors (heart rate, etc.)
        if (packageManager.checkPermission(
                android.Manifest.permission.BODY_SENSORS,
                packageName
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            sensorTypes.add("heart_rate")
            sensorTypes.add("body_sensors")
        }
        
        // ACTIVITY_RECOGNITION permission indicates step counter/detector access
        if (packageManager.checkPermission(                android.Manifest.permission.ACTIVITY_RECOGNITION,
                packageName
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            sensorTypes.add("step_counter")
            sensorTypes.add("step_detector")
        }

        // Check camera and microphone permissions
        val hasCameraAccess = packageManager.checkPermission(
            android.Manifest.permission.CAMERA,
            packageName
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasCameraAccess) {
            sensorTypes.add("camera")
        }

        val hasMicrophoneAccess = packageManager.checkPermission(
            android.Manifest.permission.RECORD_AUDIO,
            packageName
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasMicrophoneAccess) {
            sensorTypes.add("microphone")
        }

        return SensorAccess(
            packageName = packageName,
            appName = appName,
            sensorTypes = sensorTypes.distinct()
        )
    }
}

