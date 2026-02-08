// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.ui.servicelogging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.servicestate.ServiceIds
import com.ble1st.connectias.core.servicestate.ServiceStateRepository
import com.ble1st.connectias.service.logging.ExternalLogParcel
import com.ble1st.connectias.service.logging.LoggingServiceProxy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Service Log Viewer: displays logs from LoggingService (external apps).
 */
@HiltViewModel
class ServiceLogViewerViewModel @Inject constructor(
    private val loggingServiceProxy: LoggingServiceProxy,
    serviceStateRepository: ServiceStateRepository
) : ViewModel() {

    val serviceEnabled: StateFlow<Boolean> = serviceStateRepository.observeState
        .map { it[ServiceIds.LOGGING_SERVICE] ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = serviceStateRepository.isEnabled(ServiceIds.LOGGING_SERVICE)
        )

    private val _logs = MutableStateFlow<List<ExternalLogParcel>>(emptyList())
    val logs: StateFlow<List<ExternalLogParcel>> = _logs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadLogs()
    }

    fun loadLogs(limit: Int = 1000) {
        viewModelScope.launch {
            if (!serviceEnabled.value) {
                _error.value = "Logging Service is disabled"
                return@launch
            }
            _isLoading.value = true
            _error.value = null
            try {
                val result = loggingServiceProxy.getRecentLogs(limit)
                _logs.value = result
            } catch (e: Exception) {
                _error.value = "Failed to load logs: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
