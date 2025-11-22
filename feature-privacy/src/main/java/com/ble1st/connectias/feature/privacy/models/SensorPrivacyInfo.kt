package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Sensor privacy information including active sensor accesses.
 */
@Parcelize
data class SensorPrivacyInfo(
    val activeSensorAccesses: List<SensorAccess>,
    val totalAppsWithSensorAccess: Int
) : Parcelable {
    init {
        // Validate consistency: totalAppsWithSensorAccess should match distinct package count
        require(totalAppsWithSensorAccess >= 0) { "totalAppsWithSensorAccess must be non-negative" }
        require(totalAppsWithSensorAccess == activeSensorAccesses.distinctBy { it.packageName }.size) {
            "totalAppsWithSensorAccess must match distinct package count"
        }
    }
    
    /**
     * Computed property for total apps with sensor access.
     * Returns the count of distinct packages in activeSensorAccesses.
     */
    val totalAppsWithSensorAccessComputed: Int
        get() = activeSensorAccesses.distinctBy { it.packageName }.size
}

/**
 * Information about an app's access to sensors.
 */
@Parcelize
data class SensorAccess(
    val packageName: String,
    val appName: String,
    val sensorTypes: List<String>
) : Parcelable {
    init {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(appName.isNotBlank()) { "App name cannot be blank" }
    }
    
    /**
     * Whether the app has camera access.
     * Derived from sensorTypes containing "camera".
     */
    val hasCameraAccess: Boolean
        get() = "camera" in sensorTypes
    
    /**
     * Whether the app has microphone access.
     * Derived from sensorTypes containing "microphone".
     */
    val hasMicrophoneAccess: Boolean
        get() = "microphone" in sensorTypes
}

