package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Location privacy information including mock location and app accesses.
 */
@Parcelize
data class LocationPrivacyInfo(
    val mockLocationEnabled: Boolean,
    /**
     * Overall location services enabled status.
     * This represents the OS-level setting and is derived from gpsEnabled || networkLocationEnabled.
     * It may differ from individual provider statuses (gpsEnabled/networkLocationEnabled) in edge cases
     * where the system-level setting is disabled but individual providers may still be configured.
     */
    val locationServicesEnabled: Boolean,
    val appsWithLocationAccess: List<LocationAccess>,
    /**
     * GPS provider enabled status.
     * Represents the individual GPS provider status, independent of the overall location services setting.
     */
    val gpsEnabled: Boolean,
    /**
     * Network location provider enabled status.
     * Represents the individual network location provider status, independent of the overall location services setting.
     */
    val networkLocationEnabled: Boolean
) : Parcelable {
    /**
     * Computed property for location services enabled status.
     * Returns true if either GPS or network location provider is enabled.
     */
    val isLocationServicesActive: Boolean
        get() = gpsEnabled || networkLocationEnabled
}

/**
 * Information about an app's location access.
 */
@Parcelize
data class LocationAccess(
    val packageName: String,
    val appName: String,
    val hasFineLocation: Boolean,
    val hasCoarseLocation: Boolean,
    val permissionLevel: LocationPermissionLevel,
    /**
     * Whether the app has background location access (ACCESS_BACKGROUND_LOCATION).
     * Available on Android 10+ (API 29+).
     */
    val hasBackgroundAccess: Boolean = false
) : Parcelable

/**
 * Location permission level.
 */
enum class LocationPermissionLevel {
    NONE,
    COARSE,    // ACCESS_COARSE_LOCATION
    FINE,      // ACCESS_FINE_LOCATION
    BACKGROUND // ACCESS_BACKGROUND_LOCATION (Android 10+)
}

