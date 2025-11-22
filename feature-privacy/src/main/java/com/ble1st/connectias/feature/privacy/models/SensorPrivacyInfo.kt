package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Sensor privacy information including apps with sensor permissions.
 * 
 * Note: The list represents apps with granted sensor permissions, not real-time active usage.
 * Android doesn't provide a direct API to query which sensors an app is actively using.
 */
@Parcelize
data class SensorPrivacyInfo(
    val potentialSensorAccesses: List<SensorAccess>
) : Parcelable {
    
    val totalAppsWithSensorAccess: Int
        get() = potentialSensorAccesses.distinctBy { it.packageName }.size
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

