package com.ble1st.connectias.feature.utilities.text

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Text Tools.
 */
@HiltViewModel
class TextViewModel @Inject constructor(
    private val textProvider: TextProvider
) : ViewModel() {

    private val _textState = MutableStateFlow<TextState>(TextState.Idle)
    val textState: StateFlow<TextState> = _textState.asStateFlow()

    /**
     * Converts text case.
     */
    fun convertCase(text: String, caseType: TextProvider.CaseType) {
        viewModelScope.launch {
            _textState.value = TextState.Loading
            val converted = textProvider.convertCase(text, caseType)
            _textState.value = TextState.Converted(converted)
        }
    }

    /**
     * Counts words and characters.
     */
    fun countWordsAndChars(text: String) {
        viewModelScope.launch {
            val count = textProvider.countWordsAndChars(text)
            _textState.value = TextState.Counted(count)
        }
    }

    /**
     * Tests regex pattern.
     */
    fun testRegex(text: String, pattern: String) {
        if (pattern.isBlank()) {
            _textState.value = TextState.Error("Regex pattern cannot be empty")
            return
        }

        viewModelScope.launch {
            _textState.value = TextState.Loading
            val result = textProvider.testRegex(text, pattern)
            _textState.value = TextState.RegexTested(result)
        }
    }

    /**
     * Formats JSON.
     */
    fun formatJson(jsonText: String) {
        if (jsonText.isBlank()) {
            _textState.value = TextState.Error("JSON text cannot be empty")
            return
        }

        viewModelScope.launch {
            _textState.value = TextState.Loading
            val formatted = textProvider.formatJson(jsonText)
            if (formatted != null) {
                _textState.value = TextState.JsonFormatted(formatted)
            } else {
                _textState.value = TextState.Error("Invalid JSON format")
            }
        }
    }

    /**
     * Validates JSON.
     */
    fun validateJson(jsonText: String) {
        if (jsonText.isBlank()) {
            _textState.value = TextState.Error("JSON text cannot be empty")
            return
        }

        viewModelScope.launch {
            _textState.value = TextState.Loading
            val isValid = textProvider.validateJson(jsonText)
            _textState.value = if (isValid) {
                TextState.JsonValid("Valid JSON")
            } else {
                TextState.JsonInvalid("Invalid JSON format")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _textState.value = TextState.Idle
    }
}

/**
 * State representation for text operations.
 */
sealed class TextState {
    object Idle : TextState()
    object Loading : TextState()
    data class Converted(val text: String) : TextState()
    data class Counted(val count: WordCharCount) : TextState()
    data class RegexTested(val result: RegexTestResult) : TextState()
    data class JsonFormatted(val json: String) : TextState()
    data class JsonValid(val message: String) : TextState()
    data class JsonInvalid(val message: String) : TextState()
    data class Error(val message: String) : TextState()
}

