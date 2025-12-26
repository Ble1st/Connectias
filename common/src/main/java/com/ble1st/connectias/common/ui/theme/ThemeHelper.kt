package com.ble1st.connectias.common.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Helper composable to observe theme settings and provide them to ConnectiasTheme.
 * This simplifies theme setup in Fragments.
 */
@Composable
fun ObserveThemeSettings(
    settingsProvider: ThemeSettingsProvider,
    content: @Composable (
        themePreference: String,
        themeStyle: ThemeStyle,
        dynamicColor: Boolean
    ) -> Unit
) {
    val theme by settingsProvider.observeTheme().collectAsState(initial = settingsProvider.getTheme())
    val themeStyleString by settingsProvider.observeThemeStyle().collectAsState(initial = settingsProvider.getThemeStyle())
    val dynamicColor by settingsProvider.observeDynamicColor().collectAsState(initial = settingsProvider.getDynamicColor())
    val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }
    
    content(theme, themeStyle, dynamicColor)
}

