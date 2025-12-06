package com.ble1st.connectias.common.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// No specific imports needed for individual colors here, as they are in the same package (Color.kt)
// and thus directly accessible.

val AdeptusMechanicusDarkColorScheme = darkColorScheme(
    primary = MarsRed,
    onPrimary = OldParchment,
    secondary = Bronze,
    onSecondary = DeepSpaceBlack,
    background = DeepSpaceBlack,
    onBackground = OldParchment,
    surface = DarkMarsRed,
    onSurface = OldParchment,
    error = CriticalRed,
    onError = OldParchment,
    outline = Bronze
)

val AdeptusMechanicusLightColorScheme = lightColorScheme(
    // For now, map light scheme to dark/industrial look as well,
    // as Adeptus Mechanicus theme is inherently dark/gothic.
    primary = MarsRed,
    onPrimary = OldParchment,
    secondary = Bronze,
    onSecondary = DeepSpaceBlack,
    background = Color(0xFF1A1A1A),
    onBackground = OldParchment,
    surface = Color(0xFF2A1A1A),
    onSurface = OldParchment,
    error = CriticalRed,
    onError = OldParchment,
    outline = Bronze
)