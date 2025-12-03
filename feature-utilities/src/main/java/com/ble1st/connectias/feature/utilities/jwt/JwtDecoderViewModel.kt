package com.ble1st.connectias.feature.utilities.jwt

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for JWT Decoder screen.
 */
@HiltViewModel
class JwtDecoderViewModel @Inject constructor(
    private val jwtDecoderProvider: JwtDecoderProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(JwtDecoderUiState())
    val uiState: StateFlow<JwtDecoderUiState> = _uiState.asStateFlow()

    /**
     * Decodes a JWT token.
     */
    fun decodeToken(token: String) {
        val decoded = jwtDecoderProvider.decodeToken(token)
        _uiState.update { it.copy(
            inputToken = token,
            decodedToken = decoded,
            formattedPayload = jwtDecoderProvider.formatPayload(decoded)
        ) }
    }

    /**
     * Clears the current token.
     */
    fun clearToken() {
        _uiState.update { it.copy(
            inputToken = "",
            decodedToken = null,
            formattedPayload = null
        ) }
    }

    /**
     * Gets common JWT patterns.
     */
    fun getCommonPatterns(): List<JwtPattern> = jwtDecoderProvider.getCommonPatterns()
}

/**
 * UI state for JWT Decoder screen.
 */
data class JwtDecoderUiState(
    val inputToken: String = "",
    val decodedToken: JwtToken? = null,
    val formattedPayload: String? = null
)

