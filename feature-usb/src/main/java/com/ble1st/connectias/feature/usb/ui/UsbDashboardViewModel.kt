package com.ble1st.connectias.feature.usb.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.provider.UsbProvider
import com.ble1st.connectias.feature.usb.provider.UsbResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class UsbDashboardUiState(
    val devices: List<UsbDevice> = emptyList(),
    val isLoading: Boolean = false,
    val selectedDevice: UsbDevice? = null,
    val error: String? = null
)

@HiltViewModel
class UsbDashboardViewModel @Inject constructor(
    private val usbProvider: UsbProvider,
    private val deviceDetector: UsbDeviceDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsbDashboardUiState())
    val uiState: StateFlow<UsbDashboardUiState> = _uiState.asStateFlow()

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = usbProvider.enumerateDevices()) {
                is UsbResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            devices = result.data,
                            isLoading = false
                        ) 
                    }
                }
                is UsbResult.Failure -> {
                    Timber.e(result.error, "Failed to enumerate USB devices")
                    _uiState.update { 
                        it.copy(
                            devices = emptyList(),
                            isLoading = false,
                            error = result.error.message ?: "Unknown error"
                        ) 
                    }
                }
            }
        }
    }

    fun selectDevice(device: UsbDevice?) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedDevice = null) }
    }

    // Called when the Fragment registers/unregisters the receiver
    fun onReceiverRegistered() {
        // Potentially re-scan or set up listener callback logic if supported by detector
    }
}
