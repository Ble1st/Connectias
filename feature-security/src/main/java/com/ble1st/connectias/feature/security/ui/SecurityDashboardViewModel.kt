package com.ble1st.connectias.feature.security.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import com.ble1st.connectias.core.services.SecurityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SecurityDashboardViewModel @Inject constructor(
    private val securityService: SecurityService
) : ViewModel() {

    private val _securityState = MutableStateFlow<SecurityState>(SecurityState.Loading)
    val securityState: StateFlow<SecurityState> = _securityState.asStateFlow()
    
    // Track the current security check job to cancel previous requests
    private var securityCheckJob: Job? = null

    init {
        performSecurityCheck()
    }

    fun performSecurityCheck() {
        // Cancel previous job if still running
        securityCheckJob?.cancel()
        
        securityCheckJob = viewModelScope.launch {
            _securityState.value = SecurityState.Loading
            try {
                val result = securityService.performSecurityCheck()
                _securityState.value = SecurityState.Success(result)
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is IOException -> "Network error. Please check your connection."
                    is SecurityException -> "Security check failed. Please try again."
                    else -> e.message ?: "An unexpected error occurred"
                }
                _securityState.value = SecurityState.Error(errorMessage)
                // Consider logging the exception for debugging
            }
        }
    }
}

sealed class SecurityState {
    object Loading : SecurityState()
    data class Success(val result: SecurityCheckResult) : SecurityState()
    data class Error(val message: String) : SecurityState()
}

