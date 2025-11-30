package com.ble1st.connectias.feature.network.analysis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.analysis.analyzer.SubnetAnalyzerProvider
import com.ble1st.connectias.feature.network.analysis.models.SubnetInfo
import javax.inject.Inject

/**
 * ViewModel for Subnet Analyzer.
 */
@HiltViewModel
class SubnetAnalyzerViewModel @Inject constructor(
    private val subnetAnalyzerProvider: SubnetAnalyzerProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<SubnetAnalyzerState>(SubnetAnalyzerState.Idle)
    val uiState: StateFlow<SubnetAnalyzerState> = _uiState.asStateFlow()
    
    private var analysisJob: Job? = null

    /**
     * Analyzes a CIDR notation.
     */
    fun analyzeCidr(cidr: String) {
        // Cancel previous analysis if running
        analysisJob?.cancel()
        
        // Set loading state immediately
        _uiState.value = SubnetAnalyzerState.Loading
        
        if (cidr.isBlank()) {
            _uiState.value = SubnetAnalyzerState.Error("CIDR notation cannot be empty")
            return
        }

        analysisJob = viewModelScope.launch {
            try {
                val subnetInfo = subnetAnalyzerProvider.calculateSubnet(cidr)
                if (subnetInfo != null) {
                    _uiState.value = SubnetAnalyzerState.Success(subnetInfo)
                } else {
                    _uiState.value = SubnetAnalyzerState.Error("Invalid CIDR notation")
                }
            } catch (e: Exception) {
                _uiState.value = SubnetAnalyzerState.Error(e.message ?: "Failed to analyze subnet")
            }
        }
    }

    /**
     * Discovers subnets from a list of IP addresses.
     */
    fun discoverSubnets(ipAddresses: List<String>) {
        // Cancel previous analysis if running
        analysisJob?.cancel()
        
        // Validate input
        if (ipAddresses.isEmpty()) {
            _uiState.value = SubnetAnalyzerState.Error("No IP addresses provided")
            return
        }
        
        analysisJob = viewModelScope.launch {
            _uiState.value = SubnetAnalyzerState.Loading
            try {
                val subnets = subnetAnalyzerProvider.discoverSubnets(ipAddresses)
                _uiState.value = SubnetAnalyzerState.DiscoveredSubnets(subnets)
            } catch (e: Exception) {
                _uiState.value = SubnetAnalyzerState.Error(e.message ?: "Failed to discover subnets")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _uiState.value = SubnetAnalyzerState.Idle
    }
}

/**
 * State representation for subnet analyzer operations.
 */
sealed class SubnetAnalyzerState {
    object Idle : SubnetAnalyzerState()
    object Loading : SubnetAnalyzerState()
    data class Success(val subnetInfo: SubnetInfo) : SubnetAnalyzerState()
    data class DiscoveredSubnets(val subnets: List<SubnetInfo>) : SubnetAnalyzerState()
    data class Error(val message: String) : SubnetAnalyzerState()
}
