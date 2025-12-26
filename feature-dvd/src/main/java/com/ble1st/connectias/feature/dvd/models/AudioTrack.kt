package com.ble1st.connectias.feature.dvd.models

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
) : Parcelable {
    init {
        require(number > 0) { "Track number must be positive" }
        require(duration >= 0) { "Duration cannot be negative" }
        require(startSector >= 0) { "Start sector cannot be negative" }
        require(endSector > startSector) { "End sector must be greater than start sector" }
    }
}
