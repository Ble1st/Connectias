package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Optical drive (DVD/CD) information.
 */
@Parcelize
data class OpticalDrive(
    val device: UsbDevice,
    val mountPoint: String,
    val fileSystem: FileSystem,
    val type: DiscType
) : Parcelable

/**
 * File system type.
 */
enum class FileSystem {
    ISO9660,
    UDF,
    UNKNOWN
}

/**
 * Disc type.
 */
enum class DiscType {
    DATA_DVD,
    VIDEO_DVD,
    AUDIO_DVD,
    DATA_CD,
    AUDIO_CD,
    UNKNOWN
}
