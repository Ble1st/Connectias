package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Audio track information from an Audio CD.
 */
@Parcelize
data class AudioTrack(
    val number: Int,
    val title: String?,
    val duration: Long, // milliseconds
    val startSector: Long,
    val endSector: Long
) : Parcelable
