package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * DVD chapter information.
 */
@Parcelize
data class DvdChapter(
    val number: Int,
    val titleNumber: Int,
    val startTime: Long, // milliseconds
    val duration: Long // milliseconds
) : Parcelable
