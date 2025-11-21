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
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _theme = MutableStateFlow(
        runCatching {
            settingsRepository.getTheme()
        }.getOrElse { e ->
            Timber.e(e, "Failed to get theme during ViewModel initialization")
            "system" // Safe default fallback
        }
    )
    val theme: StateFlow<String> = _theme.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setTheme(theme: String) {
        viewModelScope.launch {
            try {
                // Pessimistic update: only update UI after repository succeeds
                settingsRepository.setTheme(theme)
                _theme.value = theme
                _error.value = null // Clear any previous errors
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Log error and surface to UI
                Timber.e(e, "Failed to set theme: $theme")
                _error.value = "Failed to save theme: ${e.message ?: "Unknown error"}"
                // UI remains unchanged (pessimistic approach)
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
