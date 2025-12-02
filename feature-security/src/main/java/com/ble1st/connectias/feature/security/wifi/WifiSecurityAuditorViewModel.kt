package com.ble1st.connectias.feature.security.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.security.wifi.models.EncryptionAssessment
import com.ble1st.connectias.feature.security.wifi.models.EvilTwinResult
import com.ble1st.connectias.feature.security.wifi.models.SuspiciousAP
import com.ble1st.connectias.feature.security.wifi.models.WifiSecurityReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for WiFi Security Auditor functionality.
 */
@HiltViewModel
class WifiSecurityAuditorViewModel @Inject constructor(
    private val wifiAuditorProvider: WifiSecurityAuditorProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiAuditorUiState())
    val uiState: StateFlow<WifiAuditorUiState> = _uiState.asStateFlow()

    val auditHistory: StateFlow<List<WifiSecurityReport>> = wifiAuditorProvider.auditHistory

    init {
        auditCurrentNetwork()
    }

    /**
     * Audits the current WiFi network.
     */
    fun auditCurrentNetwork() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuditing = true, error = null) }

            try {
                val report = wifiAuditorProvider.auditCurrentNetwork()
                _uiState.update { it.copy(
                    isAuditing = false,
                    currentReport = report,
                    error = if (report == null) "Not connected to WiFi" else null
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error auditing network")
                _uiState.update { it.copy(
                    isAuditing = false,
                    error = "Audit failed: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Scans for rogue access points.
     */
    fun scanForRogueAPs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningRogue = true) }

            try {
                val rogueAPs = wifiAuditorProvider.detectRogueAPs()
                _uiState.update { it.copy(
                    isScanningRogue = false,
                    suspiciousAPs = rogueAPs
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error scanning for rogue APs")
                _uiState.update { it.copy(
                    isScanningRogue = false,
                    error = "Scan failed: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Checks for evil twin attacks.
     */
    fun checkForEvilTwin() {
        viewModelScope.launch {
            val ssid = _uiState.value.currentReport?.ssid ?: return@launch
            
            _uiState.update { it.copy(isCheckingEvilTwin = true) }

            try {
                val result = wifiAuditorProvider.detectEvilTwin(ssid)
                _uiState.update { it.copy(
                    isCheckingEvilTwin = false,
                    evilTwinResult = result
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for evil twin")
                _uiState.update { it.copy(
                    isCheckingEvilTwin = false,
                    error = "Check failed: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Checks encryption strength.
     */
    fun checkEncryption() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingEncryption = true) }

            try {
                val assessment = wifiAuditorProvider.checkEncryptionStrength()
                _uiState.update { it.copy(
                    isCheckingEncryption = false,
                    encryptionAssessment = assessment
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error checking encryption")
                _uiState.update { it.copy(
                    isCheckingEncryption = false,
                    error = "Check failed: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Runs a full security scan.
     */
    fun runFullScan() {
        viewModelScope.launch {
            auditCurrentNetwork()
            scanForRogueAPs()
            checkForEvilTwin()
            checkEncryption()
        }
    }

    /**
     * Clears error.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/**
 * UI state for WiFi Security Auditor.
 */
data class WifiAuditorUiState(
    val isAuditing: Boolean = false,
    val isScanningRogue: Boolean = false,
    val isCheckingEvilTwin: Boolean = false,
    val isCheckingEncryption: Boolean = false,
    val currentReport: WifiSecurityReport? = null,
    val suspiciousAPs: List<SuspiciousAP> = emptyList(),
    val evilTwinResult: EvilTwinResult? = null,
    val encryptionAssessment: EncryptionAssessment? = null,
    val error: String? = null
) {
    val isScanning: Boolean
        get() = isAuditing || isScanningRogue || isCheckingEvilTwin || isCheckingEncryption

    val hasSecurityIssues: Boolean
        get() = currentReport?.vulnerabilities?.isNotEmpty() == true ||
                suspiciousAPs.isNotEmpty() ||
                evilTwinResult?.isDetected == true
}
