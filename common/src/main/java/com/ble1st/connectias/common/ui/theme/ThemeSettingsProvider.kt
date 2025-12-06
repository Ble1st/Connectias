package com.ble1st.connectias.common.ui.theme

import kotlinx.coroutines.flow.Flow

/**
 * Interface for providing theme settings.
 * This allows common module to work with theme settings without depending on core module.
 */
interface ThemeSettingsProvider {
    fun getTheme(): String
    fun observeTheme(): Flow<String>
    fun getThemeStyle(): String
    fun observeThemeStyle(): Flow<String>
    fun getDynamicColor(): Boolean
    fun observeDynamicColor(): Flow<Boolean>
}

