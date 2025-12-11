package com.ble1st.connectias.feature.dnstools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.dnstools.data.DnsHistoryEntity
import com.ble1st.connectias.feature.dnstools.data.DnsToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.xbill.DNS.Type
import javax.inject.Inject

@HiltViewModel
class DnsToolsViewModel @Inject constructor(
    private val repository: DnsToolsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DnsToolsUiState())
    val state: StateFlow<DnsToolsUiState> = _state

    init {
        viewModelScope.launch {
            repository.history.collect { history ->
                _state.update { it.copy(history = history) }
            }
        }
    }

    fun onDnsQueryChanged(query: String) {
        _state.update { it.copy(dnsQuery = query) }
    }
    
    fun onRecordTypeSelected(type: DnsRecordType) {
        _state.update { it.copy(selectedRecordType = type) }
    }

    fun resolveDns() {
        val query = state.value.dnsQuery
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(dnsLoading = true, dnsResult = null) }
            val result = repository.resolveDns(query, state.value.selectedRecordType.dnsType)
            _state.update { it.copy(dnsResult = result, dnsLoading = false) }
        }
    }

    fun fetchDmarc() {
        val query = state.value.dnsQuery
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(dnsLoading = true, dnsResult = null) }
            val result = repository.fetchDmarc(query)
            _state.update { it.copy(dnsResult = result, dnsLoading = false) }
        }
    }
    
    fun fetchWhois() {
        val query = state.value.dnsQuery
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(dnsLoading = true) } 
            repository.fetchWhois(query) // This saves to history
            _state.update { it.copy(dnsLoading = false) }
        }
    }

    fun onSubnetCidrChanged(cidr: String) {
        _state.update { it.copy(subnetCidr = cidr) }
    }

    fun calculateSubnet() {
        val cidr = state.value.subnetCidr
        if (cidr.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(subnetLoading = true, subnetResult = null) }
            val result = repository.calculateSubnet(cidr)
            _state.update { it.copy(subnetResult = result, subnetLoading = false) }
        }
    }

    fun onPingHostChanged(host: String) {
        _state.update { it.copy(pingHost = host) }
    }

    fun pingHost() {
        val host = state.value.pingHost
        if (host.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(pingLoading = true, pingResult = null) }
            val result = repository.pingHost(host)
            _state.update { it.copy(pingResult = result, pingLoading = false) }
        }
    }

    fun checkCaptivePortal() {
        viewModelScope.launch {
            _state.update { it.copy(captivePortalLoading = true, captivePortalResult = null) }
            val result = repository.detectCaptivePortal()
            _state.update { it.copy(captivePortalResult = result, captivePortalLoading = false) }
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
    
    fun deleteHistoryItem(item: DnsHistoryEntity) {
        viewModelScope.launch {
            repository.deleteHistoryItem(item)
        }
    }
}