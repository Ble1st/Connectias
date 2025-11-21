package com.ble1st.connectias.feature.security.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import com.ble1st.connectias.core.services.SecurityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityDashboardViewModel @Inject constructor(
    private val securityService: SecurityService
) : ViewModel() {

    private val _securityState = MutableStateFlow<SecurityState>(SecurityState.Loading)
    val securityState: StateFlow<SecurityState> = _securityState.asStateFlow()

    init {
        performSecurityCheck()
    }

    fun performSecurityCheck() {
        viewModelScope.launch {
            _securityState.value = SecurityState.Loading
            try {
                val result = securityService.performSecurityCheck()
                _securityState.value = SecurityState.Success(result)
            } catch (e: Exception) {
                _securityState.value = SecurityState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class SecurityState {
    object Loading : SecurityState()
    data class Success(val result: SecurityCheckResult) : SecurityState()
    data class Error(val message: String) : SecurityState()
}

