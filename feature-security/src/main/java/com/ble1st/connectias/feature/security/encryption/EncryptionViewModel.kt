package com.ble1st.connectias.feature.security.encryption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Encryption Tools.
 */
@HiltViewModel
class EncryptionViewModel @Inject constructor(
    private val encryptionProvider: EncryptionProvider
) : ViewModel() {

    private val _encryptionState = MutableStateFlow<EncryptionState>(EncryptionState.Idle)
    val encryptionState: StateFlow<EncryptionState> = _encryptionState.asStateFlow()

    /**
     * Encrypts text.
     */
    fun encryptText(plaintext: String, password: String) {
        if (plaintext.isBlank()) {
            _encryptionState.value = EncryptionState.Error("Text cannot be empty")
            return
        }
        if (password.isBlank()) {
            _encryptionState.value = EncryptionState.Error("Password cannot be empty")
            return
        }

        viewModelScope.launch {
            _encryptionState.value = EncryptionState.Processing
            val result = encryptionProvider.encryptText(plaintext, password)
            if (result.success) {
                _encryptionState.value = EncryptionState.Encrypted(result.encryptedData, result.iv)
            } else {
                _encryptionState.value = EncryptionState.Error(result.error ?: "Encryption failed")
            }
        }
    }

    /**
     * Decrypts text.
     */
    fun decryptText(encryptedData: String, iv: String, password: String) {
        if (encryptedData.isBlank() || iv.isBlank()) {
            _encryptionState.value = EncryptionState.Error("Encrypted data and IV cannot be empty")
            return
        }
        if (password.isBlank()) {
            _encryptionState.value = EncryptionState.Error("Password cannot be empty")
            return
        }

        viewModelScope.launch {
            _encryptionState.value = EncryptionState.Processing
            val result = encryptionProvider.decryptText(encryptedData, iv, password)
            if (result.success) {
                _encryptionState.value = EncryptionState.Decrypted(result.plaintext)
            } else {
                _encryptionState.value = EncryptionState.Error(result.error ?: "Decryption failed")
            }
        }
    }

    /**
     * Generates a random key.
     */
    fun generateKey() {
        viewModelScope.launch {
            val key = encryptionProvider.generateKey()
            _encryptionState.value = EncryptionState.KeyGenerated(key)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _encryptionState.value = EncryptionState.Idle
    }
}

/**
 * State representation for encryption operations.
 */
sealed class EncryptionState {
    object Idle : EncryptionState()
    object Processing : EncryptionState()
    data class Encrypted(val encryptedData: String, val iv: String) : EncryptionState()
    data class Decrypted(val plaintext: String) : EncryptionState()
    data class KeyGenerated(val key: String) : EncryptionState()
    data class Error(val message: String) : EncryptionState()
}

