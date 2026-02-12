package com.ble1st.connectias.feature.network.ui

import com.ble1st.connectias.feature.network.model.HostInfo
import com.ble1st.connectias.feature.network.model.NetworkEnvironment
import com.ble1st.connectias.feature.network.model.PortRangePreset
import com.ble1st.connectias.feature.network.model.PortResult
import com.ble1st.connectias.feature.network.model.WifiNetwork
import java.time.Instant

enum class NetworkToolsTab {
    WIFI,
    LAN,
    PORTS,
    SSL,
    SPEED_TEST
}

data class SpeedTestUiState(
    val isRunning: Boolean = false,
    val downloadSpeedMbps: Float = 0f,
    val progress: Float = 0f,
    val isFinished: Boolean = false,
    val error: String? = null
)

data class WifiUiState(
    val networks: List<WifiNetwork> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val permissionGranted: Boolean = false,
    val lastUpdated: Instant? = null
)

data class LanScanUiState(
    val environment: NetworkEnvironment? = null,
    val hosts: List<HostInfo> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null,
    val progress: Float = 0f
)

data class PortScanUiState(
    val target: String = "",
    val selectedPreset: PortRangePreset? = null,
    val customStart: Int = 1,
    val customEnd: Int = 1024,
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val results: List<PortResult> = emptyList(),
    val error: String? = null
)

data class SslScanUiState(
    val target: String = "",
    val isRunning: Boolean = false,
    val error: String? = null,
    val report: com.ble1st.connectias.feature.network.model.SslReport? = null
)

data class NetworkToolsState(
    val activeTab: NetworkToolsTab = NetworkToolsTab.WIFI,
    val wifiState: WifiUiState = WifiUiState(),
    val lanState: LanScanUiState = LanScanUiState(),
    val portState: PortScanUiState = PortScanUiState(),
    val sslState: SslScanUiState = SslScanUiState(),
    val speedTestState: SpeedTestUiState = SpeedTestUiState()
)
