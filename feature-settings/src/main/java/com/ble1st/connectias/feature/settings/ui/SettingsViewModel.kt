package com.ble1st.connectias.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _theme = MutableStateFlow(settingsRepository.getTheme())
    val theme: StateFlow<String> = _theme.asStateFlow()

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
            _theme.value = theme
        }
    }
}

