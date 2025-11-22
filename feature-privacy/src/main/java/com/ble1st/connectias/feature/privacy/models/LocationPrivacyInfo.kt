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
     * This represents the OS-level setting from the Android system.
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
 * 
 * Note: Background location permission (ACCESS_BACKGROUND_LOCATION) can be granted
 * in addition to foreground permissions (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION),
 * not as a mutually exclusive level. This model correctly represents apps that may have
 * both foreground and background location access simultaneously.
 */
@Parcelize
data class LocationAccess(
    val packageName: String,
    val appName: String,
    val hasFineLocation: Boolean,
    val hasCoarseLocation: Boolean,
    /**
     * Foreground location permission level.
     * Represents the highest foreground permission granted (FINE > COARSE > NONE).
     * Background access is tracked separately via hasBackgroundAccess.
     */
    val permissionLevel: LocationPermissionLevel,
    /**
     * Whether the app has background location access (ACCESS_BACKGROUND_LOCATION).
     * This can be true in addition to foreground permissions (hasFineLocation/hasCoarseLocation).
     * Available on Android 10+ (API 29+).
     * Background location requires a foreground location permission as a prerequisite.
     */
    val hasBackgroundAccess: Boolean = false
) : Parcelable

/**
 * Foreground location permission level.
 * Represents the highest foreground permission granted.
 * Note: Background location access is tracked separately and can coexist with any foreground level.
 */
enum class LocationPermissionLevel {
    NONE,
    COARSE,    // ACCESS_COARSE_LOCATION
    FINE       // ACCESS_FINE_LOCATION
}

