package com.ble1st.connectias.feature.security.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Overall privacy status aggregating all privacy categories.
 */
@Parcelize
data class PrivacyStatus(
    val networkPrivacy: PrivacyLevel,
    val sensorPrivacy: PrivacyLevel,
    val locationPrivacy: PrivacyLevel,
    val permissionsPrivacy: PrivacyLevel,
    val backgroundPrivacy: PrivacyLevel,
    val storagePrivacy: PrivacyLevel,
    val overallLevel: PrivacyLevel
) : Parcelable

/**
 * Privacy categories for different aspects of device privacy.
 */
enum class PrivacyCategory {
    NETWORK,
    SENSORS,
    LOCATION,
    PERMISSIONS,
    BACKGROUND,
    STORAGE
}

/**
 * Privacy level indicating the security status of a privacy category.
 */
enum class PrivacyLevel {
    SECURE,    // No privacy concerns detected
    WARNING,   // Some privacy concerns, but not critical
    CRITICAL,  // Critical privacy issues detected
    UNKNOWN    // Unable to determine privacy status
}

