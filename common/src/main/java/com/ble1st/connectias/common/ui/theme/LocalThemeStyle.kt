package com.ble1st.connectias.common.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for ThemeStyle.
 * Provides access to the current theme style throughout the composition tree.
 */
val LocalThemeStyle = compositionLocalOf<ThemeStyle> {
    ThemeStyle.Standard // Default fallback
}

