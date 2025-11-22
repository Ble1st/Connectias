package com.ble1st.connectias.feature.privacy.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import com.ble1st.connectias.feature.privacy.models.LocationAccess
import com.ble1st.connectias.feature.privacy.models.LocationPermissionLevel
import com.ble1st.connectias.feature.privacy.models.LocationPrivacyInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides location privacy information including mock location and app accesses.
 */
@Singleton
class LocationPrivacyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager by lazy {
        context.packageManager
    }

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    /**
     * Gets location privacy information.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getLocationPrivacyInfo(): LocationPrivacyInfo = withContext(Dispatchers.IO) {
        try {
            val mockLocationEnabled = isMockLocationEnabled()
            val locationServicesEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkLocationEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            val appsWithLocationAccess = getAppsWithLocationAccess()

            LocationPrivacyInfo(
                mockLocationEnabled = mockLocationEnabled,
                locationServicesEnabled = locationServicesEnabled,
                appsWithLocationAccess = appsWithLocationAccess,
                gpsEnabled = gpsEnabled,
                networkLocationEnabled = networkLocationEnabled
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting location privacy info")
            LocationPrivacyInfo(
                mockLocationEnabled = false,
                locationServicesEnabled = false,
                appsWithLocationAccess = emptyList(),
                gpsEnabled = false,
                networkLocationEnabled = false
            )
        }
    }

    private fun isMockLocationEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION,
                0
            ) != 0
        } catch (e: Exception) {
            Timber.w(e, "Error checking mock location")
            false
        }
    }

    private fun getAppsWithLocationAccess(): List<LocationAccess> {
        return try {
            val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            installedPackages
                .filter { it.requestedPermissions != null }
                .mapNotNull { packageInfo ->
                    val hasFineLocation = packageInfo.requestedPermissions?.contains(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == true
                    val hasCoarseLocation = packageInfo.requestedPermissions?.contains(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == true

                    if (hasFineLocation || hasCoarseLocation) {
                        val permissionLevel = when {
                            hasFineLocation -> LocationPermissionLevel.FINE
                            hasCoarseLocation -> LocationPermissionLevel.COARSE
                            else -> LocationPermissionLevel.NONE
                        }

                        // Check if permission is actually granted
                        val fineGranted = packageManager.checkPermission(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            packageInfo.packageName
                        ) == PackageManager.PERMISSION_GRANTED

                        val coarseGranted = packageManager.checkPermission(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            packageInfo.packageName
                        ) == PackageManager.PERMISSION_GRANTED

                        val appInfo = packageInfo.applicationInfo
                        val appName = appInfo?.let { packageManager.getApplicationLabel(it).toString() } 
                            ?: packageInfo.packageName
                        
                        LocationAccess(
                            packageName = packageInfo.packageName,
                            appName = appName,
                            hasFineLocation = fineGranted,
                            hasCoarseLocation = coarseGranted,
                            permissionLevel = if (fineGranted) LocationPermissionLevel.FINE
                                else if (coarseGranted) LocationPermissionLevel.COARSE
                                else LocationPermissionLevel.NONE
                        )
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error getting apps with location access")
            emptyList()
        }
    }
}

