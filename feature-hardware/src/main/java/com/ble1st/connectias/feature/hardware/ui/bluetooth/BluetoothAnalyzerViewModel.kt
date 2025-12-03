package com.ble1st.connectias.feature.hardware.ui.bluetooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.hardware.bluetooth.BluetoothAnalyzerProvider
import com.ble1st.connectias.feature.hardware.models.BeaconInfo
import com.ble1st.connectias.feature.hardware.models.BluetoothConnectionResult
import com.ble1st.connectias.feature.hardware.models.BluetoothDeviceInfo
import com.ble1st.connectias.feature.hardware.models.BluetoothScanResult
import com.ble1st.connectias.feature.hardware.models.ScanMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Bluetooth Analyzer screen.
 */
@HiltViewModel
class BluetoothAnalyzerViewModel @Inject constructor(
    private val bluetoothAnalyzerProvider: BluetoothAnalyzerProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(BluetoothAnalyzerUiState())
    val uiState: StateFlow<BluetoothAnalyzerUiState> = _uiState.asStateFlow()

    val isScanning = bluetoothAnalyzerProvider.isScanning
    val discoveredDevices = bluetoothAnalyzerProvider.discoveredDevices
    val connectedDevice = bluetoothAnalyzerProvider.connectedDevice

    private var scanJob: Job? = null
    private var connectionJob: Job? = null

    init {
        checkBluetoothAvailability()
        loadPairedDevices()
    }

    private fun checkBluetoothAvailability() {
        _uiState.update { state ->
            state.copy(
                isBluetoothAvailable = bluetoothAnalyzerProvider.isBluetoothAvailable(),
                isBluetoothEnabled = bluetoothAnalyzerProvider.isBluetoothEnabled(),
                isBleSupported = bluetoothAnalyzerProvider.isBleSupported()
            )
        }
    }

    private fun loadPairedDevices() {
        try {
            val pairedDevices = bluetoothAnalyzerProvider.getPairedDevices()
            _uiState.update { it.copy(pairedDevices = pairedDevices) }
        } catch (e: SecurityException) {
            Timber.w(e, "Permission denied for getting paired devices")
        }
    }

    /**
     * Starts BLE scanning.
     */
    fun startScan(mode: ScanMode = ScanMode.BALANCED) {
        scanJob?.cancel()
        
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(
                scanMode = mode,
                beacons = emptyList()
            ) }

            bluetoothAnalyzerProvider.scanBleDevices(mode).collect { result ->
                handleScanResult(result)
            }
        }
    }

    /**
     * Stops scanning.
     */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        bluetoothAnalyzerProvider.stopScan()
    }

    private fun handleScanResult(result: BluetoothScanResult) {
        when (result) {
            is BluetoothScanResult.ScanStarted -> {
                _uiState.update { it.copy(snackbarMessage = "Scan started (${result.mode})") }
            }
            is BluetoothScanResult.ScanStopped -> {
                _uiState.update { it.copy(snackbarMessage = "Scan stopped") }
            }
            is BluetoothScanResult.DeviceFound -> {
                // Devices are updated through the StateFlow
            }
            is BluetoothScanResult.BeaconFound -> {
                addBeacon(result.beacon)
            }
            is BluetoothScanResult.Error -> {
                _uiState.update { it.copy(snackbarMessage = "Error: ${result.message}") }
            }
            else -> {}
        }
    }

    private fun addBeacon(beacon: BeaconInfo) {
        _uiState.update { state ->
            val existingIndex = state.beacons.indexOfFirst { 
                it.deviceAddress == beacon.deviceAddress 
            }
            val updatedBeacons = if (existingIndex >= 0) {
                state.beacons.toMutableList().apply { set(existingIndex, beacon) }
            } else {
                state.beacons + beacon
            }
            state.copy(beacons = updatedBeacons)
        }
    }

    /**
     * Connects to a device.
     */
    fun connectToDevice(address: String) {
        connectionJob?.cancel()

        connectionJob = viewModelScope.launch {
            _uiState.update { it.copy(
                isConnecting = true,
                connectingDeviceAddress = address
            ) }

            bluetoothAnalyzerProvider.connectAndDiscoverServices(address).collect { result ->
                handleConnectionResult(result)
            }
        }
    }

    private fun handleConnectionResult(result: BluetoothConnectionResult) {
        when (result) {
            is BluetoothConnectionResult.Connected -> {
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        connectingDeviceAddress = null,
                        snackbarMessage = "Connected to ${result.device.name ?: result.device.address}"
                    )
                }
            }
            is BluetoothConnectionResult.Disconnected -> {
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        connectingDeviceAddress = null,
                        snackbarMessage = "Disconnected from ${result.address}"
                    )
                }
            }
            is BluetoothConnectionResult.Error -> {
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        connectingDeviceAddress = null,
                        snackbarMessage = "Connection error: ${result.message}"
                    )
                }
            }
        }
    }

    /**
     * Disconnects from the current device.
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        bluetoothAnalyzerProvider.disconnect()
    }

    /**
     * Calculates estimated distance for a device.
     */
    fun getEstimatedDistance(rssi: Int, txPower: Int = -59): Double {
        return bluetoothAnalyzerProvider.estimateDistance(rssi, txPower)
    }

    /**
     * Sets the selected device for details view.
     */
    fun selectDevice(device: BluetoothDeviceInfo?) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    /**
     * Sets the scan mode.
     */
    fun setScanMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode) }
    }

    /**
     * Toggles the filter for showing only connectable devices.
     */
    fun toggleConnectableFilter() {
        _uiState.update { it.copy(showOnlyConnectable = !it.showOnlyConnectable) }
    }

    /**
     * Clears the snackbar message.
     */
    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Refreshes Bluetooth state.
     */
    fun refreshBluetoothState() {
        checkBluetoothAvailability()
        loadPairedDevices()
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        disconnect()
    }
}

/**
 * UI state for Bluetooth Analyzer screen.
 */
data class BluetoothAnalyzerUiState(
    val isBluetoothAvailable: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isBleSupported: Boolean = false,
    val isConnecting: Boolean = false,
    val connectingDeviceAddress: String? = null,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val beacons: List<BeaconInfo> = emptyList(),
    val selectedDevice: BluetoothDeviceInfo? = null,
    val scanMode: ScanMode = ScanMode.BALANCED,
    val showOnlyConnectable: Boolean = false,
    val snackbarMessage: String? = null
)

