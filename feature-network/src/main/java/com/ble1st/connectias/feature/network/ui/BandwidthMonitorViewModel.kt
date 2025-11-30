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
import com.ble1st.connectias.feature.network.monitor.BandwidthMonitorProvider
import com.ble1st.connectias.feature.network.monitor.InterfaceStats
import com.ble1st.connectias.feature.network.monitor.DeviceBandwidthStats
import com.ble1st.connectias.feature.network.monitor.TrafficPattern
import com.ble1st.connectias.feature.network.models.NetworkDevice
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Bandwidth Monitor.
 */
@HiltViewModel
class BandwidthMonitorViewModel @Inject constructor(
    private val bandwidthMonitorProvider: BandwidthMonitorProvider
) : ViewModel() {

    private val _interfaceStats = MutableStateFlow<List<InterfaceStats>>(emptyList())
    val interfaceStats: StateFlow<List<InterfaceStats>> = _interfaceStats.asStateFlow()

    private val _deviceStats = MutableStateFlow<List<DeviceBandwidthStats>>(emptyList())
    val deviceStats: StateFlow<List<DeviceBandwidthStats>> = _deviceStats.asStateFlow()

    private val _trafficPattern = MutableStateFlow<TrafficPattern?>(null)
    val trafficPattern: StateFlow<TrafficPattern?> = _trafficPattern.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var refreshJob: Job? = null

    /**
     * Refreshes bandwidth statistics.
     * Prevents concurrent calls by canceling any existing refresh job before starting a new one.
     */
    fun refreshStats(devices: List<NetworkDevice> = emptyList()) {
        // Cancel previous job if still active
        refreshJob?.cancel()
        
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _interfaceStats.value = bandwidthMonitorProvider.getInterfaceStats()
                if (devices.isNotEmpty()) {
                    _deviceStats.value = bandwidthMonitorProvider.getDeviceBandwidthStats(devices)
                } else {
                    _deviceStats.value = emptyList()
                }
                _trafficPattern.value = bandwidthMonitorProvider.analyzeTrafficPatterns()
            } catch (e: CancellationException) {
                // Re-throw cancellation to allow proper coroutine cancellation
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh bandwidth statistics")
                _errorMessage.value = e.message ?: "Failed to refresh bandwidth statistics"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
