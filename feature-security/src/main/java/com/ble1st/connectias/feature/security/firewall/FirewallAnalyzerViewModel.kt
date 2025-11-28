package com.ble1st.connectias.feature.security.firewall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Firewall Analyzer.
 */
@HiltViewModel
class FirewallAnalyzerViewModel @Inject constructor(
    private val firewallAnalyzerProvider: FirewallAnalyzerProvider
) : ViewModel() {

    private val _firewallState = MutableStateFlow<FirewallState>(FirewallState.Idle)
    val firewallState: StateFlow<FirewallState> = _firewallState.asStateFlow()

    /**
     * Analyzes app network permissions.
     */
    fun analyzeApps() {
        viewModelScope.launch {
            _firewallState.value = FirewallState.Loading
            val apps = firewallAnalyzerProvider.getAppNetworkPermissions()
            val riskyApps = firewallAnalyzerProvider.analyzeRiskyApps(apps)
            _firewallState.value = FirewallState.Success(apps, riskyApps)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _firewallState.value = FirewallState.Idle
    }
}

/**
 * State representation for firewall operations.
 */
sealed class FirewallState {
    object Idle : FirewallState()
    object Loading : FirewallState()
    data class Success(val apps: List<AppNetworkInfo>, val riskyApps: List<RiskyApp>) : FirewallState()
    data class Error(val message: String) : FirewallState()
}

