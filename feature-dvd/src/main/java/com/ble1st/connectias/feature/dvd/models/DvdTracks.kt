package com.ble1st.connectias.feature.dvd.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DvdAudioTrack(
    val streamId: Int,
    val language: String?,
    val codec: String,
    val channels: Int,
    val sampleRate: Int
) : Parcelable

@Parcelize
data class DvdSubtitleTrack(
    val streamId: Int,
    val language: String?,
    val type: String
) : Parcelable
