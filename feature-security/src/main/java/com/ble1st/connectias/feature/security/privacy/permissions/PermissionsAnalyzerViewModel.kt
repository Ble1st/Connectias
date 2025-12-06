package com.ble1st.connectias.feature.security.privacy.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Permissions Analyzer.
 */
@HiltViewModel
class PermissionsAnalyzerViewModel @Inject constructor(
    private val permissionsAnalyzerProvider: PermissionsAnalyzerProvider
) : ViewModel() {

    private val _permissionsState = MutableStateFlow<PermissionsState>(PermissionsState.Idle)
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    /**
     * Analyzes all app permissions.
     */
    fun analyzePermissions() {
        viewModelScope.launch {
            _permissionsState.value = PermissionsState.Loading
            val allApps = permissionsAnalyzerProvider.getAllAppPermissions()
            val riskyApps = permissionsAnalyzerProvider.getAppsWithRiskyPermissions()
            _permissionsState.value = PermissionsState.Success(allApps, riskyApps)
        }
    }

    /**
     * Gets recommendations for a specific app.
     */
    fun getRecommendations(appPermissions: AppPermissions) {
        viewModelScope.launch {
            val recommendations = permissionsAnalyzerProvider.getRecommendations(appPermissions)
            _permissionsState.value = PermissionsState.Recommendations(recommendations)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _permissionsState.value = PermissionsState.Idle
    }
}

/**
 * State representation for permissions operations.
 */
sealed class PermissionsState {
    object Idle : PermissionsState()
    object Loading : PermissionsState()
    data class Success(val allApps: List<AppPermissions>, val riskyApps: List<AppPermissions>) : PermissionsState()
    data class Recommendations(val recommendations: List<PermissionRecommendation>) : PermissionsState()
    data class Error(val message: String) : PermissionsState()
}

