package com.ble1st.connectias.feature.bluetooth.ui.model

data class UiDevice(
    val title: String,
    val address: String,
    val rssi: Int,
    val fillLevel: Float
)

enum class PermissionStatus {
    Unknown,
    Granted,
    Rationale,
    DeniedPermanently
}

data class BluetoothUiState(
    val devices: List<UiDevice> = emptyList(),
    val permissionStatus: PermissionStatus = PermissionStatus.Unknown,
    val isScanning: Boolean = false,
    val isBluetoothDisabled: Boolean = false,
    val selectedDevice: UiDevice? = null,
    val errorMessage: String? = null
)
