package com.ble1st.connectias.feature.dnstools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.dnstools.data.DnsToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DnsToolsViewModel @Inject constructor(
    private val repository: DnsToolsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DnsToolsUiState())
    val state: StateFlow<DnsToolsUiState> = _state

    private var currentJob: Job? = null

    fun onDomainChanged(value: String) {
        _state.update { it.copy(domainInput = value) }
    }

    fun onRecordTypeChanged(type: DnsRecordType) {
        _state.update { it.copy(recordType = type) }
    }

    fun onSubnetInputChanged(value: String) {
        _state.update { it.copy(subnetInput = value) }
    }

    fun onPingHostChanged(value: String) {
        _state.update { it.copy(pingHost = value) }
    }

    fun onMarkdownChanged(value: String) {
        _state.update { state ->
            state.copy(markdownDocument = state.markdownDocument.copy(content = value))
        }
    }

    fun onMarkdownNameChanged(value: String) {
        _state.update { state ->
            state.copy(markdownDocument = state.markdownDocument.copy(name = value))
        }
    }

    fun resolveDns() {
        launchAsync {
            val result = repository.resolveDns(state.value.domainInput, state.value.recordType.dnsType)
            _state.update { it.copy(dnsResult = result) }
        }
    }

    fun fetchDmarc() {
        launchAsync {
            val result = repository.fetchDmarc(state.value.domainInput)
            _state.update { it.copy(dmarcResult = result) }
        }
    }

    fun fetchWhois() {
        launchAsync {
            val result = repository.fetchWhois(state.value.domainInput)
            _state.update { it.copy(whoisResult = result) }
        }
    }

    fun calculateSubnet() {
        val result = repository.calculateSubnet(state.value.subnetInput)
        _state.update { it.copy(subnetResult = result) }
    }

    fun pingHost() {
        launchAsync {
            val result = repository.pingHost(state.value.pingHost)
            _state.update { it.copy(pingResult = result) }
        }
    }

    fun detectCaptivePortal() {
        launchAsync {
            val result = repository.detectCaptivePortal()
            _state.update { it.copy(captivePortalResult = result) }
        }
    }

    private fun launchAsync(block: suspend () -> Unit) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                block()
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = t.message) }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
