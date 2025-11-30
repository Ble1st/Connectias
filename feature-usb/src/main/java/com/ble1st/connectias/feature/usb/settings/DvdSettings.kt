package com.ble1st.connectias.feature.usb.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings for DVD features, including CSS decryption.
 */
@Singleton
class DvdSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEY_CSS_DECRYPTION_ENABLED = "css_decryption_enabled"
        private const val KEY_CSS_DISCLAIMER_ACCEPTED = "css_disclaimer_accepted"
    }
    
    private val _cssDecryptionEnabled = MutableStateFlow(false)
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("dvd_settings", Context.MODE_PRIVATE).also {
            // Initialize StateFlow with current value
            _cssDecryptionEnabled.value = it.getBoolean(KEY_CSS_DECRYPTION_ENABLED, false)
            
            // Listen for SharedPreferences changes and update StateFlow
            it.registerOnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_CSS_DECRYPTION_ENABLED) {
                    _cssDecryptionEnabled.value = it.getBoolean(KEY_CSS_DECRYPTION_ENABLED, false)
                }
            }
        }
    }
    
    /**
     * Observable StateFlow for CSS decryption enabled state.
     * Use this in Compose with collectAsState() for automatic recomposition.
     */
    val cssDecryptionEnabled: StateFlow<Boolean> = _cssDecryptionEnabled.asStateFlow()
    
    /**
     * Checks if CSS decryption is enabled.
     */
    fun isCssDecryptionEnabled(): Boolean {
        return prefs.getBoolean(KEY_CSS_DECRYPTION_ENABLED, false)
    }
    
    /**
     * Checks if the CSS decryption disclaimer has been accepted.
     */
    fun isDisclaimerAccepted(): Boolean {
        return prefs.getBoolean(KEY_CSS_DISCLAIMER_ACCEPTED, false)
    }
    
    /**
     * Sets CSS decryption enabled state and disclaimer acceptance.
     */
    fun setCssDecryptionEnabled(enabled: Boolean, disclaimerAccepted: Boolean) {
        Timber.i("Setting CSS decryption: enabled=$enabled, disclaimerAccepted=$disclaimerAccepted")
        
        // Enforce that CSS decryption cannot be enabled unless disclaimer is accepted
        if (enabled && !disclaimerAccepted) {
            Timber.e("Attempted to enable CSS decryption without accepting disclaimer - refusing to persist")
            throw IllegalStateException("CSS decryption cannot be enabled without accepting the disclaimer")
        }
        
        prefs.edit()
            .putBoolean(KEY_CSS_DECRYPTION_ENABLED, enabled)
            .putBoolean(KEY_CSS_DISCLAIMER_ACCEPTED, disclaimerAccepted)
            .apply()
        
        // StateFlow will be updated automatically via the SharedPreferences listener
        
        if (enabled) {
            Timber.w("CSS decryption enabled - user has accepted legal disclaimer")
        } else {
            Timber.d("CSS decryption disabled")
        }
    }
}
