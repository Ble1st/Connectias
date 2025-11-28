package com.ble1st.connectias.feature.utilities.color

import android.graphics.Color
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for color manipulation operations.
 */
@Singleton
class ColorProvider @Inject constructor() {

    /**
     * Converts RGB to HEX.
     */
    suspend fun rgbToHex(r: Int, g: Int, b: Int): String = withContext(Dispatchers.Default) {
        String.format("#%02X%02X%02X", r, g, b)
    }

    /**
     * Converts HEX to RGB.
     */
    suspend fun hexToRgb(hex: String): RgbColor? = withContext(Dispatchers.Default) {
        try {
            val cleanHex = hex.replace("#", "").uppercase()
            if (cleanHex.length != 6) return@withContext null
            
            val r = cleanHex.substring(0, 2).toInt(16)
            val g = cleanHex.substring(2, 4).toInt(16)
            val b = cleanHex.substring(4, 6).toInt(16)
            
            RgbColor(r, g, b)
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert HEX to RGB: $hex")
            null
        }
    }

    /**
     * Converts RGB to HSL.
     */
    suspend fun rgbToHsl(r: Int, g: Int, b: Int): HslColor = withContext(Dispatchers.Default) {
        val rNorm = r / 255.0
        val gNorm = g / 255.0
        val bNorm = b / 255.0

        val max = maxOf(rNorm, gNorm, bNorm)
        val min = minOf(rNorm, gNorm, bNorm)
        val delta = max - min

        var h = 0.0
        val l = (max + min) / 2.0
        var s = 0.0

        if (delta != 0.0) {
            s = if (l < 0.5) {
                delta / (max + min)
            } else {
                delta / (2.0 - max - min)
            }

            when (max) {
                rNorm -> h = ((gNorm - bNorm) / delta + (if (gNorm < bNorm) 6 else 0)) / 6.0
                gNorm -> h = ((bNorm - rNorm) / delta + 2) / 6.0
                bNorm -> h = ((rNorm - gNorm) / delta + 4) / 6.0
            }
        }

        HslColor(
            h = (h * 360).toInt(),
            s = (s * 100).toInt(),
            l = (l * 100).toInt()
        )
    }

    /**
     * Converts HSL to RGB.
     */
    suspend fun hslToRgb(h: Int, s: Int, l: Int): RgbColor = withContext(Dispatchers.Default) {
        val hNorm = h / 360.0
        val sNorm = s / 100.0
        val lNorm = l / 100.0

        val c = (1 - kotlin.math.abs(2 * lNorm - 1)) * sNorm
        val x = c * (1 - kotlin.math.abs((hNorm * 6) % 2 - 1))
        val m = lNorm - c / 2

        val (r1, g1, b1) = when {
            hNorm < 1.0 / 6 -> Triple(c, x, 0.0)
            hNorm < 2.0 / 6 -> Triple(x, c, 0.0)
            hNorm < 3.0 / 6 -> Triple(0.0, c, x)
            hNorm < 4.0 / 6 -> Triple(0.0, x, c)
            hNorm < 5.0 / 6 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }

        RgbColor(
            r = ((r1 + m) * 255).toInt().coerceIn(0, 255),
            g = ((g1 + m) * 255).toInt().coerceIn(0, 255),
            b = ((b1 + m) * 255).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Converts RGB to HSV.
     */
    suspend fun rgbToHsv(r: Int, g: Int, b: Int): HsvColor = withContext(Dispatchers.Default) {
        val rNorm = r / 255.0
        val gNorm = g / 255.0
        val bNorm = b / 255.0

        val max = maxOf(rNorm, gNorm, bNorm)
        val min = minOf(rNorm, gNorm, bNorm)
        val delta = max - min

        var h = 0.0
        val v = max
        val s = if (max == 0.0) 0.0 else delta / max

        if (delta != 0.0) {
            when (max) {
                rNorm -> h = ((gNorm - bNorm) / delta + (if (gNorm < bNorm) 6 else 0)) / 6.0
                gNorm -> h = ((bNorm - rNorm) / delta + 2) / 6.0
                bNorm -> h = ((rNorm - gNorm) / delta + 4) / 6.0
            }
        }

        HsvColor(
            h = (h * 360).toInt(),
            s = (s * 100).toInt(),
            v = (v * 100).toInt()
        )
    }

    /**
     * Calculates contrast ratio between two colors (for accessibility).
     * Returns ratio between 1 and 21.
     */
    suspend fun calculateContrastRatio(color1: Int, color2: Int): Double = withContext(Dispatchers.Default) {
        val luminance1 = calculateLuminance(color1)
        val luminance2 = calculateLuminance(color2)
        
        val lighter = maxOf(luminance1, luminance2)
        val darker = minOf(luminance1, luminance2)
        
        (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Calculates relative luminance of a color (for accessibility).
     */
    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0

        val rLinear = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gLinear = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bLinear = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
    }

    /**
     * Checks if contrast ratio meets WCAG AA standards (4.5:1 for normal text).
     */
    suspend fun meetsWcagAA(color1: Int, color2: Int): Boolean {
        val ratio = calculateContrastRatio(color1, color2)
        return ratio >= 4.5
    }

    /**
     * Checks if contrast ratio meets WCAG AAA standards (7:1 for normal text).
     */
    suspend fun meetsWcagAAA(color1: Int, color2: Int): Boolean {
        val ratio = calculateContrastRatio(color1, color2)
        return ratio >= 7.0
    }
}

/**
 * RGB color representation.
 */
data class RgbColor(val r: Int, val g: Int, val b: Int)

/**
 * HSL color representation.
 */
data class HslColor(val h: Int, val s: Int, val l: Int)

/**
 * HSV color representation.
 */
data class HsvColor(val h: Int, val s: Int, val v: Int)

