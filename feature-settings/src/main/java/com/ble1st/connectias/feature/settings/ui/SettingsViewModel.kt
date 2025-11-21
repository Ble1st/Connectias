package com.ble1st.connectias.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _theme = MutableStateFlow<String>("system") // Safe default
    val theme: StateFlow<String> = _theme.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Async initialization: load theme and update error state on failure
        viewModelScope.launch {
            try {
                val loadedTheme = settingsRepository.getTheme()
                _theme.value = loadedTheme
                _error.value = null // Clear any previous errors
            } catch (e: Exception) {
                // Log technical details internally
                Timber.e(e, "Failed to get theme during ViewModel initialization")
                // Surface user-friendly error message (no technical details)
                _error.value = mapExceptionToUserMessage(e)
                // Theme remains at safe default "system"
            }
        }
    }
    
    /**
     * Maps exceptions to user-friendly error messages without exposing technical details.
     */
    private fun mapExceptionToUserMessage(e: Exception): String {
        return when (e) {
            is IOException -> "Unable to save settings. Please check your connection and try again."
            is IllegalStateException -> "Settings could not be saved. Please try again."
            is SecurityException -> "Permission denied. Please check app permissions."
            else -> "Unable to save settings. Please try again."
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            try {
                // Pessimistic update: only update UI after repository succeeds
                settingsRepository.setTheme(theme)
                _theme.value = theme
                _error.value = null // Clear any previous errors
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Log technical details internally
                Timber.e(e, "Failed to set theme: $theme")
                // Surface user-friendly error message (no technical details)
                _error.value = mapExceptionToUserMessage(e)
                // UI remains unchanged (pessimistic approach)
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
