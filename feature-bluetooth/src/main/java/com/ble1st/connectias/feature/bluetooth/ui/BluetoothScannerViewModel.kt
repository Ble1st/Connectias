package com.ble1st.connectias.feature.bluetooth.ui

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.bluetooth.data.BluetoothScanner
import com.ble1st.connectias.feature.bluetooth.model.DiscoveredDevice
import com.ble1st.connectias.feature.bluetooth.model.RssiNormalizer
import com.ble1st.connectias.feature.bluetooth.ui.model.BluetoothUiState
import com.ble1st.connectias.feature.bluetooth.ui.model.PermissionStatus
import com.ble1st.connectias.feature.bluetooth.ui.model.UiDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class BluetoothScannerViewModel @Inject constructor(
    private val scanner: BluetoothScanner,
    private val bluetoothAdapter: BluetoothAdapter?
) : ViewModel() {

    private var dispatcher: CoroutineDispatcher = Dispatchers.IO
    internal constructor(
        scanner: BluetoothScanner,
        bluetoothAdapter: BluetoothAdapter?,
        dispatcher: CoroutineDispatcher
    ) : this(scanner, bluetoothAdapter) {
        this.dispatcher = dispatcher
    }

    private val _state = MutableStateFlow(BluetoothUiState())
    val state: StateFlow<BluetoothUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun onPermissionStatus(status: PermissionStatus) {
        _state.update { it.copy(permissionStatus = status) }
        if (status == PermissionStatus.Granted) {
            startScanning()
        } else {
            stopScanning()
        }
    }

    fun startScanning() {
        if (_state.value.permissionStatus != PermissionStatus.Granted) {
            _state.update { it.copy(permissionStatus = PermissionStatus.Rationale) }
            return
        }
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch(dispatcher) {
            scanner.scan()
                .onStart {
                    _state.update {
                        it.copy(
                            isScanning = true,
                            isBluetoothDisabled = bluetoothAdapter?.isEnabled == false,
                            errorMessage = null
                        )
                    }
                }
                .catch { throwable ->
                    Timber.e(throwable, "Bluetooth scan failed")
                    _state.update {
                        it.copy(
                            isScanning = false,
                            errorMessage = throwable.message
                        )
                    }
                }
                .collect { devices ->
                    _state.update {
                        it.copy(
                            devices = devices.map(::mapToUi),
                            isScanning = true,
                            isBluetoothDisabled = bluetoothAdapter?.isEnabled == false
                        )
                    }
                }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(isScanning = false) }
    }

    fun selectDevice(device: UiDevice) {
        _state.update { it.copy(selectedDevice = device) }
    }

    fun dismissRadar() {
        _state.update { it.copy(selectedDevice = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }

    private fun mapToUi(device: DiscoveredDevice): UiDevice {
        return UiDevice(
            title = device.name?.takeIf { it.isNotBlank() } ?: "Unbekanntes Gerät",
            address = device.address,
            rssi = device.rssi,
            fillLevel = RssiNormalizer.toFillLevel(device.rssi)
        )
    }
}
