package com.ble1st.connectias.feature.utilities.regex

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for Regex Tester screen.
 */
@HiltViewModel
class RegexTesterViewModel @Inject constructor(
    private val regexTesterProvider: RegexTesterProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegexTesterUiState())
    val uiState: StateFlow<RegexTesterUiState> = _uiState.asStateFlow()

    /**
     * Tests a regex pattern.
     */
    fun testPattern(pattern: String, input: String, flags: Set<RegexOption> = emptySet()) {
        val result = regexTesterProvider.testPattern(pattern, input, flags)
        _uiState.update { it.copy(
            pattern = pattern,
            input = input,
            flags = flags,
            result = result
        ) }
    }

    /**
     * Replaces matches in the input.
     */
    fun replace(pattern: String, input: String, replacement: String): String {
        return regexTesterProvider.replace(pattern, input, replacement)
    }

    /**
     * Gets pattern explanation.
     */
    fun explainPattern(pattern: String): PatternExplanation {
        return regexTesterProvider.explainPattern(pattern)
    }

    /**
     * Gets common patterns.
     */
    fun getCommonPatterns(): List<CommonPattern> = regexTesterProvider.getCommonPatterns()

    /**
     * Clears the current state.
     */
    fun clear() {
        _uiState.update { RegexTesterUiState() }
    }
}

/**
 * UI state for Regex Tester screen.
 */
data class RegexTesterUiState(
    val pattern: String = "",
    val input: String = "",
    val flags: Set<RegexOption> = emptySet(),
    val result: RegexResult? = null
)

