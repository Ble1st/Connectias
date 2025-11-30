package com.ble1st.connectias.feature.network.analysis.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about a MAC address including OUI lookup results.
 */
@Parcelize
data class MacAddressInfo(
    val macAddress: String,
    val manufacturer: String?,
    val isValid: Boolean,
    val formattedAddress: String
) : Parcelable
