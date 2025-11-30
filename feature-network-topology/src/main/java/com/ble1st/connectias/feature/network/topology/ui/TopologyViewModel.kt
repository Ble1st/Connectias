package com.ble1st.connectias.feature.network.topology.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.topology.topology.TopologyMapperProvider
import com.ble1st.connectias.feature.network.topology.models.NetworkTopology
import javax.inject.Inject

/**
 * ViewModel for Network Topology Mapper.
 */
@HiltViewModel
class TopologyViewModel @Inject constructor(
    private val topologyMapperProvider: TopologyMapperProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<TopologyState>(TopologyState.Idle)
    val uiState: StateFlow<TopologyState> = _uiState.asStateFlow()

    private var topologyJob: Job? = null

    /**
     * Builds topology from discovered devices.
     */
    fun buildTopology(devices: List<NetworkDevice>) {
        if (devices.isEmpty()) {
            _uiState.value = TopologyState.Error("No devices provided")
            return
        }

        topologyJob?.cancel()
        topologyJob = viewModelScope.launch {
            _uiState.value = TopologyState.Loading
            try {
                val topology = topologyMapperProvider.buildTopology(devices)
                _uiState.value = TopologyState.Success(topology)
            } catch (e: Exception) {
                _uiState.value = TopologyState.Error(e.message ?: "Failed to build topology")
            }
        }
    }

    /**
     * Resets the state to idle.
     * Cancels any running topology job to prevent it from overwriting the idle state.
     */
    fun resetState() {
        topologyJob?.cancel()
        topologyJob = null
        _uiState.value = TopologyState.Idle
    }
}

/**
 * State representation for topology operations.
 */
sealed class TopologyState {
    object Idle : TopologyState()
    object Loading : TopologyState()
    data class Success(val topology: NetworkTopology) : TopologyState()
    data class Error(val message: String) : TopologyState()
}
