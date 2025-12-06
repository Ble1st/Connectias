package com.ble1st.connectias.feature.security.privacy.models

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
    val totalRunningServices: Int,
    /**
     * Indicates if the data is incomplete due to errors during retrieval.
     * When true, some information may be missing (e.g., empty lists) due to
     * recoverable errors (like package filtering issues). When false, all data
     * was successfully retrieved.
     */
    val isIncomplete: Boolean = false
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

