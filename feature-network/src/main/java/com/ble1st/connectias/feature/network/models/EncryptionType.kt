package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents Wi‑Fi encryption types with security level.
 */
@Parcelize
sealed class EncryptionType(val securityLevel: SecurityLevel) : Parcelable {
    object Open : EncryptionType(SecurityLevel.NONE)
    
    object WEP : EncryptionType(SecurityLevel.WEAK)
    
    object WPA : EncryptionType(SecurityLevel.MODERATE)
    
    object WPA2 : EncryptionType(SecurityLevel.STRONG)
    
    object WPA3 : EncryptionType(SecurityLevel.STRONGEST)
    
    object WPA_WPA2_MIXED : EncryptionType(SecurityLevel.STRONG)
    
    object WPA2_WPA3_TRANSITION : EncryptionType(SecurityLevel.STRONGEST)
    
    object WPA_ENTERPRISE : EncryptionType(SecurityLevel.STRONG)
    
    object OWE : EncryptionType(SecurityLevel.STRONGEST)
    
    /**
     * User-friendly display name for the encryption type.
     */
    val displayName: String
        get() = when (this) {
            Open -> "Open"
            WEP -> "WEP"
            WPA -> "WPA"
            WPA2 -> "WPA2"
            WPA3 -> "WPA3"
            WPA_WPA2_MIXED -> "WPA/WPA2"
            WPA2_WPA3_TRANSITION -> "WPA2/WPA3"
            WPA_ENTERPRISE -> "WPA Enterprise"
            OWE -> "OWE"
        }
    
    /**
     * Convenience property for backward compatibility.
     * @deprecated Use securityLevel instead
     */
    @Deprecated("Use securityLevel instead", ReplaceWith("securityLevel != SecurityLevel.NONE"))
    val isSecure: Boolean
        get() = securityLevel != SecurityLevel.NONE
}

