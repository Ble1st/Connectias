package com.ble1st.connectias.feature.network.analysis.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.analysis.analyzer.OuiLookupProvider
import com.ble1st.connectias.feature.network.analysis.models.MacAddressInfo
import javax.inject.Inject

/**
 * ViewModel for MAC Address Analyzer.
 */
@HiltViewModel
class MacAnalyzerViewModel @Inject constructor(
    private val ouiLookupProvider: OuiLookupProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<MacAnalyzerState>(MacAnalyzerState.Idle)
    val uiState: StateFlow<MacAnalyzerState> = _uiState.asStateFlow()
    
    private var analysisJob: Job? = null

    /**
     * Analyzes a MAC address.
     */
    fun analyzeMacAddress(macAddress: String) {
        // Cancel previous analysis if running
        analysisJob?.cancel()
        
        if (macAddress.isBlank()) {
            _uiState.value = MacAnalyzerState.Error("MAC address cannot be empty")
            return
        }

        analysisJob = viewModelScope.launch {
            _uiState.value = MacAnalyzerState.Loading
            try {
                val info = ouiLookupProvider.lookupMacAddress(macAddress)
                _uiState.value = MacAnalyzerState.Success(info)
            } catch (e: Exception) {
                _uiState.value = MacAnalyzerState.Error(e.message ?: "Failed to analyze MAC address")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        // Cancel any running analysis
        analysisJob?.cancel()
        _uiState.value = MacAnalyzerState.Idle
    }
}

/**
 * State representation for MAC analyzer operations.
 */
sealed class MacAnalyzerState {
    object Idle : MacAnalyzerState()
    object Loading : MacAnalyzerState()
    data class Success(val macInfo: MacAddressInfo) : MacAnalyzerState()
    data class Error(val message: String) : MacAnalyzerState()
}
