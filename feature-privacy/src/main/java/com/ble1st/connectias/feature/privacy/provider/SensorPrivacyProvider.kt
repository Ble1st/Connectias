package com.ble1st.connectias.feature.privacy.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.SensorManager
import com.ble1st.connectias.feature.privacy.models.SensorAccess
import com.ble1st.connectias.feature.privacy.models.SensorPrivacyInfo
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
            val activeSensorAccesses = appsWithSensorAccess.map { appInfo ->
                createSensorAccess(appInfo)
            }

            SensorPrivacyInfo(
                activeSensorAccesses = activeSensorAccesses,
                totalAppsWithSensorAccess = activeSensorAccesses.size
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting sensor privacy info")
            SensorPrivacyInfo(
                activeSensorAccesses = emptyList(),
                totalAppsWithSensorAccess = 0
            )
        }
    }

    private fun getAppsWithSensorAccess(): List<ApplicationInfo> {
        return try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            installedPackages
                .filter { it.requestedPermissions != null }
                .mapNotNull { packageInfo ->
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
                }
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
        
        // STEP_COUNTER and STEP_DETECTOR permissions indicate step counter access
        if (packageManager.checkPermission(
                android.Manifest.permission.ACTIVITY_RECOGNITION,
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

