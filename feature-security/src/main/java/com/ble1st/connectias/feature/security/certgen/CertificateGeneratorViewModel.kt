package com.ble1st.connectias.feature.security.certgen

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
 * ViewModel for Certificate Generator screen.
 */
@HiltViewModel
class CertificateGeneratorViewModel @Inject constructor(
    private val certificateGenerator: CertificateGeneratorProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(CertificateGeneratorUiState())
    val uiState: StateFlow<CertificateGeneratorUiState> = _uiState.asStateFlow()

    /**
     * Generates a self-signed certificate.
     */
    fun generateCertificate(config: CertificateConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            val result = certificateGenerator.generateSelfSignedCertificate(config)
            _uiState.update { it.copy(
                isGenerating = false,
                result = result
            ) }
        }
    }

    /**
     * Generates a CSR.
     */
    fun generateCsr(config: CsrConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            val result = certificateGenerator.generateCSR(config)
            _uiState.update { it.copy(
                isGenerating = false,
                csrResult = result
            ) }
        }
    }

    /**
     * Lists keystore entries.
     */
    fun listKeystoreEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val entries = certificateGenerator.listKeystoreCertificates()
            _uiState.update { it.copy(
                isLoading = false,
                keystoreEntries = entries
            ) }
        }
    }

    /**
     * Validates a PEM certificate.
     */
    fun validateCertificate(pem: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true) }
            val validation = certificateGenerator.validatePemCertificate(pem)
            _uiState.update { it.copy(
                isValidating = false,
                validation = validation
            ) }
        }
    }

    /**
     * Clears the results.
     */
    fun clearResults() {
        _uiState.update { CertificateGeneratorUiState() }
    }
}

/**
 * UI state for Certificate Generator screen.
 */
data class CertificateGeneratorUiState(
    val isGenerating: Boolean = false,
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val result: CertificateResult? = null,
    val csrResult: CsrResult? = null,
    val keystoreEntries: List<KeystoreEntry> = emptyList(),
    val validation: CertificateValidation? = null,
    val error: String? = null
)

