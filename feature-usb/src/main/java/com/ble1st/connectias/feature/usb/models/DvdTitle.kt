package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * DVD title information.
 * 
 * @property number DVD title index/ID (1-based). Must be greater than 0.
 * @property duration Duration in milliseconds. Must be non-negative (>= 0).
 * @property chapters List of chapters for this title. The size of this list represents the chapter count.
 *                    This list is immutable after construction.
 * 
 * Note: chapterCount is now a derived property computed from chapters.size rather than a constructor parameter.
 */
@Parcelize
data class DvdTitle(
    val number: Int,
    val duration: Long, // milliseconds
    val chapters: List<DvdChapter>
) : Parcelable {
    init {
        require(number > 0) { "Title number must be positive, got: $number" }
        require(duration >= 0) { "Duration cannot be negative, got: $duration ms" }
    }
    
    /**
     * Returns the number of chapters (derived from chapters.size).
     */
    val chapterCount: Int
        get() = chapters.size
    

}
