package com.ble1st.connectias.feature.dvd.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * DVD title information.
 * 
 * @property number DVD title index/ID (1-based). Must be greater than 0.
 * @property duration Duration in milliseconds. Must be non-negative (>= 0).
 * @property chapterCount Number of chapters in this title. Used for lazy loading.
 * @property chapters List of chapters for this title (may be empty if not yet loaded).
 *                    This list is immutable after construction.
 * 
 * Note: Chapters are loaded lazily for performance. Use DvdNavigation.navigateToTitle()
 * to load chapters when needed.
 */
@Parcelize
data class DvdTitle(
    val number: Int,
    val duration: Long, // milliseconds
    val chapterCount: Int,
    val audioTracks: List<DvdAudioTrack> = emptyList(),
    val subtitleTracks: List<DvdSubtitleTrack> = emptyList()
) : Parcelable {
    init {
        require(number > 0) { "Title number must be positive, got: $number" }
        require(duration >= 0) { "Duration cannot be negative, got: $duration ms" }
        require(chapterCount >= 0) { "Chapter count cannot be negative, got: $chapterCount" }
    }

}
