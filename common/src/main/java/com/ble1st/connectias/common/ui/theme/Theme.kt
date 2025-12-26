package com.ble1st.connectias.common.ui.theme

import android.app.Activity
import android.content.Context
import android.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ble1st.connectias.common.ui.strings.AdeptusDictionary
import com.ble1st.connectias.common.ui.strings.LocalAppStrings
import com.ble1st.connectias.common.ui.strings.StandardDictionary

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
    primary = DarkBoldBlue,
    onPrimary = OnDarkBoldBlue,
    primaryContainer = DarkBoldBlueContainer,
    onPrimaryContainer = OnDarkBoldBlueContainer,
    secondary = DarkExpressiveAccent,
    onSecondary = OnDarkExpressiveAccent,
    secondaryContainer = DarkExpressiveAccentContainer,
    onSecondaryContainer = OnDarkExpressiveAccentContainer,
    tertiary = DarkBoldTertiary,
    onTertiary = OnDarkBoldTertiary,
    tertiaryContainer = DarkBoldTertiaryContainer,
    onTertiaryContainer = OnDarkBoldTertiaryContainer,
    error = DarkExpressiveError,
    errorContainer = DarkExpressiveErrorContainer,
    onError = OnDarkExpressiveError,
    onErrorContainer = OnDarkExpressiveErrorContainer,
    background = DarkExpressiveBackground,
    onBackground = OnDarkExpressiveBackground,
    surface = DarkExpressiveSurface,
    onSurface = OnDarkExpressiveSurface,
    surfaceVariant = DarkSurfaceContainerHigh, // M3 Mapping approximation
    onSurfaceVariant = OnDarkExpressiveSurface, // Approximation
    outline = DarkSurfaceContainerHighest
)

private val LightColorScheme = lightColorScheme(
    primary = BoldBlue,
    onPrimary = OnBoldBlue,
    primaryContainer = BoldBlueContainer,
    onPrimaryContainer = OnBoldBlueContainer,
    secondary = ExpressiveAccent,
    onSecondary = OnExpressiveAccent,
    secondaryContainer = ExpressiveAccentContainer,
    onSecondaryContainer = OnExpressiveAccentContainer,
    tertiary = BoldTertiary,
    onTertiary = OnBoldTertiary,
    tertiaryContainer = BoldTertiaryContainer,
    onTertiaryContainer = OnBoldTertiaryContainer,
    error = ExpressiveError,
    errorContainer = ExpressiveErrorContainer,
    onError = OnExpressiveError,
    onErrorContainer = OnExpressiveErrorContainer,
    background = ExpressiveBackground,
    onBackground = OnExpressiveBackground,
    surface = ExpressiveSurface,
    onSurface = OnExpressiveSurface,
    surfaceVariant = SurfaceContainerHigh, // M3 Mapping approximation
    onSurfaceVariant = OnExpressiveSurface, // Approximation
    outline = SurfaceContainerHighest
)

@Composable
@Suppress("DEPRECATION")
fun ConnectiasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themePreference: String? = null,
    themeStyle: ThemeStyle = ThemeStyle.Standard,
    content: @Composable () -> Unit
) {
    val actualDarkTheme = when (themePreference) {
        "light" -> false
        "dark" -> true
        "system", null -> darkTheme 
        else -> darkTheme
    }
    
    val actualDynamicColor = dynamicColor && themeStyle is ThemeStyle.Standard
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            activity?.window?.let { window ->
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                
                val insetsController = WindowCompat.getInsetsController(window, view)
                // For Adeptus Mechanicus, we generally want light status bars (dark icons) only if the background is light,
                // but since Adeptus is dark-themed, we usually want light content (isAppearanceLightStatusBars = false).
                val isLightStatusBars = !actualDarkTheme && themeStyle is ThemeStyle.Standard
                
                insetsController.isAppearanceLightStatusBars = isLightStatusBars
                insetsController.isAppearanceLightNavigationBars = isLightStatusBars
            }
        }
    }

    AnimatedContent(
        targetState = themeStyle,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "ThemeTransition"
    ) { currentThemeStyle ->
        val animatedColorScheme: ColorScheme = when {
            actualDynamicColor && currentThemeStyle is ThemeStyle.Standard -> {
                val context = LocalContext.current
                if (actualDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            currentThemeStyle is ThemeStyle.AdeptusMechanicus -> {
                // Force dark-ish scheme for Adeptus Mechanicus regardless of system setting,
                // but respect high-contrast/light mode slightly if implemented in AdeptusMechanicusLightColorScheme
                if (actualDarkTheme) AdeptusMechanicusDarkColorScheme else AdeptusMechanicusLightColorScheme
            }
            actualDarkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

        val animatedTypography: Typography = when (currentThemeStyle) {
            is ThemeStyle.AdeptusMechanicus -> AdeptusMechanicusTypography
            is ThemeStyle.Standard -> Typography
        }

        val animatedDictionary = when (currentThemeStyle) {
            is ThemeStyle.AdeptusMechanicus -> AdeptusDictionary
            is ThemeStyle.Standard -> StandardDictionary
        }

        CompositionLocalProvider(
            LocalThemeStyle provides currentThemeStyle,
            LocalAppStrings provides animatedDictionary
        ) {
            MaterialTheme(
                colorScheme = animatedColorScheme,
                typography = animatedTypography,
                shapes = Shapes,
                content = content
            )
        }
    }
}