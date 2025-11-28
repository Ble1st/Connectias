package com.ble1st.connectias.feature.deviceinfo.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Battery Analyzer.
 */
@HiltViewModel
class BatteryAnalyzerViewModel @Inject constructor(
    private val batteryAnalyzerProvider: BatteryAnalyzerProvider
) : ViewModel() {

    private val _batteryState = MutableStateFlow<BatteryState>(BatteryState.Idle)
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private var monitoringJob: Job? = null

    /**
     * Gets current battery information.
     */
    fun getBatteryInfo() {
        viewModelScope.launch {
            val info = batteryAnalyzerProvider.getBatteryInfo()
            _batteryState.value = BatteryState.Info(info)
        }
    }

    /**
     * Starts battery monitoring.
     */
    fun startMonitoring() {
        if (monitoringJob == null || monitoringJob?.isCompleted == true) {
            monitoringJob = viewModelScope.launch {
                batteryAnalyzerProvider.monitorBattery().collect { info ->
                    _batteryState.value = BatteryState.Info(info)
                }
            }
        }
    }

    /**
     * Stops battery monitoring.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Estimates time to full charge.
     */
    fun estimateTimeToFullCharge() {
        viewModelScope.launch {
            val time = batteryAnalyzerProvider.estimateTimeToFullCharge()
            _batteryState.value = BatteryState.TimeEstimate(time, true)
        }
    }

    /**
     * Estimates time to empty.
     */
    fun estimateTimeToEmpty() {
        viewModelScope.launch {
            val time = batteryAnalyzerProvider.estimateTimeToEmpty()
            _batteryState.value = BatteryState.TimeEstimate(time, false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}

/**
 * State representation for battery operations.
 */
sealed class BatteryState {
    object Idle : BatteryState()
    data class Info(val info: BatteryInfo) : BatteryState()
    data class TimeEstimate(val timeMs: Long?, val isCharging: Boolean) : BatteryState()
}

