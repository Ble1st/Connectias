package com.ble1st.connectias.feature.network.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.monitor.BandwidthMonitorProvider
import com.ble1st.connectias.feature.network.monitor.InterfaceStats
import com.ble1st.connectias.feature.network.monitor.DeviceBandwidthStats
import com.ble1st.connectias.feature.network.monitor.TrafficPattern
import com.ble1st.connectias.feature.network.models.NetworkDevice
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

    /**
     * Refreshes bandwidth statistics.
     */
    fun refreshStats(devices: List<NetworkDevice> = emptyList()) {
        viewModelScope.launch {
            try {
                _interfaceStats.value = bandwidthMonitorProvider.getInterfaceStats()
                if (devices.isNotEmpty()) {
                    _deviceStats.value = bandwidthMonitorProvider.getDeviceBandwidthStats(devices)
                } else {
                    _deviceStats.value = emptyList()
                }
                _trafficPattern.value = bandwidthMonitorProvider.analyzeTrafficPatterns()
            } catch (e: Exception) {
                // Handle error - log and/or expose to UI via error state
            }
        }
    }
}
