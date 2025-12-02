package com.ble1st.connectias.feature.network.wol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.network.wol.models.WolDevice
import com.ble1st.connectias.feature.network.wol.models.WolDeviceGroup
import com.ble1st.connectias.feature.network.wol.models.WolHistoryEntry
import com.ble1st.connectias.feature.network.wol.models.WolResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Wake-on-LAN functionality.
 */
@HiltViewModel
class WolViewModel @Inject constructor(
    private val wolProvider: WakeOnLanProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(WolUiState())
    val uiState: StateFlow<WolUiState> = _uiState.asStateFlow()

    val devices: StateFlow<List<WolDevice>> = wolProvider.devices
    val groups: StateFlow<List<WolDeviceGroup>> = wolProvider.groups
    val history: StateFlow<List<WolHistoryEntry>> = wolProvider.history

    init {
        viewModelScope.launch {
            wolProvider.refreshDeviceStatuses()
        }
    }

    /**
     * Wakes a device.
     */
    fun wakeDevice(device: WolDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isWaking = true,
                wakingDeviceId = device.id,
                lastResult = null,
                error = null
            ) }

            val result = wolProvider.wakeDevice(device)
            
            _uiState.update { it.copy(
                isWaking = false,
                wakingDeviceId = null,
                lastResult = result
            ) }

            when (result) {
                is WolResult.Success -> {
                    Timber.d("Wake packet sent to ${device.name}")
                }
                is WolResult.DeviceAwake -> {
                    Timber.d("Device ${device.name} is awake (${result.responseTime}ms)")
                }
                is WolResult.DeviceNotResponding -> {
                    Timber.w("Device ${device.name} did not respond")
                }
                is WolResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
                is WolResult.InvalidMacAddress -> {
                    _uiState.update { it.copy(error = "Invalid MAC address: ${result.macAddress}") }
                }
            }
        }
    }

    /**
     * Wakes a device by MAC address (quick wake).
     */
    fun quickWake(macAddress: String, broadcastAddress: String = "255.255.255.255", port: Int = 9) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isWaking = true,
                error = null
            ) }

            val result = wolProvider.sendMagicPacket(macAddress, broadcastAddress, port)
            
            _uiState.update { it.copy(
                isWaking = false,
                lastResult = result
            ) }

            if (result is WolResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    /**
     * Wakes all devices in a group.
     */
    fun wakeGroup(group: WolDeviceGroup) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isWaking = true,
                wakingGroupId = group.id,
                error = null
            ) }

            val results = wolProvider.wakeGroup(group)
            
            _uiState.update { it.copy(
                isWaking = false,
                wakingGroupId = null,
                lastGroupResults = results
            ) }

            val errors = results.filterIsInstance<WolResult.Error>()
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(
                    error = "Some devices failed to wake: ${errors.joinToString { it.message }}"
                ) }
            }
        }
    }

    /**
     * Refreshes the online status of all devices.
     */
    fun refreshDeviceStatuses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            wolProvider.refreshDeviceStatuses()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Adds a new device.
     */
    fun addDevice(device: WolDevice) {
        wolProvider.addDevice(device)
    }

    /**
     * Updates an existing device.
     */
    fun updateDevice(device: WolDevice) {
        wolProvider.updateDevice(device)
    }

    /**
     * Removes a device.
     */
    fun removeDevice(deviceId: String) {
        wolProvider.removeDevice(deviceId)
    }

    /**
     * Adds a new device group.
     */
    fun addGroup(group: WolDeviceGroup) {
        wolProvider.addGroup(group)
    }

    /**
     * Updates an existing group.
     */
    fun updateGroup(group: WolDeviceGroup) {
        wolProvider.updateGroup(group)
    }

    /**
     * Removes a group.
     */
    fun removeGroup(groupId: String) {
        wolProvider.removeGroup(groupId)
    }

    /**
     * Clears the wake history.
     */
    fun clearHistory() {
        wolProvider.clearHistory()
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clears the last result.
     */
    fun clearResult() {
        _uiState.update { it.copy(lastResult = null, lastGroupResults = null) }
    }

    /**
     * Sets the edit dialog device.
     */
    fun setEditDevice(device: WolDevice?) {
        _uiState.update { it.copy(editingDevice = device) }
    }

    /**
     * Sets the edit dialog group.
     */
    fun setEditGroup(group: WolDeviceGroup?) {
        _uiState.update { it.copy(editingGroup = group) }
    }

    /**
     * Shows the add device dialog.
     */
    fun showAddDeviceDialog() {
        _uiState.update { it.copy(showAddDeviceDialog = true) }
    }

    /**
     * Hides the add device dialog.
     */
    fun hideAddDeviceDialog() {
        _uiState.update { it.copy(showAddDeviceDialog = false, editingDevice = null) }
    }

    /**
     * Shows the quick wake dialog.
     */
    fun showQuickWakeDialog() {
        _uiState.update { it.copy(showQuickWakeDialog = true) }
    }

    /**
     * Hides the quick wake dialog.
     */
    fun hideQuickWakeDialog() {
        _uiState.update { it.copy(showQuickWakeDialog = false) }
    }
}

/**
 * UI state for Wake-on-LAN screen.
 */
data class WolUiState(
    val isWaking: Boolean = false,
    val isRefreshing: Boolean = false,
    val wakingDeviceId: String? = null,
    val wakingGroupId: String? = null,
    val lastResult: WolResult? = null,
    val lastGroupResults: List<WolResult>? = null,
    val error: String? = null,
    val editingDevice: WolDevice? = null,
    val editingGroup: WolDeviceGroup? = null,
    val showAddDeviceDialog: Boolean = false,
    val showQuickWakeDialog: Boolean = false
)
