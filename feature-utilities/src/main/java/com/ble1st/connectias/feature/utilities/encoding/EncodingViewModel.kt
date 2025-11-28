package com.ble1st.connectias.feature.utilities.encoding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Encoding/Decoding Tools.
 */
@HiltViewModel
class EncodingViewModel @Inject constructor(
    private val encodingProvider: EncodingProvider
) : ViewModel() {

    private val _encodingState = MutableStateFlow<EncodingState>(EncodingState.Idle)
    val encodingState: StateFlow<EncodingState> = _encodingState.asStateFlow()

    /**
     * Encodes the given text.
     */
    fun encode(text: String, encodingType: EncodingProvider.EncodingType) {
        if (text.isBlank()) {
            _encodingState.value = EncodingState.Error("Text cannot be empty")
            return
        }

        viewModelScope.launch {
            _encodingState.value = EncodingState.Loading
            val encoded = encodingProvider.encode(text, encodingType)
            _encodingState.value = if (encoded != null) {
                EncodingState.Success(encoded)
            } else {
                EncodingState.Error("Failed to encode text")
            }
        }
    }

    /**
     * Decodes the given text.
     */
    fun decode(text: String, encodingType: EncodingProvider.EncodingType) {
        if (text.isBlank()) {
            _encodingState.value = EncodingState.Error("Text cannot be empty")
            return
        }

        viewModelScope.launch {
            _encodingState.value = EncodingState.Loading
            val decoded = encodingProvider.decode(text, encodingType)
            _encodingState.value = if (decoded != null) {
                EncodingState.Success(decoded)
            } else {
                EncodingState.Error("Failed to decode text")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _encodingState.value = EncodingState.Idle
    }
}

/**
 * State representation for encoding operations.
 */
sealed class EncodingState {
    object Idle : EncodingState()
    object Loading : EncodingState()
    data class Success(val result: String) : EncodingState()
    data class Error(val message: String) : EncodingState()
}

