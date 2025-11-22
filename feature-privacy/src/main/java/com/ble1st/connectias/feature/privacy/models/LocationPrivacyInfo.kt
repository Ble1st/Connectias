package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Location privacy information including mock location and app accesses.
 */
@Parcelize
data class LocationPrivacyInfo(
    val mockLocationEnabled: Boolean,
    val locationServicesEnabled: Boolean,
    val appsWithLocationAccess: List<LocationAccess>,
    val gpsEnabled: Boolean,
    val networkLocationEnabled: Boolean
) : Parcelable

/**
 * Information about an app's location access.
 */
@Parcelize
data class LocationAccess(
    val packageName: String,
    val appName: String,
    val hasFineLocation: Boolean,
    val hasCoarseLocation: Boolean,
    val permissionLevel: LocationPermissionLevel
) : Parcelable

/**
 * Location permission level.
 */
enum class LocationPermissionLevel {
    NONE,
    COARSE,    // ACCESS_COARSE_LOCATION
    FINE        // ACCESS_FINE_LOCATION
}

