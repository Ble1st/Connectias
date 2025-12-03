package com.ble1st.connectias.feature.hardware.ui

import androidx.lifecycle.ViewModel
import com.ble1st.connectias.feature.hardware.bluetooth.BluetoothAnalyzerProvider
import com.ble1st.connectias.feature.hardware.nfc.NfcToolsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for Hardware Dashboard screen.
 */
@HiltViewModel
class HardwareViewModel @Inject constructor(
    private val nfcToolsProvider: NfcToolsProvider,
    private val bluetoothAnalyzerProvider: BluetoothAnalyzerProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HardwareDashboardUiState())
    val uiState: StateFlow<HardwareDashboardUiState> = _uiState.asStateFlow()

    init {
        checkHardwareAvailability()
    }

    private fun checkHardwareAvailability() {
        _uiState.update { state ->
            state.copy(
                isNfcAvailable = nfcToolsProvider.isNfcAvailable(),
                isNfcEnabled = nfcToolsProvider.isNfcEnabled(),
                isBluetoothAvailable = bluetoothAnalyzerProvider.isBluetoothAvailable(),
                isBluetoothEnabled = bluetoothAnalyzerProvider.isBluetoothEnabled(),
                isBleSupported = bluetoothAnalyzerProvider.isBleSupported()
            )
        }
    }

    /**
     * Refreshes hardware availability state.
     */
    fun refreshHardwareState() {
        nfcToolsProvider.updateEnabledState()
        checkHardwareAvailability()
    }
}

/**
 * UI state for Hardware Dashboard.
 */
data class HardwareDashboardUiState(
    val isNfcAvailable: Boolean = false,
    val isNfcEnabled: Boolean = false,
    val isBluetoothAvailable: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isBleSupported: Boolean = false
)

