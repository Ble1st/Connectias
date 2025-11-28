package com.ble1st.connectias.feature.security.certificate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Certificate Analyzer.
 */
@HiltViewModel
class CertificateAnalyzerViewModel @Inject constructor(
    private val certificateAnalyzerProvider: CertificateAnalyzerProvider
) : ViewModel() {

    private val _certificateState = MutableStateFlow<CertificateState>(CertificateState.Idle)
    val certificateState: StateFlow<CertificateState> = _certificateState.asStateFlow()

    /**
     * Analyzes certificate from URL.
     */
    fun analyzeCertificate(url: String) {
        if (url.isBlank()) {
            _certificateState.value = CertificateState.Error("URL cannot be empty")
            return
        }

        viewModelScope.launch {
            _certificateState.value = CertificateState.Loading
            val info = certificateAnalyzerProvider.analyzeCertificateFromUrl(url)
            if (info != null) {
                _certificateState.value = CertificateState.Success(info)
            } else {
                _certificateState.value = CertificateState.Error("Failed to analyze certificate")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _certificateState.value = CertificateState.Idle
    }
}

/**
 * State representation for certificate operations.
 */
sealed class CertificateState {
    object Idle : CertificateState()
    object Loading : CertificateState()
    data class Success(val info: CertificateInfo) : CertificateState()
    data class Error(val message: String) : CertificateState()
}

