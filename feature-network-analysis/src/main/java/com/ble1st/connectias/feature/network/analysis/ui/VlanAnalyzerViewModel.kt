package com.ble1st.connectias.feature.network.analysis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.analysis.analyzer.VlanAnalyzerProvider
import com.ble1st.connectias.feature.network.analysis.models.VlanInfo
import javax.inject.Inject

/**
 * ViewModel for VLAN Analyzer.
 */
@HiltViewModel
class VlanAnalyzerViewModel @Inject constructor(
    private val vlanAnalyzerProvider: VlanAnalyzerProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<VlanAnalyzerState>(VlanAnalyzerState.Idle)
    val uiState: StateFlow<VlanAnalyzerState> = _uiState.asStateFlow()
    
    private var analyzeJob: Job? = null

    /**
     * Analyzes VLANs from a list of IP addresses.
     */
    fun analyzeVlans(ipAddresses: List<String>) {
        // Cancel previous analysis if running
        analyzeJob?.cancel()
        
        if (ipAddresses.isEmpty()) {
            _uiState.value = VlanAnalyzerState.Error("IP address list cannot be empty")
            return
        }

        analyzeJob = viewModelScope.launch {
            _uiState.value = VlanAnalyzerState.Loading
            try {
                val vlans = vlanAnalyzerProvider.analyzeVlans(ipAddresses)
                _uiState.value = VlanAnalyzerState.Success(vlans)
            } catch (e: Exception) {
                _uiState.value = VlanAnalyzerState.Error(e.message ?: "Failed to analyze VLANs")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _uiState.value = VlanAnalyzerState.Idle
    }
}

/**
 * State representation for VLAN analyzer operations.
 */
sealed class VlanAnalyzerState {
    object Idle : VlanAnalyzerState()
    object Loading : VlanAnalyzerState()
    data class Success(val vlans: List<VlanInfo>) : VlanAnalyzerState()
    data class Error(val message: String) : VlanAnalyzerState()
}
