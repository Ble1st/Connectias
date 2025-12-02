package com.ble1st.connectias.feature.dvd.models

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
) : Parcelable {
    init {
        require(number > 0) { "Chapter number must be positive" }
        require(titleNumber > 0) { "Title number must be positive" }
        require(startTime >= 0) { "Start time cannot be negative" }
        require(duration > 0) { "Duration must be positive" }
    }
}
