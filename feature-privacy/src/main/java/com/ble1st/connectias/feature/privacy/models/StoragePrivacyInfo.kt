package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Storage privacy information including scoped storage status.
 */
@Parcelize
data class StoragePrivacyInfo(
    val scopedStorageEnabled: Boolean,
    val legacyStorageMode: Boolean,
    val appsWithStorageAccess: List<StorageAccess>,
    val mediaStoreAccessEnabled: Boolean
) : Parcelable

/**
 * Information about an app's storage access.
 */
@Parcelize
data class StorageAccess(
    val packageName: String,
    val appName: String,
    val hasReadStorage: Boolean,
    val hasWriteStorage: Boolean,
    val hasMediaAccess: Boolean
) : Parcelable

