package com.ble1st.connectias.feature.utilities.color

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Color Tools.
 */
@HiltViewModel
class ColorViewModel @Inject constructor(
    private val colorProvider: ColorProvider
) : ViewModel() {

    private val _colorState = MutableStateFlow<ColorState>(ColorState.Idle)
    val colorState: StateFlow<ColorState> = _colorState.asStateFlow()

    /**
     * Converts RGB to HEX.
     */
    fun convertRgbToHex(r: Int, g: Int, b: Int) {
        viewModelScope.launch {
            val hex = colorProvider.rgbToHex(r, g, b)
            _colorState.value = ColorState.HexResult(hex)
        }
    }

    /**
     * Converts HEX to RGB.
     */
    fun convertHexToRgb(hex: String) {
        viewModelScope.launch {
            val rgb = colorProvider.hexToRgb(hex)
            if (rgb != null) {
                _colorState.value = ColorState.RgbResult(rgb)
            } else {
                _colorState.value = ColorState.Error("Invalid HEX color format")
            }
        }
    }

    /**
     * Converts RGB to HSL.
     */
    fun convertRgbToHsl(r: Int, g: Int, b: Int) {
        viewModelScope.launch {
            val hsl = colorProvider.rgbToHsl(r, g, b)
            _colorState.value = ColorState.HslResult(hsl)
        }
    }

    /**
     * Converts HSL to RGB.
     */
    fun convertHslToRgb(h: Int, s: Int, l: Int) {
        viewModelScope.launch {
            val rgb = colorProvider.hslToRgb(h, s, l)
            _colorState.value = ColorState.RgbResult(rgb)
        }
    }

    /**
     * Converts RGB to HSV.
     */
    fun convertRgbToHsv(r: Int, g: Int, b: Int) {
        viewModelScope.launch {
            val hsv = colorProvider.rgbToHsv(r, g, b)
            _colorState.value = ColorState.HsvResult(hsv)
        }
    }

    /**
     * Calculates contrast ratio.
     */
    fun calculateContrast(color1Hex: String, color2Hex: String) {
        viewModelScope.launch {
            val rgb1 = colorProvider.hexToRgb(color1Hex)
            val rgb2 = colorProvider.hexToRgb(color2Hex)
            
            if (rgb1 == null || rgb2 == null) {
                _colorState.value = ColorState.Error("Invalid color format")
                return@launch
            }

            val color1 = Color.rgb(rgb1.r, rgb1.g, rgb1.b)
            val color2 = Color.rgb(rgb2.r, rgb2.g, rgb2.b)
            
            val ratio = colorProvider.calculateContrastRatio(color1, color2)
            val meetsAA = colorProvider.meetsWcagAA(color1, color2)
            val meetsAAA = colorProvider.meetsWcagAAA(color1, color2)
            
            _colorState.value = ColorState.ContrastResult(ratio, meetsAA, meetsAAA)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _colorState.value = ColorState.Idle
    }
}

/**
 * State representation for color operations.
 */
sealed class ColorState {
    object Idle : ColorState()
    data class HexResult(val hex: String) : ColorState()
    data class RgbResult(val rgb: RgbColor) : ColorState()
    data class HslResult(val hsl: HslColor) : ColorState()
    data class HsvResult(val hsv: HsvColor) : ColorState()
    data class ContrastResult(val ratio: Double, val meetsAA: Boolean, val meetsAAA: Boolean) : ColorState()
    data class Error(val message: String) : ColorState()
}

