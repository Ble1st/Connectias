package com.ble1st.connectias.feature.network.analysis.models

import kotlinx.serialization.Serializable

/**
 * OUI (Organizationally Unique Identifier) entry from the database.
 * Represents a MAC address prefix and its associated manufacturer.
 * 
 * @param prefix The OUI prefix (first 3 octets of MAC address in hex format)
 * @param manufacturer The manufacturer name associated with this OUI prefix
 * @param address Optional human-readable address or location associated with the OUI.
 *                This field may be present for some entries and provides additional
 *                location information about the manufacturer when available.
 */
@Serializable
data class OuiEntry(
    val prefix: String,
    val manufacturer: String,
    val address: String? = null
)
