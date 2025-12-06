package com.ble1st.connectias.common.ui.strings

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for ThemeStringProvider.
 * Provides access to the current ThemeStringProvider throughout the composition tree.
 * 
 * Note: This is optional - you can also use getThemeStringProvider() or getThemedString() directly.
 */
val LocalThemeStringProvider = compositionLocalOf<ThemeStringProvider> {
    // Default fallback - will use Standard theme
    ThemeStringProvider(com.ble1st.connectias.common.ui.theme.ThemeStyle.Standard)
}

