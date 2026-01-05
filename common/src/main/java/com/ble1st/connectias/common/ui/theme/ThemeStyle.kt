package com.ble1st.connectias.common.ui.theme

/**
 * Represents the visual style theme of the application.
 * Can be combined with dark/light/system preference.
 */
sealed class ThemeStyle {
    object Standard : ThemeStyle()
    object AdeptusMechanicus : ThemeStyle()
    
    companion object {
        /**
         * Converts a string value to ThemeStyle.
         * @param value String representation ("standard" or "adeptus_mechanicus")
         * @return ThemeStyle instance
         */
        fun fromString(value: String): ThemeStyle = when (value) {
            "adeptus_mechanicus" -> AdeptusMechanicus
            else -> Standard
        }
        
        /**
         * Converts ThemeStyle to string representation.
         * @param style ThemeStyle instance
         * @return String representation
         */
        fun toString(style: ThemeStyle): String = when (style) {
            is Standard -> "standard"
            is AdeptusMechanicus -> "adeptus_mechanicus"
        }
    }
}

