package com.ble1st.connectias.feature.usb.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("dvd_settings", Context.MODE_PRIVATE)
    }
    
    private val KEY_CSS_DECRYPTION_ENABLED = "css_decryption_enabled"
    private val KEY_CSS_DISCLAIMER_ACCEPTED = "css_disclaimer_accepted"
    
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
        prefs.edit()
            .putBoolean(KEY_CSS_DECRYPTION_ENABLED, enabled)
            .putBoolean(KEY_CSS_DISCLAIMER_ACCEPTED, disclaimerAccepted)
            .apply()
        
        if (enabled) {
            Timber.w("CSS decryption enabled - user has accepted legal disclaimer")
        } else {
            Timber.d("CSS decryption disabled")
        }
    }
}
