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
) : Parcelable

/**
 * Information about an app's access to sensors.
 */
@Parcelize
data class SensorAccess(
    val packageName: String,
    val appName: String,
    val sensorTypes: List<String>,
    val hasCameraAccess: Boolean,
    val hasMicrophoneAccess: Boolean
) : Parcelable

