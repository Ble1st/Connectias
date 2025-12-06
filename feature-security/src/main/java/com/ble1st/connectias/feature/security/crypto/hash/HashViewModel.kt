package com.ble1st.connectias.feature.security.crypto.hash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Hash & Checksum Tools.
 */
@HiltViewModel
class HashViewModel @Inject constructor(
    private val hashProvider: HashProvider
) : ViewModel() {

    private val _hashState = MutableStateFlow<HashState>(HashState.Idle)
    val hashState: StateFlow<HashState> = _hashState.asStateFlow()

    /**
     * Calculates hash for the given text.
     */
    fun calculateTextHash(text: String, algorithm: HashProvider.HashAlgorithm) {
        if (text.isBlank()) {
            _hashState.value = HashState.Error("Text cannot be empty")
            return
        }

        viewModelScope.launch {
            _hashState.value = HashState.Loading
            val hash = hashProvider.calculateTextHash(text, algorithm)
            _hashState.value = if (hash != null) {
                HashState.Success(hash)
            } else {
                HashState.Error("Failed to calculate hash")
            }
        }
    }

    /**
     * Calculates hash for the given file.
     */
    fun calculateFileHash(filePath: String, algorithm: HashProvider.HashAlgorithm) {
        viewModelScope.launch {
            _hashState.value = HashState.Loading
            val hash = hashProvider.calculateFileHash(filePath, algorithm)
            _hashState.value = if (hash != null) {
                HashState.Success(hash)
            } else {
                HashState.Error("Failed to calculate file hash")
            }
        }
    }

    /**
     * Verifies hash against given text.
     */
    fun verifyTextHash(text: String, expectedHash: String, algorithm: HashProvider.HashAlgorithm) {
        if (text.isBlank() || expectedHash.isBlank()) {
            _hashState.value = HashState.Error("Text and hash cannot be empty")
            return
        }

        viewModelScope.launch {
            _hashState.value = HashState.Loading
            val isValid = hashProvider.verifyTextHash(text, expectedHash, algorithm)
            _hashState.value = if (isValid) {
                HashState.VerificationSuccess("Hash verification successful")
            } else {
                HashState.VerificationFailed("Hash verification failed")
            }
        }
    }

    /**
     * Verifies hash against given file.
     */
    fun verifyFileHash(filePath: String, expectedHash: String, algorithm: HashProvider.HashAlgorithm) {
        viewModelScope.launch {
            _hashState.value = HashState.Loading
            val isValid = hashProvider.verifyFileHash(filePath, expectedHash, algorithm)
            _hashState.value = if (isValid) {
                HashState.VerificationSuccess("File hash verification successful")
            } else {
                HashState.VerificationFailed("File hash verification failed")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _hashState.value = HashState.Idle
    }
}

/**
 * State representation for hash operations.
 */
sealed class HashState {
    object Idle : HashState()
    object Loading : HashState()
    data class Success(val hash: String) : HashState()
    data class Error(val message: String) : HashState()
    data class VerificationSuccess(val message: String) : HashState()
    data class VerificationFailed(val message: String) : HashState()
}

