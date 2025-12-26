package com.ble1st.connectias.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Settings screen.
 * Manages all application settings state and provides methods to update them.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * Loads all settings from repository and updates UI state.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                _uiState.update { currentState ->
                    currentState.copy(
                        theme = settingsRepository.getTheme(),
                        themeStyle = settingsRepository.getThemeStyle(),
                        dynamicColor = settingsRepository.getDynamicColor(),
                        autoLockEnabled = settingsRepository.getAutoLockEnabled(),
                        raspLoggingEnabled = settingsRepository.getRaspLoggingEnabled(),
                        loggingLevel = settingsRepository.getLoggingLevel(),
                        clipboardAutoClear = settingsRepository.getClipboardAutoClear()
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load settings")
                _uiState.update { it.copy(errorMessage = "Failed to load settings: ${e.message}") }
            }
        }
    }

    /**
     * Updates theme preference (light/dark/system).
     */
    fun setTheme(theme: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setTheme(theme)
                _uiState.update { it.copy(theme = theme) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set theme")
                _uiState.update { it.copy(errorMessage = "Failed to set theme: ${e.message}") }
            }
        }
    }

    /**
     * Updates theme style preference (standard/adeptus_mechanicus).
     */
    fun setThemeStyle(themeStyle: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setThemeStyle(themeStyle)
                _uiState.update { it.copy(themeStyle = themeStyle) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set theme style")
                _uiState.update { it.copy(errorMessage = "Failed to set theme style: ${e.message}") }
            }
        }
    }

    /**
     * Updates dynamic color preference.
     */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setDynamicColor(enabled)
                _uiState.update { it.copy(dynamicColor = enabled) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set dynamic color")
                _uiState.update { it.copy(errorMessage = "Failed to set dynamic color: ${e.message}") }
            }
        }
    }

    /**
     * Updates auto-lock preference.
     */
    fun setAutoLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setAutoLockEnabled(enabled)
                _uiState.update { it.copy(autoLockEnabled = enabled) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set auto-lock")
                _uiState.update { it.copy(errorMessage = "Failed to set auto-lock: ${e.message}") }
            }
        }
    }

    /**
     * Updates RASP logging preference.
     */
    fun setRaspLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setRaspLoggingEnabled(enabled)
                _uiState.update { it.copy(raspLoggingEnabled = enabled) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set RASP logging")
                _uiState.update { it.copy(errorMessage = "Failed to set RASP logging: ${e.message}") }
            }
        }
    }

    /**
     * Updates logging level preference.
     */
    fun setLoggingLevel(level: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setLoggingLevel(level)
                _uiState.update { it.copy(loggingLevel = level) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set logging level")
                _uiState.update { it.copy(errorMessage = "Failed to set logging level: ${e.message}") }
            }
        }
    }

    /**
     * Resets all settings to defaults.
     */
    fun resetAllSettings(resetPlainSettings: Boolean = true, resetEncryptedSettings: Boolean = true) {
        viewModelScope.launch {
            try {
                settingsRepository.resetAllSettings(resetPlainSettings, resetEncryptedSettings)
                loadSettings() // Reload settings after reset
                _uiState.update { it.copy(successMessage = "Settings reset successfully") }
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset settings")
                _uiState.update { it.copy(errorMessage = "Failed to reset settings: ${e.message}") }
            }
        }
    }

    /**
     * Clears error message.
     */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clears success message.
     */
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

/**
 * UI State for Settings screen.
 */
data class SettingsUiState(
    val theme: String = "system", // light, dark, system
    val themeStyle: String = "standard", // standard, adeptus_mechanicus
    val dynamicColor: Boolean = true,
    val autoLockEnabled: Boolean = false,
    val raspLoggingEnabled: Boolean = true,
    val loggingLevel: String = "INFO",
    val clipboardAutoClear: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

