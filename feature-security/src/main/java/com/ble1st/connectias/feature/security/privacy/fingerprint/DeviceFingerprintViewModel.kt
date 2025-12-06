package com.ble1st.connectias.feature.security.privacy.fingerprint

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
 * ViewModel for Device Fingerprint screen.
 */
@HiltViewModel
class DeviceFingerprintViewModel @Inject constructor(
    private val fingerprintProvider: DeviceFingerprintProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceFingerprintUiState())
    val uiState: StateFlow<DeviceFingerprintUiState> = _uiState.asStateFlow()

    /**
     * Generates a device fingerprint.
     */
    fun generateFingerprint() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val fingerprint = fingerprintProvider.generateFingerprint()
            val recommendations = fingerprintProvider.getPrivacyRecommendations(fingerprint)
            _uiState.update { it.copy(
                isLoading = false,
                fingerprint = fingerprint,
                recommendations = recommendations
            ) }
        }
    }

    /**
     * Filters components by category.
     */
    fun filterByCategory(category: ComponentCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * Clears the results.
     */
    fun clearResults() {
        _uiState.update { DeviceFingerprintUiState() }
    }
}

/**
 * UI state for Device Fingerprint screen.
 */
data class DeviceFingerprintUiState(
    val isLoading: Boolean = false,
    val fingerprint: DeviceFingerprint? = null,
    val recommendations: List<PrivacyRecommendation> = emptyList(),
    val selectedCategory: ComponentCategory? = null
)

