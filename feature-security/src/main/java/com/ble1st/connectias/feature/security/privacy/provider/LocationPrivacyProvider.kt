package com.ble1st.connectias.feature.security.privacy.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import com.ble1st.connectias.feature.security.privacy.models.LocationAccess
import com.ble1st.connectias.feature.security.privacy.models.LocationPermissionLevel
import com.ble1st.connectias.feature.security.privacy.models.LocationPrivacyInfo
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

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }
    /**
     * Gets location privacy information.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getLocationPrivacyInfo(): LocationPrivacyInfo = withContext(Dispatchers.IO) {
        try {
            val mockLocationEnabled = isMockLocationEnabled()
            val locationMgr = locationManager
            val gpsEnabled = locationMgr?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val networkLocationEnabled = locationMgr?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            val locationServicesEnabled = gpsEnabled || networkLocationEnabled
            
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

    /**
     * Checks if mock location is enabled using deprecated Settings API.
     * 
     * @deprecated Settings.Secure.ALLOW_MOCK_LOCATION is ineffective on API 23+.
     * Mock detection should be done per-Location via Location.isFromMockProvider() (API 18+)
     * in location callbacks where Location objects are available.
     * This method returns false on modern Android versions as the settings-based approach
     * is unreliable.
     */
    @Deprecated(
        message = "Use Location.isFromMockProvider() for per-location mock detection",
        level = DeprecationLevel.WARNING
    )
    private fun isMockLocationEnabled(): Boolean {
        // Settings.Secure.ALLOW_MOCK_LOCATION is ineffective on API 23+
        // Return false and rely on per-Location mock detection via Location.isFromMockProvider()
        return false
    }

    private fun getAppsWithLocationAccess(): List<LocationAccess> {
        return try {
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                ))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
            installedPackages
                .filter { it.requestedPermissions != null }
                .mapNotNull { packageInfo ->
                    try {
                        val hasFineLocation = packageInfo.requestedPermissions?.contains(
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == true
                        val hasCoarseLocation = packageInfo.requestedPermissions?.contains(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == true

                        if (hasFineLocation || hasCoarseLocation) {
                            // Check if permission is actually granted
                            val fineGranted = packageManager.checkPermission(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                packageInfo.packageName
                            ) == PackageManager.PERMISSION_GRANTED

                            val coarseGranted = packageManager.checkPermission(
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                packageInfo.packageName
                            ) == PackageManager.PERMISSION_GRANTED

                            val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                packageManager.checkPermission(
                                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                    packageInfo.packageName
                                ) == PackageManager.PERMISSION_GRANTED
                            } else {
                                false
                            }

                            val appInfo = packageInfo.applicationInfo
                            val appName = appInfo?.let { packageManager.getApplicationLabel(it).toString() } 
                                ?: packageInfo.packageName
                            
                            // Determine foreground permission level (highest granted)
                            val permissionLevel = when {
                                fineGranted -> LocationPermissionLevel.FINE
                                coarseGranted -> LocationPermissionLevel.COARSE
                                else -> LocationPermissionLevel.NONE
                            }
                            
                            LocationAccess(
                                packageName = packageInfo.packageName,
                                appName = appName,
                                hasFineLocation = fineGranted,
                                hasCoarseLocation = coarseGranted,
                                permissionLevel = permissionLevel,
                                hasBackgroundAccess = backgroundGranted
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error processing package ${packageInfo.packageName} for location access")
                        null
                    }
                }
        } catch (e: android.os.BadParcelableException) {
            Timber.e(e, "Binder transaction failed: too many packages. Returning empty list.")
            emptyList()
        } catch (e: android.os.DeadSystemException) {
            Timber.e(e, "Binder transaction failed: system is dead. Returning empty list.")
            emptyList()
        } catch (e: android.os.DeadObjectException) {
            Timber.e(e, "Binder transaction failed: remote process died or buffer full. Returning empty list.")
            emptyList()
        } catch (e: RuntimeException) {
            // Catch DeadSystemRuntimeException and other runtime exceptions
            if (e.cause is android.os.DeadSystemException) {
                Timber.e(e, "Binder transaction failed: system runtime is dead. Returning empty list.")
            } else {
                Timber.e(e, "Runtime exception during binder transaction. Returning empty list.")
            }
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error getting apps with location access")
            emptyList()
        }
    }
}

