package com.ble1st.connectias.feature.network.repository

import com.ble1st.connectias.feature.network.models.NetworkAnalysis
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.NetworkResult
import com.ble1st.connectias.feature.network.models.ParcelableList
import com.ble1st.connectias.feature.network.models.WifiNetwork
import com.ble1st.connectias.feature.network.provider.LanScannerProvider
import com.ble1st.connectias.feature.network.provider.NetworkAnalysisProvider
import com.ble1st.connectias.feature.network.provider.WifiScannerProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that aggregates all network providers and provides caching.
 * Thread-safe operations using Mutex for atomic cache operations.
 * Returns Result types to distinguish between success and error states.
 */
@Singleton
class NetworkRepository @Inject constructor(
    private val wifiScannerProvider: WifiScannerProvider,
    private val lanScannerProvider: LanScannerProvider,
    private val networkAnalysisProvider: NetworkAnalysisProvider
) {
    // Cache for network information (nullable properties instead of StateFlow)
    private var wifiNetworksCache: NetworkResult<ParcelableList<WifiNetwork>>? = null
    
    private var localDevicesCache: NetworkResult<ParcelableList<NetworkDevice>>? = null
    
    private var networkAnalysisCache: NetworkResult<NetworkAnalysis>? = null
    
    // Mutexes for thread-safe cache access
    private val wifiNetworksMutex = Mutex()
    private val localDevicesMutex = Mutex()
    private val networkAnalysisMutex = Mutex()
    
    /**
     * Generic helper for cached data retrieval with Result types.
     * Ensures atomic cache operations to prevent race conditions.
     */
    private suspend fun <T> getCached(
        mutex: Mutex,
        cache: () -> NetworkResult<T>?,
        setCache: (NetworkResult<T>) -> Unit,
        fetch: suspend () -> NetworkResult<T>
    ): NetworkResult<T> = mutex.withLock {
        Timber.d("Checking cache for data")
        val cached = cache()
        if (cached != null) {
            Timber.d("Cache hit: returning cached data")
            when (cached) {
                is NetworkResult.Success -> Timber.d("Cached data: Success")
                is NetworkResult.Error -> Timber.d("Cached data: Error - ${cached.message}")
            }
            return@withLock cached
        }
        
        Timber.d("Cache miss: fetching fresh data")
        val fresh = try {
            val result = fetch()
            Timber.d("Fetch completed: ${if (result is NetworkResult.Success) "Success" else "Error"}")
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch data")
            NetworkResult.Error.fromThrowable(e)
        }
        
        // Only cache successful results to avoid persisting permission/network errors
        if (fresh is NetworkResult.Success) {
            Timber.d("Caching successful result")
            setCache(fresh)
        } else {
            Timber.d("Skipping cache update due to error result")
        }
        fresh
    }
    
    /**
     * Gets Wi‑Fi networks with caching.
     * Empty list is treated as a successful result (no networks found is not an error).
     */
    suspend fun getWifiNetworks(): NetworkResult<ParcelableList<WifiNetwork>> {
        Timber.d("getWifiNetworks() called")
        return getCached(
            mutex = wifiNetworksMutex,
            cache = { wifiNetworksCache },
            setCache = { wifiNetworksCache = it },
            fetch = {
                Timber.d("Fetching Wi‑Fi networks from provider")
                val result = wifiScannerProvider.scanWifiNetworks()
                when (result) {
                    is NetworkResult.Success -> {
                        Timber.d("Provider returned ${result.data.items.size} Wi‑Fi networks")
                        result
                    }
                    is NetworkResult.Error -> {
                        Timber.w("Provider returned error: ${result.message}")
                        result
                    }
                }
            }
        )
    }
    
    /**
     * Gets local network devices with caching.
     * Empty list is treated as a successful result (no devices found is not an error).
     */
    suspend fun getLocalNetworkDevices(): NetworkResult<ParcelableList<NetworkDevice>> {
        Timber.d("getLocalNetworkDevices() called")
        return getCached(
            mutex = localDevicesMutex,
            cache = { localDevicesCache },
            setCache = { localDevicesCache = it },
            fetch = {
                Timber.d("Fetching local network devices from provider")
                val result = lanScannerProvider.scanLocalNetwork()
                when (result) {
                    is NetworkResult.Success -> {
                        Timber.d("Provider returned ${result.data.items.size} local network devices")
                        result
                    }
                    is NetworkResult.Error -> {
                        Timber.w("Provider returned error: ${result.message}")
                        result
                    }
                }
            }
        )
    }
    
    /**
     * Gets network analysis with caching.
     * Returns Result to distinguish between real disconnected state and fetch failures.
     */
    suspend fun getNetworkAnalysis(): NetworkResult<NetworkAnalysis> {
        Timber.d("getNetworkAnalysis() called")
        return getCached(
            mutex = networkAnalysisMutex,
            cache = { networkAnalysisCache },
            setCache = { networkAnalysisCache = it },
            fetch = {
                try {
                    Timber.d("Fetching network analysis from provider")
                    val analysis = networkAnalysisProvider.getNetworkAnalysis()
                    Timber.d("Provider returned network analysis: connected=${analysis.isConnected}, type=${analysis.connectionType.name}, gateway=${analysis.gateway}, dnsCount=${analysis.dnsServers.size}")
                    NetworkResult.Success(analysis)
                } catch (e: Exception) {
                    // Critical exception - wrap it in Error result
                    Timber.e(e, "Failed to get network analysis")
                    NetworkResult.Error.fromThrowable(e)
                }
            }
        )
    }
    
    /**
     * Refreshes all cached data.
     * Invalidates cache and triggers fresh fetch.
     */
    suspend fun refreshAll() {
        Timber.d("refreshAll() called - invalidating all caches")
        // Atomic cache invalidation
        wifiNetworksMutex.withLock {
            wifiNetworksCache = null
            Timber.d("Wi‑Fi networks cache invalidated")
        }
        localDevicesMutex.withLock {
            localDevicesCache = null
            Timber.d("Local devices cache invalidated")
        }
        networkAnalysisMutex.withLock {
            networkAnalysisCache = null
            Timber.d("Network analysis cache invalidated")
        }
        
        // Trigger refresh by getting fresh data (errors are handled by Result types)
        Timber.d("Triggering fresh fetch for all data")
        getWifiNetworks()
        getLocalNetworkDevices()
        getNetworkAnalysis()
        Timber.d("refreshAll() completed")
    }
    
    /**
     * Refreshes Wi‑Fi networks cache.
     * Invalidates cache and triggers fresh fetch.
     * @return NetworkResult with fresh Wi‑Fi networks data
     */
    suspend fun refreshWifiNetworks(): NetworkResult<ParcelableList<WifiNetwork>> {
        Timber.d("refreshWifiNetworks() called")
        wifiNetworksMutex.withLock {
            wifiNetworksCache = null
            Timber.d("Wi‑Fi networks cache invalidated")
        }
        // Trigger refresh and return result directly (errors are handled by Result types)
        val result = getWifiNetworks()
        Timber.d("refreshWifiNetworks() completed: ${if (result is NetworkResult.Success) "Success (${result.data.items.size} networks)" else "Error"}")
        return result
    }
    
    /**
     * Refreshes local network devices cache.
     * Invalidates cache and triggers fresh fetch.
     * @return NetworkResult with fresh local network devices data
     */
    suspend fun refreshLocalDevices(): NetworkResult<ParcelableList<NetworkDevice>> {
        Timber.d("refreshLocalDevices() called")
        localDevicesMutex.withLock {
            localDevicesCache = null
            Timber.d("Local devices cache invalidated")
        }
        // Trigger refresh and return result directly (errors are handled by Result types)
        val result = getLocalNetworkDevices()
        Timber.d("refreshLocalDevices() completed: ${if (result is NetworkResult.Success) "Success (${result.data.items.size} devices)" else "Error"}")
        return result
    }
    
    /**
     * Refreshes network analysis cache.
     * Invalidates cache and triggers fresh fetch.
     * @return NetworkResult with fresh network analysis data
     */
    suspend fun refreshAnalysis(): NetworkResult<NetworkAnalysis> {
        Timber.d("refreshAnalysis() called")
        networkAnalysisMutex.withLock {
            networkAnalysisCache = null
            Timber.d("Network analysis cache invalidated")
        }
        // Trigger refresh and return result directly (errors are handled by Result types)
        val result = getNetworkAnalysis()
        Timber.d("refreshAnalysis() completed: ${if (result is NetworkResult.Success) "Success" else "Error"}")
        return result
    }
}

