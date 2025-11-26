package com.ble1st.connectias.feature.network.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.models.ConnectionType
import com.ble1st.connectias.feature.network.exceptions.PermissionDeniedException
import com.ble1st.connectias.feature.network.models.ErrorType
import com.ble1st.connectias.feature.network.models.NetworkAnalysis
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.NetworkResult
import com.ble1st.connectias.feature.network.models.WifiNetwork
import com.ble1st.connectias.feature.network.repository.NetworkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * ViewModel for Network Dashboard.
 * Manages network data state and refresh operations.
 */
@HiltViewModel
class NetworkDashboardViewModel @Inject constructor(
    private val networkRepository: NetworkRepository
) : ViewModel() {
    
    companion object {
        // Error message prefixes for different network components
        private const val ERROR_PREFIX_WIFI = "Wi‑Fi:"
        private const val ERROR_PREFIX_LAN_DEVICES = "LAN Devices:"
        private const val ERROR_PREFIX_ANALYSIS = "Analysis:"
    }
    
    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Loading)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    init {
        Timber.d("NetworkDashboardViewModel initialized")
        loadNetworkData()
    }
    
    /**
     * Loads all network data in parallel for better performance.
     * Handles each result individually to allow partial success.
     * Only shows loading state if current state is not Success (prevents flicker).
     */
    fun loadNetworkData() {
        Timber.d("loadNetworkData() called")
        viewModelScope.launch {
            val currentState = _networkState.value
            Timber.d("Current state: ${currentState::class.simpleName}")
            if (currentState !is NetworkState.Success) {
                Timber.d("Setting state to Loading")
                _networkState.value = NetworkState.Loading
            } else {
                Timber.d("Keeping current Success state to prevent flicker")
            }
            
            supervisorScope {
                Timber.d("Starting parallel data fetching")
                // Execute all repository calls in parallel
                val wifiNetworksDeferred = async { 
                    Timber.d("Starting Wi‑Fi networks fetch")
                    runCatching { networkRepository.getWifiNetworks() }
                }
                val devicesDeferred = async { 
                    Timber.d("Starting local devices fetch")
                    runCatching { networkRepository.getLocalNetworkDevices() }
                }
                val analysisDeferred = async { 
                    Timber.d("Starting network analysis fetch")
                    runCatching { networkRepository.getNetworkAnalysis() }
                }
                
                Timber.d("Awaiting all parallel results")
                // Handle each result individually
                val wifiResult = wifiNetworksDeferred.await().getOrElse { 
                    Timber.e(it, "Wi‑Fi networks fetch threw exception")
                    NetworkResult.Error.fromThrowable(it)
                }
                Timber.d("Wi‑Fi networks result: ${when (wifiResult) {
                    is NetworkResult.Success -> "Success (${wifiResult.data.items.size} networks)"
                    is NetworkResult.Error -> "Error: ${wifiResult.message}"
                }}")
                
                val devicesResult = devicesDeferred.await().getOrElse { 
                    Timber.e(it, "Local devices fetch threw exception")
                    NetworkResult.Error.fromThrowable(it)
                }
                Timber.d("Local devices result: ${when (devicesResult) {
                    is NetworkResult.Success -> "Success (${devicesResult.data.items.size} devices)"
                    is NetworkResult.Error -> "Error: ${devicesResult.message}"
                }}")
                
                val analysisResult = analysisDeferred.await().getOrElse { 
                    Timber.e(it, "Network analysis fetch threw exception")
                    NetworkResult.Error.fromThrowable(it)
                }
                Timber.d("Network analysis result: ${when (analysisResult) {
                    is NetworkResult.Success -> "Success"
                    is NetworkResult.Error -> "Error: ${analysisResult.message}"
                }}")
                
                // Extract data or use defaults
                val wifiNetworks = when (wifiResult) {
                    is NetworkResult.Success -> {
                        Timber.d("Extracted ${wifiResult.data.items.size} Wi‑Fi networks")
                        wifiResult.data.items
                    }
                    is NetworkResult.Error -> {
                        Timber.w("Failed to load Wi‑Fi networks: ${wifiResult.message}")
                        emptyList()
                    }
                }
                
                val devices = when (devicesResult) {
                    is NetworkResult.Success -> {
                        Timber.d("Extracted ${devicesResult.data.items.size} local devices")
                        devicesResult.data.items
                    }
                    is NetworkResult.Error -> {
                        Timber.w("Failed to load local devices: ${devicesResult.message}")
                        emptyList()
                    }
                }
                
                val analysis = when (analysisResult) {
                    is NetworkResult.Success -> {
                        Timber.d("Extracted network analysis: connected=${analysisResult.data.isConnected}, type=${analysisResult.data.connectionType.name}")
                        analysisResult.data
                    }
                    is NetworkResult.Error -> {
                        Timber.w("Network analysis failed, using default disconnected state: ${analysisResult.message}")
                        // Don't log as critical - empty analysis is a valid disconnected state
                        // Return default analysis for disconnected state
                        NetworkAnalysis(
                            isConnected = false,
                            dnsServers = emptyList(),
                            gateway = null,
                            networkSpeed = null,
                            connectionType = ConnectionType.NONE
                        )
                    }
                }
                
                // Aggregate all error messages from different components
                Timber.d("Aggregating error messages")
                val errorMessages = mutableListOf<String>()
                if (wifiResult is NetworkResult.Error) {
                    errorMessages.add("$ERROR_PREFIX_WIFI ${getUserFriendlyErrorMessage(wifiResult.errorType)}")
                }
                if (devicesResult is NetworkResult.Error) {
                    errorMessages.add("$ERROR_PREFIX_LAN_DEVICES ${getUserFriendlyErrorMessage(devicesResult.errorType)}")
                }
                if (analysisResult is NetworkResult.Error) {
                    errorMessages.add("$ERROR_PREFIX_ANALYSIS ${getUserFriendlyErrorMessage(analysisResult.errorType)}")
                }
                
                val aggregatedErrorMessage = if (errorMessages.isNotEmpty()) {
                    errorMessages.joinToString(" | ")
                } else {
                    null
                }
                
                if (aggregatedErrorMessage != null) {
                    Timber.d("Setting state to PartialSuccess with errors: $aggregatedErrorMessage")
                    _networkState.value = NetworkState.PartialSuccess(
                        wifiNetworks = wifiNetworks,
                        devices = devices,
                        analysis = analysis,
                        errorMessage = aggregatedErrorMessage
                    )
                } else {
                    Timber.d("Setting state to Success: ${wifiNetworks.size} networks, ${devices.size} devices")
                    _networkState.value = NetworkState.Success(
                        wifiNetworks = wifiNetworks,
                        devices = devices,
                        analysis = analysis
                    )
                }
                Timber.d("loadNetworkData() completed")
            }
        }
    }
    
    /**
     * Converts error type to user-friendly error message.
     */
    private fun getUserFriendlyErrorMessage(errorType: ErrorType): String {
        return when (errorType) {
            ErrorType.PermissionDenied -> 
                "Location permission is required for Wi‑Fi scanning"
            ErrorType.NetworkError -> 
                "Network error. Please check your connection and try again."
            ErrorType.Unknown -> 
                "Unable to load network information. Please try again."
        }
    }
    
    /**
     * Builds aggregated error message by combining new error with previous errors.
     * Replaces any existing error with the same prefix and preserves others.
     * 
     * @param newError The new error result (null if no error)
     * @param errorPrefix The prefix for the new error (e.g., "Wi‑Fi:", "LAN Devices:")
     * @param previousErrorMessage The previous aggregated error message (null if none)
     * @return Aggregated error message string, or null if no errors
     */
    private fun buildAggregatedErrorMessage(
        newError: NetworkResult.Error?,
        errorPrefix: String,
        previousErrorMessage: String?
    ): String? {
        val errorMessages = mutableListOf<String>()
        
        // Add new error if present
        if (newError != null) {
            errorMessages.add("$errorPrefix ${getUserFriendlyErrorMessage(newError.errorType)}")
        }
        
        // Preserve non-matching errors from previous state
        if (previousErrorMessage != null) {
            previousErrorMessage.split(" | ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { error ->
                    if (!error.startsWith(errorPrefix)) {
                        errorMessages.add(error)
                    }
                }
        }
        
        return if (errorMessages.isNotEmpty()) {
            errorMessages.joinToString(" | ")
        } else {
            null
        }
    }
    
    /**
     * Refreshes Wi‑Fi networks.
     * Updates state without showing loading flicker if data already exists.
     */
    fun refreshWifiNetworks() {
        Timber.d("refreshWifiNetworks() called")
        viewModelScope.launch {
            val wifiResult = runCatching { networkRepository.refreshWifiNetworks() }
                .getOrElse { 
                    Timber.e(it, "refreshWifiNetworks() threw exception")
                    NetworkResult.Error.fromThrowable(it)
                }
            
            Timber.d("Wi‑Fi refresh result: ${when (wifiResult) {
                is NetworkResult.Success -> "Success (${wifiResult.data.items.size} networks)"
                is NetworkResult.Error -> "Error: ${wifiResult.message}"
            }}")
            
            val currentState = _networkState.value
            Timber.d("Current state: ${currentState::class.simpleName}")
            val wifiNetworks = when (wifiResult) {
                is NetworkResult.Success -> {
                    Timber.d("Extracted ${wifiResult.data.items.size} Wi‑Fi networks from refresh")
                    wifiResult.data.items
                }
                is NetworkResult.Error -> {
                    Timber.w("Failed to refresh Wi‑Fi networks: ${wifiResult.message}")
                    if (currentState is NetworkState.NetworkDataHolder) {
                        Timber.d("Keeping existing ${currentState.wifiNetworks.size} Wi‑Fi networks due to error")
                        currentState.wifiNetworks // Keep existing data
                    } else {
                        Timber.d("No existing data, using empty list")
                        emptyList()
                    }
                }
            }
            
            if (currentState is NetworkState.NetworkDataHolder) {
                val previousErrorMessage = (currentState as? NetworkState.PartialSuccess)?.errorMessage
                Timber.d("Previous error message: $previousErrorMessage")
                val aggregatedErrorMessage = buildAggregatedErrorMessage(
                    newError = wifiResult as? NetworkResult.Error,
                    errorPrefix = ERROR_PREFIX_WIFI,
                    previousErrorMessage = previousErrorMessage
                )
                
                if (aggregatedErrorMessage != null) {
                    Timber.d("Updating state to PartialSuccess with error: $aggregatedErrorMessage")
                    _networkState.value = NetworkState.PartialSuccess(
                        wifiNetworks = wifiNetworks,
                        devices = currentState.devices,
                        analysis = currentState.analysis,
                        errorMessage = aggregatedErrorMessage
                    )
                } else {
                    Timber.d("Updating state to Success: ${wifiNetworks.size} networks, ${currentState.devices.size} devices")
                    _networkState.value = NetworkState.Success(
                        wifiNetworks = wifiNetworks,
                        devices = currentState.devices,
                        analysis = currentState.analysis
                    )
                }
            } else {
                Timber.d("Current state is not NetworkDataHolder, calling loadNetworkData()")
                loadNetworkData()
            }
            Timber.d("refreshWifiNetworks() completed")
        }
    }
    
    /**
     * Refreshes local network devices.
     * Updates state without showing loading flicker if data already exists.
     */
    fun refreshLocalDevices() {
        Timber.d("refreshLocalDevices() called")
        viewModelScope.launch {
            val devicesResult = runCatching { networkRepository.refreshLocalDevices() }
                .getOrElse { 
                    Timber.e(it, "refreshLocalDevices() threw exception")
                    NetworkResult.Error.fromThrowable(it)
                }
            
            Timber.d("Local devices refresh result: ${when (devicesResult) {
                is NetworkResult.Success -> "Success (${devicesResult.data.items.size} devices)"
                is NetworkResult.Error -> "Error: ${devicesResult.message}"
            }}")
            
            val currentState = _networkState.value
            Timber.d("Current state: ${currentState::class.simpleName}")
            val devices = when (devicesResult) {
                is NetworkResult.Success -> {
                    Timber.d("Extracted ${devicesResult.data.items.size} local devices from refresh")
                    devicesResult.data.items
                }
                is NetworkResult.Error -> {
                    Timber.w("Failed to refresh local devices: ${devicesResult.message}")
                    if (currentState is NetworkState.NetworkDataHolder) {
                        Timber.d("Keeping existing ${currentState.devices.size} local devices due to error")
                        currentState.devices // Keep existing data
                    } else {
                        Timber.d("No existing data, using empty list")
                        emptyList()
                    }
                }
            }
            
            if (currentState is NetworkState.NetworkDataHolder) {
                val previousErrorMessage = (currentState as? NetworkState.PartialSuccess)?.errorMessage
                Timber.d("Previous error message: $previousErrorMessage")
                val aggregatedErrorMessage = buildAggregatedErrorMessage(
                    newError = devicesResult as? NetworkResult.Error,
                    errorPrefix = ERROR_PREFIX_LAN_DEVICES,
                    previousErrorMessage = previousErrorMessage
                )
                
                if (aggregatedErrorMessage != null) {
                    Timber.d("Updating state to PartialSuccess with error: $aggregatedErrorMessage")
                    _networkState.value = NetworkState.PartialSuccess(
                        wifiNetworks = currentState.wifiNetworks,
                        devices = devices,
                        analysis = currentState.analysis,
                        errorMessage = aggregatedErrorMessage
                    )
                } else {
                    Timber.d("Updating state to Success: ${currentState.wifiNetworks.size} networks, ${devices.size} devices")
                    _networkState.value = NetworkState.Success(
                        wifiNetworks = currentState.wifiNetworks,
                        devices = devices,
                        analysis = currentState.analysis
                    )
                }
            } else {
                Timber.d("Current state is not NetworkDataHolder, calling loadNetworkData()")
                loadNetworkData()
            }
            Timber.d("refreshLocalDevices() completed")
        }
    }
    
    /**
     * Refreshes network analysis.
     * Updates state without showing loading flicker if data already exists.
     */
    fun refreshAnalysis() {
        Timber.d("refreshAnalysis() called")
        viewModelScope.launch {
            val analysisResult = runCatching { networkRepository.refreshAnalysis() }
                .getOrElse { 
                    Timber.e(it, "refreshAnalysis() threw exception")
                    NetworkResult.Error.fromThrowable(it)
                }
            
            Timber.d("Network analysis refresh result: ${when (analysisResult) {
                is NetworkResult.Success -> "Success (connected=${analysisResult.data.isConnected}, type=${analysisResult.data.connectionType.name})"
                is NetworkResult.Error -> "Error: ${analysisResult.message}"
            }}")
            
            val currentState = _networkState.value
            Timber.d("Current state: ${currentState::class.simpleName}")
            val analysis = when (analysisResult) {
                is NetworkResult.Success -> {
                    Timber.d("Extracted network analysis from refresh: connected=${analysisResult.data.isConnected}, gateway=${analysisResult.data.gateway}, dnsCount=${analysisResult.data.dnsServers.size}")
                    analysisResult.data
                }
                is NetworkResult.Error -> {
                    Timber.w("Network analysis refresh failed: ${analysisResult.message} (not critical - empty analysis is valid disconnected state)")
                    if (currentState is NetworkState.NetworkDataHolder) {
                        Timber.d("Keeping existing network analysis due to error")
                        currentState.analysis // Keep existing data
                    } else {
                        Timber.d("No existing data, using default disconnected state")
                        NetworkAnalysis(
                            isConnected = false,
                            dnsServers = emptyList(),
                            gateway = null,
                            networkSpeed = null,
                            connectionType = ConnectionType.NONE
                        )
                    }
                }
            }
            
            if (currentState is NetworkState.NetworkDataHolder) {
                val previousErrorMessage = (currentState as? NetworkState.PartialSuccess)?.errorMessage
                Timber.d("Previous error message: $previousErrorMessage")
                val aggregatedErrorMessage = buildAggregatedErrorMessage(
                    newError = analysisResult as? NetworkResult.Error,
                    errorPrefix = ERROR_PREFIX_ANALYSIS,
                    previousErrorMessage = previousErrorMessage
                )
                
                if (aggregatedErrorMessage != null) {
                    Timber.d("Updating state to PartialSuccess with error: $aggregatedErrorMessage")
                    _networkState.value = NetworkState.PartialSuccess(
                        wifiNetworks = currentState.wifiNetworks,
                        devices = currentState.devices,
                        analysis = analysis,
                        errorMessage = aggregatedErrorMessage
                    )
                } else {
                    Timber.d("Updating state to Success: ${currentState.wifiNetworks.size} networks, ${currentState.devices.size} devices")
                    _networkState.value = NetworkState.Success(
                        wifiNetworks = currentState.wifiNetworks,
                        devices = currentState.devices,
                        analysis = analysis
                    )
                }
            } else {
                Timber.d("Current state is not NetworkDataHolder, calling loadNetworkData()")
                loadNetworkData()
            }
            Timber.d("refreshAnalysis() completed")
        }
    }
}

/**
 * State representation for network dashboard.
 */
sealed class NetworkState {
    object Loading : NetworkState()
    
    /**
     * Common interface for states that contain network data.
     */
    sealed interface NetworkDataHolder {
        val wifiNetworks: List<WifiNetwork>
        val devices: List<NetworkDevice>
        val analysis: NetworkAnalysis
    }
    
    data class Success(
        override val wifiNetworks: List<WifiNetwork>,
        override val devices: List<NetworkDevice>,
        override val analysis: NetworkAnalysis
    ) : NetworkState(), NetworkDataHolder
    
    data class PartialSuccess(
        override val wifiNetworks: List<WifiNetwork>,
        override val devices: List<NetworkDevice>,
        override val analysis: NetworkAnalysis,
        val errorMessage: String
    ) : NetworkState(), NetworkDataHolder
    
    data class Error(val message: String) : NetworkState()
}

