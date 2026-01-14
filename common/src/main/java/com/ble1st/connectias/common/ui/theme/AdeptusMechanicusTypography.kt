// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.common.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ble1st.connectias.common.R

/**
 * Adeptus Mechanicus Typography
 * Uses Orbitron for headings and buttons, Rajdhani for body text
 * Based on design guidelines from ADEPTUS_MECHANICUS_THEME.md
 */

// Orbitron Font Family
private val OrbitronFontFamily = FontFamily(
    Font(R.font.orbitron_regular, FontWeight.Normal),
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_black, FontWeight.Black)
)

// Rajdhani Font Family
private val RajdhaniFontFamily = FontFamily(
    Font(R.font.rajdhani_light, FontWeight.Light),
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold)
)

/**
 * Adeptus Mechanicus Typography configuration
 * 
 * Typography Rules:
 * - H1: Orbitron, 2.5em, Bold (900), UPPERCASE, Letter-Spacing: 3px
 * - H2: Orbitron, 2em, Bold (900), UPPERCASE, Letter-Spacing: 3px
 * - H3: Orbitron, 1.5em, Bold (700), UPPERCASE, Letter-Spacing: 2px
 * - H4: Orbitron, 1.2em, Bold (700), UPPERCASE, Letter-Spacing: 1px
 * - Body Large: Rajdhani, 1.1em, Regular (400), Normal Case
 * - Body: Rajdhani, 1em, Regular (400), Normal Case
 * - Body Small: Rajdhani, 0.9em, Regular (400), Normal Case
 * - Button Text: Orbitron, 1em, Bold (700), UPPERCASE, Letter-Spacing: 1.5px
 * - Label Text: Orbitron, 0.9em, SemiBold (600), UPPERCASE, Letter-Spacing: 1px
 * - Status Badges: Orbitron, 0.85em, Bold (700), UPPERCASE, Letter-Spacing: 1px
 * - Technical Values: Orbitron, 1.5em, Bold (700), Normal Case
 * - Descriptions: Rajdhani, 0.95em, Regular (400), Italic, Normal Case
 */
val AdeptusMechanicusTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Black, // 900
        fontSize = 40.sp, // 2.5em equivalent
        lineHeight = 48.sp,
        letterSpacing = 3.sp
    ),
    displayMedium = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Black, // 900
        fontSize = 32.sp, // 2em equivalent
        lineHeight = 40.sp,
        letterSpacing = 3.sp
    ),
    displaySmall = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Black, // 900
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 3.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 24.sp, // 1.5em equivalent
        lineHeight = 32.sp,
        letterSpacing = 2.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 19.2.sp, // 1.2em equivalent
        lineHeight = 28.sp,
        letterSpacing = 1.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 14.4.sp, // 0.9em equivalent
        lineHeight = 20.sp,
        letterSpacing = 1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 13.6.sp, // 0.85em equivalent
        lineHeight = 20.sp,
        letterSpacing = 1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 17.6.sp, // 1.1em equivalent
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 16.sp, // 1em equivalent
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = RajdhaniFontFamily,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 14.4.sp, // 0.9em equivalent
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 16.sp, // Button text
        lineHeight = 20.sp,
        letterSpacing = 1.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 14.4.sp, // Label text
        lineHeight = 20.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 13.6.sp, // Status badges
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    )
)

