// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Plugin UI theme wrapper.
 *
 * Goal: When the system is in dark theme, make plugin UI text white by default.
 * We do this by overriding the relevant "on*" colors in the current Material color scheme.
 *
 * Note: We intentionally do NOT override LocalContentColor globally, so Button/Chip content colors
 * still behave correctly via Material components.
 */
@Composable
fun ConnectiasPluginUiTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    val adjusted = if (isDark) {
        base.copy(
            onSurface = Color.White,
            onSurfaceVariant = Color.White,
            onBackground = Color.White
        )
    } else {
        base
    }

    MaterialTheme(
        colorScheme = adjusted,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = {
            // Many plugin UI Text() calls don't specify a color and are not wrapped in Surface().
            // Force the default content color to white in dark mode so ALL texts become readable.
            if (isDark) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    content()
                }
            } else {
                content()
            }
        }
    )
}

