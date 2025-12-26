package com.ble1st.connectias.common.ui.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ble1st.connectias.common.ui.theme.LocalThemeStyle
import com.ble1st.connectias.common.ui.theme.ThemeStyle

/**
 * Provides theme-dependent strings based on the current theme style.
 * Uses caching for performance optimization.
 */
class ThemeStringProvider(
    private val themeStyle: ThemeStyle,
    private val mappings: StringMappings = StringMappings
) {
    // Cache for translated strings to avoid repeated lookups
    private val cache = mutableMapOf<String, String>()
    
    /**
     * Gets the theme-appropriate string.
     * @param standard The standard string
     * @return The theme-appropriate string (Adeptus Mechanicus variant if theme is AdeptusMechanicus, otherwise original)
     */
    fun getString(standard: String): String {
        return when (themeStyle) {
            is ThemeStyle.AdeptusMechanicus -> {
                // Check cache first
                cache[standard] ?: run {
                    val variant = mappings.getAdeptusMechanicusVariant(standard)
                    cache[standard] = variant
                    variant
                }
            }
            is ThemeStyle.Standard -> standard
        }
    }

}

/**
 * Composable function to get the current ThemeStringProvider.
 * Uses CompositionLocal for theme style.
 */
@Composable
fun getThemeStringProvider(): ThemeStringProvider {
    val themeStyle = LocalThemeStyle.current
    return remember(themeStyle) {
        ThemeStringProvider(themeStyle)
    }
}

/**
 * Composable function to get a theme-appropriate string.
 * Convenience function that combines getThemeStringProvider() and getString().
 */
@Composable
fun getThemedString(standard: String): String {
    val provider = getThemeStringProvider()
    return provider.getString(standard)
}

