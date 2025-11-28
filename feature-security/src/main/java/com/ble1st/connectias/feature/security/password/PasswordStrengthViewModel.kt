package com.ble1st.connectias.feature.security.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Password Strength Checker.
 */
@HiltViewModel
class PasswordStrengthViewModel @Inject constructor(
    private val passwordStrengthProvider: PasswordStrengthProvider
) : ViewModel() {

    private val _passwordState = MutableStateFlow<PasswordState>(PasswordState.Idle)
    val passwordState: StateFlow<PasswordState> = _passwordState.asStateFlow()

    /**
     * Analyzes password strength.
     */
    fun analyzePassword(password: String) {
        viewModelScope.launch {
            val strength = passwordStrengthProvider.analyzePassword(password)
            _passwordState.value = PasswordState.Analyzed(strength)
        }
    }

    /**
     * Generates a secure password.
     */
    fun generatePassword(length: Int = 16, includeSpecial: Boolean = true) {
        viewModelScope.launch {
            val password = passwordStrengthProvider.generatePassword(length, includeSpecial)
            _passwordState.value = PasswordState.Generated(password)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _passwordState.value = PasswordState.Idle
    }
}

/**
 * State representation for password operations.
 */
sealed class PasswordState {
    object Idle : PasswordState()
    data class Analyzed(val strength: PasswordStrength) : PasswordState()
    data class Generated(val password: String) : PasswordState()
}

