package com.ble1st.connectias.feature.usb.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings for DVD features.
 * 
 * CSS decryption is always enabled - libdvdcss is mandatory for DVD playback.
 */
@Singleton
class DvdSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // CSS decryption is always enabled
    private val _cssDecryptionEnabled = MutableStateFlow(true)
    
    init {
        Timber.d("DvdSettings initialized - CSS decryption always enabled")
    }
    
    /**
     * Observable StateFlow for CSS decryption enabled state.
     * Always returns true as CSS is mandatory for DVD playback.
     */
    val cssDecryptionEnabled: StateFlow<Boolean> = _cssDecryptionEnabled.asStateFlow()
    
    /**
     * Checks if CSS decryption is enabled.
     * Always returns true as CSS is mandatory for DVD playback.
     */
    fun isCssDecryptionEnabled(): Boolean = true
}
