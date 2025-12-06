package com.ble1st.connectias.feature.security.privacy.leakage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Data Leakage Scanner.
 */
@HiltViewModel
class DataLeakageViewModel @Inject constructor(
    private val dataLeakageProvider: DataLeakageProvider
) : ViewModel() {

    private val _leakageState = MutableStateFlow<DataLeakageState>(DataLeakageState.Idle)
    val leakageState: StateFlow<DataLeakageState> = _leakageState.asStateFlow()

    private var monitoringJob: Job? = null

    /**
     * Starts clipboard monitoring.
     */
    fun startClipboardMonitoring() {
        if (monitoringJob == null || monitoringJob?.isCompleted == true) {
            monitoringJob = viewModelScope.launch {
                dataLeakageProvider.monitorClipboard().collect { entry ->
                    _leakageState.value = DataLeakageState.ClipboardEntryState(entry)
                }
            }
            _leakageState.value = DataLeakageState.Monitoring(true)
        }
    }

    /**
     * Stops clipboard monitoring.
     */
    fun stopClipboardMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _leakageState.value = DataLeakageState.Monitoring(false)
    }

    /**
     * Gets apps with clipboard access.
     */
    fun getAppsWithClipboardAccess() {
        viewModelScope.launch {
            _leakageState.value = DataLeakageState.Loading
            val apps = dataLeakageProvider.getAppsWithClipboardAccess()
            _leakageState.value = DataLeakageState.AppsWithAccess(apps)
        }
    }

    /**
     * Analyzes text sensitivity.
     */
    fun analyzeText(text: String) {
        viewModelScope.launch {
            val sensitivity = dataLeakageProvider.analyzeSensitivity(text)
            _leakageState.value = DataLeakageState.SensitivityAnalysis(sensitivity)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        stopClipboardMonitoring()
        _leakageState.value = DataLeakageState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopClipboardMonitoring()
    }
}

/**
 * State representation for data leakage operations.
 */
sealed class DataLeakageState {
    object Idle : DataLeakageState()
    object Loading : DataLeakageState()
    data class Monitoring(val isMonitoring: Boolean) : DataLeakageState()
    data class ClipboardEntryState(val entry: com.ble1st.connectias.feature.privacy.leakage.ClipboardEntry) : DataLeakageState()
    data class AppsWithAccess(val apps: List<AppClipboardAccess>) : DataLeakageState()
    data class SensitivityAnalysis(val sensitivity: SensitivityLevel) : DataLeakageState()
    data class Error(val message: String) : DataLeakageState()
}

