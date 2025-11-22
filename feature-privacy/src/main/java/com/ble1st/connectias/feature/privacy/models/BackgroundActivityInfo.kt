package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Background activity information including services and restrictions.
 */
@Parcelize
data class BackgroundActivityInfo(
    val runningServices: List<RunningService>,
    val appsWithBackgroundRestrictions: List<String>,
    val appsIgnoringBatteryOptimization: List<String>,
    val totalRunningServices: Int
) : Parcelable

/**
 * Information about a running background service.
 */
@Parcelize
data class RunningService(
    val packageName: String,
    val appName: String,
    val serviceName: String
) : Parcelable

