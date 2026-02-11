package com.ble1st.connectias.feature.network.ui

import com.ble1st.connectias.feature.network.network.SpeedTestManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.network.model.PortPresets
import com.ble1st.connectias.feature.network.model.PortRangePreset
import com.ble1st.connectias.feature.network.network.NetworkScanner
import com.ble1st.connectias.feature.network.port.PortScanner
import com.ble1st.connectias.feature.network.ssl.SslScanner
import com.ble1st.connectias.feature.network.wifi.WifiScanner
import java.time.Instant
import timber.log.Timber

@HiltViewModel
class NetworkToolsViewModel @Inject constructor(
    private val wifiScanner: WifiScanner,
    private val networkScanner: NetworkScanner,
    private val portScanner: PortScanner,
    private val sslScanner: SslScanner,
    private val speedTestManager: SpeedTestManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        NetworkToolsState(
            portState = PortScanUiState(
                selectedPreset = PortPresets.presets.firstOrNull()
            )
        )
    )
    val state: StateFlow<NetworkToolsState> = _state.asStateFlow()

    fun setActiveTab(tab: NetworkToolsTab) {
        _state.update { it.copy(activeTab = tab) }
    }
    
    // ... Wifi methods ...
    fun onWifiPermission(granted: Boolean) {
        _state.update {
            it.copy(
                wifiState = it.wifiState.copy(
                    permissionGranted = granted,
                    error = null
                )
            )
        }
    }

    fun refreshWifi() {
        val current = _state.value.wifiState
        if (!current.permissionGranted) {
            _state.update {
                it.copy(
                    wifiState = it.wifiState.copy(
                        error = "Berechtigung für WLAN-Scan fehlt"
                    )
                )
            }
            return
        }
        if (current.isLoading) return

        _state.update { it.copy(wifiState = it.wifiState.copy(isLoading = true, error = null)) }

        viewModelScope.launch {
            try {
                val results = wifiScanner.scan().sortedByDescending { it.rssi }
                _state.update {
                    it.copy(
                        wifiState = it.wifiState.copy(
                            networks = results,
                            isLoading = false,
                            lastUpdated = Instant.now()
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Wifi scan failed")
                _state.update {
                    it.copy(
                        wifiState = it.wifiState.copy(
                            isLoading = false,
                            error = e.message ?: "Scan fehlgeschlagen"
                        )
                    )
                }
            }
        }
    }
    
    // ... Other methods remain, I will add startSpeedTest at the end
    fun startSpeedTest() {
        if (_state.value.speedTestState.isRunning) return
        
        _state.update { it.copy(speedTestState = SpeedTestUiState(isRunning = true, error = null, isFinished = false)) }
        
        viewModelScope.launch {
            speedTestManager.startDownloadTest().collect { result ->
                _state.update { 
                    it.copy(
                        speedTestState = it.speedTestState.copy(
                            isRunning = !result.isFinished && result.error == null,
                            downloadSpeedMbps = result.downloadSpeedMbps,
                            progress = result.progress,
                            isFinished = result.isFinished,
                            error = result.error
                        )
                    )
                }
            }
        }
    }

    fun updatePortTarget(target: String) {
        _state.update {
            it.copy(portState = it.portState.copy(target = target, error = null))
        }
    }

    fun selectPortPreset(preset: PortRangePreset?) {
        _state.update {
            it.copy(portState = it.portState.copy(selectedPreset = preset, error = null))
        }
    }

    fun updateCustomPortRange(start: Int, end: Int) {
        _state.update {
            it.copy(
                portState = it.portState.copy(
                    customStart = start,
                    customEnd = end,
                    error = null
                )
            )
        }
    }

    fun updateSslTarget(target: String) {
        _state.update { it.copy(sslState = it.sslState.copy(target = target, error = null)) }
    }

    fun startNetworkScan() {
        if (_state.value.lanState.isScanning) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    lanState = it.lanState.copy(
                        isScanning = true,
                        error = null,
                        progress = 0f,
                        hosts = emptyList()
                    )
                )
            }
            try {
                val env = networkScanner.detectEnvironment()
                    ?: throw IllegalStateException("Kein aktives IPv4-Netz gefunden")
                _state.update { it.copy(lanState = it.lanState.copy(environment = env)) }
                val hosts = networkScanner.scanHosts(
                    environment = env,
                    onProgress = { progress ->
                        _state.update {
                            it.copy(lanState = it.lanState.copy(progress = progress))
                        }
                    }
                )
                _state.update {
                    it.copy(
                        lanState = it.lanState.copy(
                            isScanning = false,
                            hosts = hosts,
                            progress = 1f,
                            error = if (hosts.isEmpty()) "Keine erreichbaren Hosts gefunden" else null
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Network scan failed")
                _state.update {
                    it.copy(
                        lanState = it.lanState.copy(
                            isScanning = false,
                            error = e.message ?: "Scan fehlgeschlagen"
                        )
                    )
                }
            }
        }
    }

    fun startPortScan() {
        val current = _state.value.portState
        if (current.isScanning) return
        val target = current.target.trim()
        if (target.isBlank()) {
            _state.update {
                it.copy(
                    portState = it.portState.copy(
                        error = "Bitte Host oder IP eingeben"
                    )
                )
            }
            return
        }

        val preset = current.selectedPreset
        val (start, end) = if (preset != null && preset.start > 0 && preset.end > 0) {
            preset.start to preset.end
        } else {
            current.customStart to current.customEnd
        }

        if (start <= 0 || end <= 0 || end < start) {
            _state.update {
                it.copy(
                    portState = it.portState.copy(
                        error = "Ungültiger Portbereich"
                    )
                )
            }
            return
        }

        _state.update {
            it.copy(
                portState = it.portState.copy(
                    isScanning = true,
                    progress = 0f,
                    error = null,
                    results = emptyList()
                )
            )
        }

        viewModelScope.launch {
            try {
                val results = portScanner.scan(
                    host = target,
                    startPort = start,
                    endPort = end,
                    onProgress = { progress ->
                        _state.update {
                            it.copy(
                                portState = it.portState.copy(progress = progress)
                            )
                        }
                    }
                )
                _state.update {
                    it.copy(
                        portState = it.portState.copy(
                            isScanning = false,
                            results = results,
                            progress = 1f,
                            error = if (results.isEmpty()) "Keine offenen Ports gefunden" else null
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Port scan failed")
                _state.update {
                    it.copy(
                        portState = it.portState.copy(
                            isScanning = false,
                            error = e.message ?: "Scan fehlgeschlagen"
                        )
                    )
                }
            }
        }
    }

    fun startSslScan() {
        val target = _state.value.sslState.target.trim()
        if (_state.value.sslState.isRunning) return
        if (target.isEmpty()) {
            _state.update {
                it.copy(
                    sslState = it.sslState.copy(
                        error = "Bitte Ziel angeben"
                    )
                )
            }
            return
        }

        _state.update {
            it.copy(
                sslState = it.sslState.copy(
                    isRunning = true,
                    error = null,
                    report = null
                )
            )
        }

        viewModelScope.launch {
            try {
                val report = sslScanner.scan(target)
                _state.update {
                    it.copy(
                        sslState = it.sslState.copy(
                            isRunning = false,
                            report = report,
                            error = null
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "SSL scan failed")
                _state.update {
                    it.copy(
                        sslState = it.sslState.copy(
                            isRunning = false,
                            error = e.message ?: "SSL-Scan fehlgeschlagen"
                        )
                    )
                }
            }
        }
    }

    fun clearSslReport() {
        _state.update { it.copy(sslState = it.sslState.copy(report = null, error = null)) }
    }
}
