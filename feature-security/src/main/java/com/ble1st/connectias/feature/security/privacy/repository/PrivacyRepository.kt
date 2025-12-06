package com.ble1st.connectias.feature.security.privacy.repository

import com.ble1st.connectias.feature.security.privacy.models.AppPermissionInfo
import com.ble1st.connectias.feature.security.privacy.models.BackgroundActivityInfo
import com.ble1st.connectias.feature.security.privacy.models.LocationPrivacyInfo
import com.ble1st.connectias.feature.security.privacy.models.NetworkPrivacyInfo
import com.ble1st.connectias.feature.security.privacy.models.PrivacyLevel
import com.ble1st.connectias.feature.security.privacy.models.PrivacyStatus
import com.ble1st.connectias.feature.security.privacy.models.SensorPrivacyInfo
import com.ble1st.connectias.feature.security.privacy.models.StoragePrivacyInfo
import com.ble1st.connectias.feature.security.privacy.provider.AppPermissionsProvider
import com.ble1st.connectias.feature.security.privacy.provider.BackgroundActivityProvider
import com.ble1st.connectias.feature.security.privacy.provider.LocationPrivacyProvider
import com.ble1st.connectias.feature.security.privacy.provider.NetworkPrivacyProvider
import com.ble1st.connectias.feature.security.privacy.provider.SensorPrivacyProvider
import com.ble1st.connectias.feature.security.privacy.provider.StoragePrivacyProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that aggregates all privacy providers and provides Flow-based updates.
 * Implements caching for performance optimization.
 */
@Singleton
class PrivacyRepository @Inject constructor(
    private val networkPrivacyProvider: NetworkPrivacyProvider,
    private val sensorPrivacyProvider: SensorPrivacyProvider,
    private val locationPrivacyProvider: LocationPrivacyProvider,
    private val appPermissionsProvider: AppPermissionsProvider,
    private val backgroundActivityProvider: BackgroundActivityProvider,
    private val storagePrivacyProvider: StoragePrivacyProvider
) {
    // Cache for privacy information
    private val networkPrivacyCache = MutableStateFlow<NetworkPrivacyInfo?>(null)
    private val sensorPrivacyCache = MutableStateFlow<SensorPrivacyInfo?>(null)
    private val locationPrivacyCache = MutableStateFlow<LocationPrivacyInfo?>(null)
    private val appPermissionsCache = MutableStateFlow<List<AppPermissionInfo>?>(null)
    private val backgroundActivityCache = MutableStateFlow<BackgroundActivityInfo?>(null)
    private val storagePrivacyCache = MutableStateFlow<StoragePrivacyInfo?>(null)
    
    // Mutexes for thread-safe cache access
    private val networkPrivacyMutex = Mutex()
    private val sensorPrivacyMutex = Mutex()
    private val locationPrivacyMutex = Mutex()
    private val appPermissionsMutex = Mutex()
    private val backgroundActivityMutex = Mutex()
    private val storagePrivacyMutex = Mutex()

    /**
     * Gets network privacy information with caching.
     */
    suspend fun getNetworkPrivacyInfo(): NetworkPrivacyInfo = networkPrivacyMutex.withLock {
        val cached = networkPrivacyCache.value
        if (cached != null) {
            return@withLock cached
        }
        
        val fresh = networkPrivacyProvider.getNetworkPrivacyInfo()
        networkPrivacyCache.value = fresh
        fresh
    }

    /**
     * Gets sensor privacy information with caching.
     */
    suspend fun getSensorPrivacyInfo(): SensorPrivacyInfo = sensorPrivacyMutex.withLock {
        val cached = sensorPrivacyCache.value
        if (cached != null) {
            return@withLock cached
        }
        
        val fresh = sensorPrivacyProvider.getSensorPrivacyInfo()
        sensorPrivacyCache.value = fresh
        fresh
    }

    /**
     * Gets location privacy information with caching.
     */
    suspend fun getLocationPrivacyInfo(): LocationPrivacyInfo = locationPrivacyMutex.withLock {
        val cached = locationPrivacyCache.value
        if (cached != null) {
            return@withLock cached
        }
        
        val fresh = locationPrivacyProvider.getLocationPrivacyInfo()
        locationPrivacyCache.value = fresh
        fresh
    }

    /**
     * Gets app permissions information with caching.
     */
    suspend fun getAppPermissionsInfo(): List<AppPermissionInfo> = appPermissionsMutex.withLock {
        val cached = appPermissionsCache.value
        if (cached != null) {
            return@withLock cached
        }
        
        val fresh = appPermissionsProvider.getAppPermissionsInfo()
        appPermissionsCache.value = fresh
        fresh
    }

    /**
     * Gets background activity information with caching.
     */
    suspend fun getBackgroundActivityInfo(): BackgroundActivityInfo = backgroundActivityMutex.withLock {
        val cached = backgroundActivityCache.value
        if (cached != null) {
            return@withLock cached
        }
        
        val fresh = backgroundActivityProvider.getBackgroundActivityInfo()
        backgroundActivityCache.value = fresh
        fresh
    }

    /**
     * Gets storage privacy information with caching.
     */
    suspend fun getStoragePrivacyInfo(): StoragePrivacyInfo = storagePrivacyMutex.withLock {
        val cached = storagePrivacyCache.value
        if (cached != null) {
            return@withLock cached
        }
        
        val fresh = storagePrivacyProvider.getStoragePrivacyInfo()
        storagePrivacyCache.value = fresh
        fresh
    }

    /**
     * Gets overall privacy status aggregating all categories.
     */
    suspend fun getOverallPrivacyStatus(): PrivacyStatus {
        return try {
            coroutineScope {
                // Fetch all privacy information in parallel
                val networkInfoDeferred = async { getNetworkPrivacyInfo() }
                val sensorInfoDeferred = async { getSensorPrivacyInfo() }
                val locationInfoDeferred = async { getLocationPrivacyInfo() }
                val appPermissionsInfoDeferred = async { getAppPermissionsInfo() }
                val backgroundInfoDeferred = async { getBackgroundActivityInfo() }
                val storageInfoDeferred = async { getStoragePrivacyInfo() }

                // Await all results
                val networkInfo = networkInfoDeferred.await()
                val sensorInfo = sensorInfoDeferred.await()
                val locationInfo = locationInfoDeferred.await()
                val appPermissionsInfo = appPermissionsInfoDeferred.await()
                val backgroundInfo = backgroundInfoDeferred.await()
                val storageInfo = storageInfoDeferred.await()

                val networkLevel = calculateNetworkPrivacyLevel(networkInfo)
                val sensorLevel = calculateSensorPrivacyLevel(sensorInfo)
                val locationLevel = calculateLocationPrivacyLevel(locationInfo)
                val permissionsLevel = calculatePermissionsPrivacyLevel(appPermissionsInfo)
                val backgroundLevel = calculateBackgroundPrivacyLevel(backgroundInfo)
                val storageLevel = calculateStoragePrivacyLevel(storageInfo)

                val overallLevel = calculateOverallLevel(
                    networkLevel, sensorLevel, locationLevel,
                    permissionsLevel, backgroundLevel, storageLevel
                )

                PrivacyStatus(
                    networkPrivacy = networkLevel,
                    sensorPrivacy = sensorLevel,
                    locationPrivacy = locationLevel,
                    permissionsPrivacy = permissionsLevel,
                    backgroundPrivacy = backgroundLevel,
                    storagePrivacy = storageLevel,
                    overallLevel = overallLevel
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting overall privacy status")
            PrivacyStatus(
                networkPrivacy = PrivacyLevel.UNKNOWN,
                sensorPrivacy = PrivacyLevel.UNKNOWN,
                locationPrivacy = PrivacyLevel.UNKNOWN,
                permissionsPrivacy = PrivacyLevel.UNKNOWN,
                backgroundPrivacy = PrivacyLevel.UNKNOWN,
                storagePrivacy = PrivacyLevel.UNKNOWN,
                overallLevel = PrivacyLevel.UNKNOWN
            )
        }
    }

    /**
     * Refreshes all privacy information by clearing cache.
     */
    fun refreshAll() {
        networkPrivacyCache.value = null
        sensorPrivacyCache.value = null
        locationPrivacyCache.value = null
        appPermissionsCache.value = null
        backgroundActivityCache.value = null
        storagePrivacyCache.value = null
    }

    private fun calculateNetworkPrivacyLevel(info: NetworkPrivacyInfo): PrivacyLevel {
        return when {
            !info.isConnected -> PrivacyLevel.WARNING
            info.vpnActive && info.privateDnsEnabled -> PrivacyLevel.SECURE
            info.privateDnsEnabled -> PrivacyLevel.SECURE
            info.dnsStatus == com.ble1st.connectias.feature.security.privacy.models.DNSStatus.STANDARD -> PrivacyLevel.WARNING
            info.dnsStatus == com.ble1st.connectias.feature.security.privacy.models.DNSStatus.UNKNOWN -> PrivacyLevel.UNKNOWN
            else -> PrivacyLevel.SECURE
        }
    }

    private fun calculateSensorPrivacyLevel(info: SensorPrivacyInfo): PrivacyLevel {
        return when {
            info.totalAppsWithSensorAccess == 0 -> PrivacyLevel.SECURE
            info.totalAppsWithSensorAccess <= 3 -> PrivacyLevel.WARNING
            else -> PrivacyLevel.CRITICAL
        }
    }

    private fun calculateLocationPrivacyLevel(info: LocationPrivacyInfo): PrivacyLevel {
        return when {
            info.mockLocationEnabled -> PrivacyLevel.CRITICAL
            info.appsWithLocationAccess.isEmpty() -> PrivacyLevel.SECURE
            info.appsWithLocationAccess.size <= 3 -> PrivacyLevel.WARNING
            else -> PrivacyLevel.CRITICAL
        }
    }

    private fun calculatePermissionsPrivacyLevel(info: List<AppPermissionInfo>): PrivacyLevel {
        val highRiskApps = info.count { it.riskLevel == com.ble1st.connectias.feature.security.privacy.models.PermissionRiskLevel.HIGH }
        return when {
            highRiskApps == 0 -> PrivacyLevel.SECURE
            highRiskApps <= 2 -> PrivacyLevel.WARNING
            else -> PrivacyLevel.CRITICAL
        }
    }

    private fun calculateBackgroundPrivacyLevel(info: BackgroundActivityInfo): PrivacyLevel {
        return when {
            info.totalRunningServices == 0 -> PrivacyLevel.SECURE
            info.totalRunningServices <= 5 -> PrivacyLevel.WARNING
            else -> PrivacyLevel.CRITICAL
        }
    }

    private fun calculateStoragePrivacyLevel(info: StoragePrivacyInfo): PrivacyLevel {
        return when {
            info.scopedStorageEnabled && !info.legacyStorageMode -> PrivacyLevel.SECURE
            info.appsWithStorageAccess.isEmpty() -> PrivacyLevel.SECURE
            info.appsWithStorageAccess.size <= 5 -> PrivacyLevel.WARNING
            else -> PrivacyLevel.CRITICAL
        }
    }

    private fun calculateOverallLevel(
        network: PrivacyLevel,
        sensor: PrivacyLevel,
        location: PrivacyLevel,
        permissions: PrivacyLevel,
        background: PrivacyLevel,
        storage: PrivacyLevel
    ): PrivacyLevel {
        val levels = listOf(network, sensor, location, permissions, background, storage)
        val unknownCount = levels.count { it == PrivacyLevel.UNKNOWN }
        val criticalCount = levels.count { it == PrivacyLevel.CRITICAL }
        val warningCount = levels.count { it == PrivacyLevel.WARNING }

        return when {
            unknownCount == levels.size -> PrivacyLevel.UNKNOWN
            criticalCount > 0 -> PrivacyLevel.CRITICAL
            warningCount >= 3 -> PrivacyLevel.WARNING
            unknownCount > 0 -> PrivacyLevel.WARNING
            else -> PrivacyLevel.SECURE
        }
    }}

