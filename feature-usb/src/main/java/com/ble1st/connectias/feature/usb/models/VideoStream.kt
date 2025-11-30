package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Video stream information.
 */
@Parcelize
data class VideoStream(
    val codec: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val frameRate: Double,
    val uri: String? // URI to video stream
) : Parcelable
