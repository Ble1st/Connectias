package com.ble1st.connectias.feature.dvd.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Complete DVD information.
 * 
 * @property handle Unique identifier or file descriptor for the DVD device (only valid while open)
 * @property mountPoint Absolute path where the DVD is mounted (Legacy, can be empty)
 * @property deviceId USB Device ID (for direct access)
 * @property titles List of DVD titles available on the disc. This list is immutable after construction.
 * @property name DVD name/title (may be null if not available)
 */
@Parcelize
data class DvdInfo(
    val handle: Long,
    val mountPoint: String,
    val deviceId: Int,
    val titles: List<DvdTitle>,
    val name: String? = null
) : Parcelable