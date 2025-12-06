package com.ble1st.connectias.feature.security.privacy.dns

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
 * ViewModel for Private DNS screen.
 */
@HiltViewModel
class PrivateDnsViewModel @Inject constructor(
    private val privateDnsProvider: PrivateDnsProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateDnsUiState())
    val uiState: StateFlow<PrivateDnsUiState> = _uiState.asStateFlow()

    val availableProviders = privateDnsProvider.availableProviders

    /**
     * Resolves a domain using DNS-over-HTTPS.
     */
    fun resolveDoH(domain: String, provider: DnsProvider, recordType: DnsRecordType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = privateDnsProvider.resolveDoH(domain, provider, recordType)
            _uiState.update { it.copy(
                isLoading = false,
                result = result,
                error = if (!result.success) result.error else null
            ) }
        }
    }

    /**
     * Tests a DNS provider.
     */
    fun testProvider(provider: DnsProvider) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val testResult = privateDnsProvider.testProvider(provider)
            _uiState.update { it.copy(
                isTesting = false,
                testResult = testResult
            ) }
        }
    }

    /**
     * Sets the selected provider.
     */
    fun selectProvider(provider: DnsProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
    }

    /**
     * Clears the results.
     */
    fun clearResults() {
        _uiState.update { PrivateDnsUiState() }
    }
}

/**
 * UI state for Private DNS screen.
 */
data class PrivateDnsUiState(
    val isLoading: Boolean = false,
    val isTesting: Boolean = false,
    val result: DnsResult? = null,
    val testResult: ProviderTestResult? = null,
    val selectedProvider: DnsProvider? = null,
    val error: String? = null
)

