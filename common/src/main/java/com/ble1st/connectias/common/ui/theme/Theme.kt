package com.ble1st.connectias.common.ui.theme

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Finds the Activity from a Context, handling FragmentContextWrapper.
 */
private fun Context.findActivity(): Activity? {
    var context = this
    var depth = 0
    while (context is android.content.ContextWrapper && depth < 100) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
        depth++
    }
    return null
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun ConnectiasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    // Optional theme preference from settings ("light", "dark", "system", or null)
    themePreference: String? = null,
    content: @Composable () -> Unit
) {
    // Determine dark theme: use themePreference if available, otherwise use parameter or system default
    val actualDarkTheme = when (themePreference) {
        "light" -> false
        "dark" -> true
        "system", null -> darkTheme // Use parameter (which defaults to system)
        else -> darkTheme
    }
    
    // Use provided dynamicColor parameter
    val actualDynamicColor = dynamicColor
    
    val colorScheme = when {
        actualDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (actualDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        actualDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            activity?.window?.let { window ->
                // Edge-to-Edge: Keep system bars transparent for fullscreen experience
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                
                // Set status bar icons based on theme (light icons for dark theme, dark icons for light)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !actualDarkTheme
                insetsController.isAppearanceLightNavigationBars = !actualDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
