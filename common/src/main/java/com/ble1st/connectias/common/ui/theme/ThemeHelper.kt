// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.common.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Helper composable to observe theme settings and provide them to ConnectiasTheme.
 * This simplifies theme setup in Fragments.
 * 
 * Uses default values as initial state - Flow will emit actual value immediately.
 * This avoids synchronous SharedPreferences access on main thread (StrictMode violation).
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
    val theme by settingsProvider.observeTheme().collectAsState(initial = "system")
    val themeStyleString by settingsProvider.observeThemeStyle().collectAsState(initial = "standard")
    val dynamicColor by settingsProvider.observeDynamicColor().collectAsState(initial = true)
    val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }
    
    content(theme, themeStyle, dynamicColor)
}

