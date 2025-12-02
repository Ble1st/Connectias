package com.ble1st.connectias.common.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Edge-to-Edge utilities for consistent inset handling across the app.
 * 
 * These utilities help screens properly handle system UI insets (status bar,
 * navigation bar, display cutouts) for a fullscreen, immersive experience.
 * 
 * Usage examples:
 * - Use statusBarPadding() for content that should not overlap with status bar
 * - Use navigationBarPadding() for content that should not overlap with navigation bar
 * - Use systemBarsPadding() for content that should not overlap with either
 */
object EdgeToEdge {

    /**
     * Get padding values for the status bar.
     * Use this for content that should start below the status bar.
     */
    @Composable
    fun statusBarPadding(): PaddingValues = WindowInsets.statusBars.asPaddingValues()

    /**
     * Get padding values for the navigation bar.
     * Use this for content that should not be hidden by the navigation bar.
     */
    @Composable
    fun navigationBarPadding(): PaddingValues = WindowInsets.navigationBars.asPaddingValues()

    /**
     * Get padding values for all system bars (status + navigation).
     * Use this for content that should have safe area on all edges.
     */
    @Composable
    fun systemBarsPadding(): PaddingValues = WindowInsets.systemBars.asPaddingValues()

    /**
     * Get padding values for display cutout (notch, punch-hole camera).
     * Use this for content that should avoid display cutouts.
     */
    @Composable
    fun displayCutoutPadding(): PaddingValues = WindowInsets.displayCutout.asPaddingValues()

    /**
     * Get padding values for the IME (keyboard).
     * Use this for content that should move above the keyboard.
     */
    @Composable
    fun imePadding(): PaddingValues = WindowInsets.ime.asPaddingValues()

    /**
     * Get the height of the status bar.
     */
    @Composable
    fun statusBarHeight(): Dp {
        val padding = statusBarPadding()
        return padding.calculateTopPadding()
    }

    /**
     * Get the height of the navigation bar.
     */
    @Composable
    fun navigationBarHeight(): Dp {
        val padding = navigationBarPadding()
        return padding.calculateBottomPadding()
    }
}

/**
 * WindowInsets extension for easy access to common insets.
 */
object Insets {
    /** Status bar insets */
    val statusBars: WindowInsets
        @Composable get() = WindowInsets.statusBars
    
    /** Navigation bar insets */
    val navigationBars: WindowInsets
        @Composable get() = WindowInsets.navigationBars
    
    /** System bars (status + navigation) insets */
    val systemBars: WindowInsets
        @Composable get() = WindowInsets.systemBars
    
    /** Display cutout insets */
    val displayCutout: WindowInsets
        @Composable get() = WindowInsets.displayCutout
    
    /** IME (keyboard) insets */
    val ime: WindowInsets
        @Composable get() = WindowInsets.ime
}

