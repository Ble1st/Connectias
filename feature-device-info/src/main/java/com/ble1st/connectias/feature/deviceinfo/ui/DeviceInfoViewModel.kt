package com.ble1st.connectias.feature.deviceinfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.deviceinfo.provider.DeviceInfo
import com.ble1st.connectias.feature.deviceinfo.provider.DeviceInfoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val deviceInfoProvider: DeviceInfoProvider
) : ViewModel() {

    private val _deviceInfo = MutableStateFlow<DeviceInfoState>(DeviceInfoState.Loading)
    val deviceInfo: StateFlow<DeviceInfoState> = _deviceInfo.asStateFlow()

    init {
        loadDeviceInfo()
    }

    fun loadDeviceInfo() {
        viewModelScope.launch {
            _deviceInfo.value = DeviceInfoState.Loading
            try {
                val info = deviceInfoProvider.getDeviceInfo()
                _deviceInfo.value = DeviceInfoState.Success(info)
            } catch (e: Exception) {
                _deviceInfo.value = DeviceInfoState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refresh() {
        loadDeviceInfo()
    }
}

sealed class DeviceInfoState {
    object Loading : DeviceInfoState()
    data class Success(val info: DeviceInfo) : DeviceInfoState()
    data class Error(val message: String) : DeviceInfoState()
}

