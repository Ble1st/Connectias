package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * DVD title information.
 */
@Parcelize
data class DvdTitle(
    val number: Int,
    val chapterCount: Int,
    val duration: Long, // milliseconds
    val chapters: List<DvdChapter>
) : Parcelable
