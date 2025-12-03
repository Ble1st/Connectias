package com.ble1st.connectias.feature.network.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for WHOIS Lookup screen.
 */
@HiltViewModel
class WhoisViewModel @Inject constructor(
    private val whoisProvider: WhoisProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhoisUiState())
    val uiState: StateFlow<WhoisUiState> = _uiState.asStateFlow()

    /**
     * Performs a domain WHOIS lookup.
     */
    fun lookupDomain(domain: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = whoisProvider.lookupDomain(domain)
            _uiState.update { it.copy(
                isLoading = false,
                result = result,
                error = if (!result.success) result.error else null
            ) }
        }
    }

    /**
     * Performs an IP WHOIS lookup.
     */
    fun lookupIp(ip: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = whoisProvider.lookupIp(ip)
            _uiState.update { it.copy(
                isLoading = false,
                result = result,
                error = if (!result.success) result.error else null
            ) }
        }
    }

    /**
     * Clears the results.
     */
    fun clearResults() {
        _uiState.update { WhoisUiState() }
    }
}

/**
 * UI state for WHOIS Lookup screen.
 */
data class WhoisUiState(
    val isLoading: Boolean = false,
    val result: WhoisResult? = null,
    val error: String? = null
)

