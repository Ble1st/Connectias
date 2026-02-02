package com.ble1st.connectias.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    // Separate state for error and success messages (not part of settings flows)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    
    // UI State - Flow-based, automatically updates when settings change
    // Combine settings flows in groups (combine() supports max 5 flows, so we nest them)
    private val settingsState: StateFlow<SettingsUiState> = combine(
        // First group: theme and UI settings
        combine(
            settingsRepository.observeTheme(),
            settingsRepository.observeThemeStyle(),
            settingsRepository.observeDynamicColor()
        ) { theme: String, themeStyle: String, dynamicColor: Boolean ->
            Triple(theme, themeStyle, dynamicColor)
        },
        // Second group: security settings
        combine(
            settingsRepository.observeAutoLockEnabled(),
            settingsRepository.observeRaspLoggingEnabled(),
            settingsRepository.observeLoggingLevel(),
            settingsRepository.observeClipboardAutoClear()
        ) { autoLock: Boolean, raspLogging: Boolean, loggingLevel: String, clipboardClear: Boolean ->
            SettingsUiState(
                theme = "system", // Will be updated
                themeStyle = "standard", // Will be updated
                dynamicColor = true, // Will be updated
                autoLockEnabled = autoLock,
                raspLoggingEnabled = raspLogging,
                loggingLevel = loggingLevel,
                clipboardAutoClear = clipboardClear,
                errorMessage = null,
                successMessage = null
            )
        }
    ) { themeGroup, securityState ->
        val (theme, themeStyle, dynamicColor) = themeGroup
        securityState.copy(
            theme = theme,
            themeStyle = themeStyle,
            dynamicColor = dynamicColor
        )
    }.combine(_errorMessage) { state, error ->
        state.copy(errorMessage = error)
    }.combine(_successMessage) { state, success ->
        state.copy(successMessage = success)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )
    
    val uiState: StateFlow<SettingsUiState> = settingsState

    /**
     * Updates theme preference (light/dark/system).
     */
    fun setTheme(theme: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setTheme(theme)
                // State will automatically update via Flow
            } catch (e: Exception) {
                Timber.e(e, "Failed to set theme")
                _errorMessage.value = "Failed to set theme: ${e.message}"
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
                // State will automatically update via Flow
            } catch (e: Exception) {
                Timber.e(e, "Failed to set theme style")
                _errorMessage.value = "Failed to set theme style: ${e.message}"
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
                // State will automatically update via Flow
            } catch (e: Exception) {
                Timber.e(e, "Failed to set dynamic color")
                _errorMessage.value = "Failed to set dynamic color: ${e.message}"
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
                // State will automatically update via Flow
            } catch (e: Exception) {
                Timber.e(e, "Failed to set auto-lock")
                _errorMessage.value = "Failed to set auto-lock: ${e.message}"
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
                // State will automatically update via Flow
            } catch (e: Exception) {
                Timber.e(e, "Failed to set RASP logging")
                _errorMessage.value = "Failed to set RASP logging: ${e.message}"
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
                // State will automatically update via Flow
            } catch (e: Exception) {
                Timber.e(e, "Failed to set logging level")
                _errorMessage.value = "Failed to set logging level: ${e.message}"
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
                // State will automatically update via Flow
                _successMessage.value = "Settings reset successfully"
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset settings")
                _errorMessage.value = "Failed to reset settings: ${e.message}"
            }
        }
    }

    /**
     * Clears error message.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Clears success message.
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
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

