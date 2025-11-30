package com.ble1st.connectias.feature.network.analysis.models

import kotlinx.serialization.Serializable

/**
 * OUI (Organizationally Unique Identifier) entry from the database.
 * Represents a MAC address prefix and its associated manufacturer.
 */
@Serializable
data class OuiEntry(
    val prefix: String,
    val manufacturer: String,
    val address: String? = null
)
