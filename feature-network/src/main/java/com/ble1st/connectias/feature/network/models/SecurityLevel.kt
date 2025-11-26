package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the security level of Wi‑Fi encryption.
 */
@Parcelize
enum class SecurityLevel : Parcelable {
    NONE,      // No encryption (Open)
    WEAK,      // Weak encryption (WEP)
    MODERATE,  // Moderate encryption (WPA - TKIP/deprecated)
    STRONG,    // Strong encryption (WPA2)
    STRONGEST  // Strongest encryption (WPA3, OWE)
}

