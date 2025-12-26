package com.ble1st.connectias.feature.password.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.password.data.PasswordGeneratorConfig
import com.ble1st.connectias.feature.password.data.PasswordHistoryEntity
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

    init {
        viewModelScope.launch {
            repository.history.collect { history ->
                _state.update { it.copy(history = history) }
            }
        }
    }

    fun onPasswordInputChanged(value: String) {
        _state.update { it.copy(passwordInput = value) }
        val result = repository.checkPassword(value)
        _state.update { it.copy(passwordCheck = result) }
    }

    fun onGeneratorConfigChanged(config: PasswordGeneratorConfig) {
        _state.update { it.copy(generatorConfig = config) }
    }
    
    fun setMode(isPassphrase: Boolean) {
        _state.update { it.copy(isPassphraseMode = isPassphrase) }
    }
    
    fun updatePassphraseWordCount(count: Int) {
        _state.update { it.copy(passphraseWordCount = count.coerceIn(2, 10)) }
    }
    
    fun updatePassphraseSeparator(separator: String) {
        _state.update { it.copy(passphraseSeparator = separator) }
    }

    fun generatePassword() {
        viewModelScope.launch {
            val generated = if (state.value.isPassphraseMode) {
                repository.generatePassphrase(state.value.passphraseWordCount, state.value.passphraseSeparator)
            } else {
                repository.generatePassword(state.value.generatorConfig)
            }
            _state.update { it.copy(generatedPassword = generated, passwordInput = generated) }
            onPasswordInputChanged(generated)
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
    
    fun deleteHistoryItem(item: PasswordHistoryEntity) {
        viewModelScope.launch {
            repository.deleteHistoryItem(item)
        }
    }
}

