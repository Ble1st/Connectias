package com.ble1st.connectias.feature.network.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.analyzer.DhcpLeaseProvider
import com.ble1st.connectias.feature.network.models.DhcpLease
import com.ble1st.connectias.feature.network.models.NetworkDevice
import javax.inject.Inject

/**
 * ViewModel for DHCP Lease Viewer.
 */
@HiltViewModel
class DhcpLeaseViewModel @Inject constructor(
    private val dhcpLeaseProvider: DhcpLeaseProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<DhcpLeaseState>(DhcpLeaseState.Idle)
    val uiState: StateFlow<DhcpLeaseState> = _uiState.asStateFlow()
    
    private var currentAnalysisJob: Job? = null

    /**
     * Analyzes DHCP leases from discovered devices.
     */
    fun analyzeLeases(devices: List<NetworkDevice>) {
        // Cancel and join previous job if active
        currentAnalysisJob?.cancel()
        
        currentAnalysisJob = viewModelScope.launch {
            _uiState.value = DhcpLeaseState.Loading
            try {
                val leases = dhcpLeaseProvider.inferLeases(devices)
                val reservedIps = dhcpLeaseProvider.getReservedIps()
                _uiState.value = DhcpLeaseState.Success(leases, reservedIps)
            } catch (e: Exception) {
                _uiState.value = DhcpLeaseState.Error(e.message ?: "Failed to analyze DHCP leases")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _uiState.value = DhcpLeaseState.Idle
    }
}

/**
 * State representation for DHCP lease operations.
 */
sealed class DhcpLeaseState {
    object Idle : DhcpLeaseState()
    object Loading : DhcpLeaseState()
    data class Success(val leases: List<DhcpLease>, val reservedIps: List<String>) : DhcpLeaseState()
    data class Error(val message: String) : DhcpLeaseState()
}
