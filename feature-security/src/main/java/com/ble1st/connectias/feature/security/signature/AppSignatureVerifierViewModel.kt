package com.ble1st.connectias.feature.security.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.security.signature.models.SignatureResult
import com.ble1st.connectias.feature.security.signature.models.SuspiciousApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for App Signature Verifier screen.
 */
@HiltViewModel
class AppSignatureVerifierViewModel @Inject constructor(
    private val signatureVerifier: AppSignatureVerifierProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignatureVerifierUiState())
    val uiState: StateFlow<SignatureVerifierUiState> = _uiState.asStateFlow()

    /**
     * Verifies signature for a package.
     */
    fun verifySignature(packageName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = signatureVerifier.verifySignature(packageName)
            _uiState.update { it.copy(
                isLoading = false,
                result = result
            ) }
        }
    }

    /**
     * Detects repackaged apps.
     */
    fun detectRepackagedApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val suspicious = signatureVerifier.detectRepackagedApps()
            _uiState.update { it.copy(
                isScanning = false,
                suspiciousApps = suspicious
            ) }
        }
    }

    /**
     * Clears the results.
     */
    fun clearResults() {
        _uiState.update { SignatureVerifierUiState() }
    }
}

/**
 * UI state for Signature Verifier screen.
 */
data class SignatureVerifierUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val result: SignatureResult? = null,
    val suspiciousApps: List<SuspiciousApp> = emptyList(),
    val error: String? = null
)

