package com.ble1st.connectias.feature.password.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.password.data.PasswordGeneratorConfig
import com.ble1st.connectias.feature.password.data.PasswordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val repository: PasswordRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PasswordUiState())
    val state: StateFlow<PasswordUiState> = _state

    fun onPasswordInputChanged(value: String) {
        _state.update { it.copy(passwordInput = value) }
        val result = repository.checkPassword(value)
        _state.update { it.copy(passwordCheck = result) }
    }

    fun onGeneratorConfigChanged(config: PasswordGeneratorConfig) {
        _state.update { it.copy(generatorConfig = config) }
    }

    fun generatePassword() {
        viewModelScope.launch {
            val generated = repository.generatePassword(state.value.generatorConfig)
            _state.update { it.copy(generatedPassword = generated, passwordInput = generated) }
            onPasswordInputChanged(generated)
        }
    }
}
