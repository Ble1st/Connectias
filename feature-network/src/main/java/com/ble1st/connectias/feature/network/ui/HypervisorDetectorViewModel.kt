package com.ble1st.connectias.feature.network.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.analyzer.HypervisorDetectorProvider
import com.ble1st.connectias.feature.network.models.HypervisorInfo
import com.ble1st.connectias.feature.network.models.NetworkDevice
import javax.inject.Inject

/**
 * ViewModel for Hypervisor/VM Detector.
 */
@HiltViewModel
class HypervisorDetectorViewModel @Inject constructor(
    private val hypervisorDetectorProvider: HypervisorDetectorProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<HypervisorDetectorState>(HypervisorDetectorState.Idle)
    val uiState: StateFlow<HypervisorDetectorState> = _uiState.asStateFlow()
    
    private var detectJob: Job? = null

    /**
     * Detects hypervisors and VMs from discovered devices.
     */
    fun detectHypervisors(
        devices: List<NetworkDevice>,
        macManufacturers: Map<String, String?> = emptyMap()
    ) {
        // Cancel previous detection if running
        detectJob?.cancel()
        
        detectJob = viewModelScope.launch {
            _uiState.value = HypervisorDetectorState.Loading
            try {
                val hypervisors = hypervisorDetectorProvider.detectHypervisors(devices, macManufacturers)
                val containers = hypervisorDetectorProvider.detectContainers(devices)
                _uiState.value = HypervisorDetectorState.Success(hypervisors, containers)
            } catch (e: CancellationException) {
                // Re-throw cancellation to allow proper coroutine cancellation
                throw e
            } catch (e: Exception) {
                _uiState.value = HypervisorDetectorState.Error(e.message ?: "Failed to detect hypervisors")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        detectJob?.cancel()
        _uiState.value = HypervisorDetectorState.Idle
    }
}

/**
 * State representation for hypervisor detector operations.
 */
sealed class HypervisorDetectorState {
    object Idle : HypervisorDetectorState()
    object Loading : HypervisorDetectorState()
    data class Success(
        val hypervisors: List<HypervisorInfo>,
        val containers: List<NetworkDevice>
    ) : HypervisorDetectorState()
    data class Error(val message: String) : HypervisorDetectorState()
}
