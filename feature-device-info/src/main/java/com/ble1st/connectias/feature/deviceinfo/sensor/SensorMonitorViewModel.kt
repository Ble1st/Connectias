package com.ble1st.connectias.feature.deviceinfo.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.hardware.Sensor
import javax.inject.Inject

/**
 * ViewModel for Sensor Monitor.
 */
@HiltViewModel
class SensorMonitorViewModel @Inject constructor(
    private val sensorMonitorProvider: SensorMonitorProvider
) : ViewModel() {

    private val _sensorState = MutableStateFlow<SensorState>(SensorState.Idle)
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private var monitoringJob: Job? = null

    /**
     * Gets available sensors.
     */
    fun getAvailableSensors() {
        viewModelScope.launch {
            val sensors = sensorMonitorProvider.getAvailableSensors()
            _sensorState.value = SensorState.Sensors(sensors)
        }
    }

    /**
     * Starts monitoring a sensor.
     */
    fun startMonitoring(sensorType: Int) {
        stopMonitoring()
        monitoringJob = viewModelScope.launch {
            sensorMonitorProvider.monitorSensor(sensorType).collect { data ->
                _sensorState.value = SensorState.Data(data)
            }
        }
    }

    /**
     * Stops sensor monitoring.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}

/**
 * State representation for sensor operations.
 */
sealed class SensorState {
    object Idle : SensorState()
    data class Sensors(val sensors: List<SensorInfo>) : SensorState()
    data class Data(val data: SensorData) : SensorState()
    data class Error(val message: String) : SensorState()
}

