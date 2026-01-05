package com.ble1st.connectias.feature.deviceinfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.deviceinfo.data.BatteryInfo
import com.ble1st.connectias.feature.deviceinfo.data.DeviceRepository
import com.ble1st.connectias.feature.deviceinfo.data.StorageInfo
import com.ble1st.connectias.feature.deviceinfo.data.TemperatureEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val batteryInfo: BatteryInfo? = null,
    val storageInfo: StorageInfo? = null,
    val currentTemp: Float = 0f,
    val tempHistory: List<TemperatureEntity> = emptyList()
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val repository: DeviceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceUiState())

    init {
        viewModelScope.launch {
            repository.temperatureHistory.collect { history ->
                _state.update { it.copy(tempHistory = history) }
            }
        }
        
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            while (true) {
                val battery = repository.getBatteryInfo()
                val storage = repository.getStorageInfo()
                val temp = repository.getCpuTemperature()
                
                _state.update { 
                    it.copy(
                        batteryInfo = battery,
                        storageInfo = storage,
                        currentTemp = temp
                    )
                }
                
                // Log temp every 5 seconds for demo (in real app maybe via WorkManager every 15 min)
                // For this interactive view, we just show live data, but let's log manually or just rely on manual refresh?
                // The prompt asked for history. Let's add a "Log Now" or auto-log periodically.
                // Let's rely on a Worker for background logging, but here we just show what's in DB.
                // I will add a manual log call every minute or so if screen is active.
                
                delay(2000)
            }
        }
        
        // Separate logger loop
        viewModelScope.launch {
            while(true) {
                repository.logTemperature()
                delay(60000) // Log every minute
            }
        }
    }

}
