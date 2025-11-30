package com.ble1st.connectias.feature.network.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.analyzer.FlowAnalyzerProvider
import com.ble1st.connectias.feature.network.models.FlowStats
import com.ble1st.connectias.feature.network.models.NetworkDevice
import javax.inject.Inject

/**
 * ViewModel for Network Flow Analyzer.
 */
@HiltViewModel
class FlowAnalyzerViewModel @Inject constructor(
    private val flowAnalyzerProvider: FlowAnalyzerProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<FlowAnalyzerState>(FlowAnalyzerState.Idle)
    val uiState: StateFlow<FlowAnalyzerState> = _uiState.asStateFlow()
    
    private var currentAnalysis: Job? = null

    /**
     * Analyzes network flows.
     */
    fun analyzeFlows(devices: List<NetworkDevice>) {
        // Cancel previous analysis if running
        currentAnalysis?.cancel()
        
        currentAnalysis = viewModelScope.launch {
            _uiState.value = FlowAnalyzerState.Loading
            try {
                flowAnalyzerProvider.trackFlows(devices)
                val stats = flowAnalyzerProvider.analyzeFlowStats(devices)
                _uiState.value = FlowAnalyzerState.Success(stats)
            } catch (e: Exception) {
                _uiState.value = FlowAnalyzerState.Error(e.message ?: "Failed to analyze flows")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _uiState.value = FlowAnalyzerState.Idle
    }
}

/**
 * State representation for flow analyzer operations.
 */
sealed class FlowAnalyzerState {
    object Idle : FlowAnalyzerState()
    object Loading : FlowAnalyzerState()
    data class Success(val stats: FlowStats) : FlowAnalyzerState()
    data class Error(val message: String) : FlowAnalyzerState()
}
