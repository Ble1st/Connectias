package com.ble1st.connectias.feature.network.geolocation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for IP Geolocation screen.
 */
@HiltViewModel
class IpGeolocationViewModel @Inject constructor(
    private val geolocationProvider: IpGeolocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(IpGeolocationUiState())
    val uiState: StateFlow<IpGeolocationUiState> = _uiState.asStateFlow()

    /**
     * Looks up geolocation for an IP address.
     */
    fun lookupIp(ip: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = geolocationProvider.lookupIp(ip)
            _uiState.update { it.copy(
                isLoading = false,
                result = result,
                error = if (!result.success) result.error else null
            ) }
        }
    }

    /**
     * Looks up geolocation for the current device's IP.
     */
    fun lookupCurrentIp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = geolocationProvider.lookupCurrentIp()
            _uiState.update { it.copy(
                isLoading = false,
                result = result,
                error = if (!result.success) result.error else null
            ) }
        }
    }

    /**
     * Clears the results.
     */
    fun clearResults() {
        _uiState.update { IpGeolocationUiState() }
    }
}

/**
 * UI state for IP Geolocation screen.
 */
data class IpGeolocationUiState(
    val isLoading: Boolean = false,
    val result: GeolocationResult? = null,
    val error: String? = null
)

