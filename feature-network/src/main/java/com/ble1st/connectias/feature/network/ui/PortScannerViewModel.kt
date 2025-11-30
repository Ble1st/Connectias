package com.ble1st.connectias.feature.network.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.network.models.PortScanResult
import com.ble1st.connectias.feature.network.provider.PortScannerProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Port Scanner.
 */
@HiltViewModel
class PortScannerViewModel @Inject constructor(
    private val portScannerProvider: PortScannerProvider
) : ViewModel() {

    private val _portScanState = MutableStateFlow<PortScanState>(PortScanState.Idle)
    val portScanState: StateFlow<PortScanState> = _portScanState.asStateFlow()

    fun scanPorts(host: String, ports: List<Int>?) {
        viewModelScope.launch {
            _portScanState.value = PortScanState.Scanning
            val result = portScannerProvider.scanHost(host, ports)
            when (result) {
                is com.ble1st.connectias.feature.network.models.NetworkResult.Success -> {
                    _portScanState.value = PortScanState.Success(result.data.items)
                }
                is com.ble1st.connectias.feature.network.models.NetworkResult.Error -> {
                    _portScanState.value = PortScanState.Error(result.message)
                }
            }
        }
    }
}

/**
 * State representation for port scanning.
 */
sealed class PortScanState {
    object Idle : PortScanState()
    object Scanning : PortScanState()
    data class Success(val results: List<PortScanResult>) : PortScanState()
    data class Error(val message: String) : PortScanState()
}
