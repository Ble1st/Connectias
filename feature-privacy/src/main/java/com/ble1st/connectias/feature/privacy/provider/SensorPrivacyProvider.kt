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

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
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
                    val hasSensorPermissions = packageInfo.requestedPermissions?.any { permission ->
                        permission.contains("SENSOR", ignoreCase = true) ||
                        permission == android.Manifest.permission.CAMERA ||
                        permission == android.Manifest.permission.RECORD_AUDIO
                    } == true

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

    private fun createSensorAccess(appInfo: ApplicationInfo): SensorAccess {
        val packageName = appInfo.packageName
        val appName = packageManager.getApplicationLabel(appInfo).toString()

        val sensorTypes = mutableListOf<String>()
        val availableSensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
        
        // Check which sensors are available (this doesn't tell us which apps use them,
        // but we can list available sensor types)
        availableSensors.forEach { sensor ->
            sensorTypes.add(sensor.name)
        }

        // Check camera and microphone permissions
        val hasCameraAccess = packageManager.checkPermission(
            android.Manifest.permission.CAMERA,
            packageName
        ) == PackageManager.PERMISSION_GRANTED

        val hasMicrophoneAccess = packageManager.checkPermission(
            android.Manifest.permission.RECORD_AUDIO,
            packageName
        ) == PackageManager.PERMISSION_GRANTED

        return SensorAccess(
            packageName = packageName,
            appName = appName,
            sensorTypes = sensorTypes.distinct(),
            hasCameraAccess = hasCameraAccess,
            hasMicrophoneAccess = hasMicrophoneAccess
        )
    }
}

