package com.ble1st.connectias.feature.dvd.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Optical drive (DVD/CD) information.
 */
@Parcelize
data class OpticalDrive(
    val device: UsbDevice,
    val mountPoint: String?,
    val devicePath: String?,
    val fileSystem: FileSystem,
    val type: DiscType
) : Parcelable

/**
 * File system type.
 */
enum class FileSystem {
    ISO9660,
    UDF,
    UNKNOWN;
    
    /**
     * Returns a user-friendly display name for the file system.
     */
    val displayName: String
        get() = when (this) {
            ISO9660 -> "ISO9660"
            UDF -> "UDF"
            UNKNOWN -> "Unknown"
        }
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
    UNKNOWN;
    
    /**
     * Returns a user-friendly display name for the disc type.
     */
    val displayName: String
        get() = when (this) {
            DATA_DVD -> "DVD-ROM"
            VIDEO_DVD -> "DVD-Video"
            AUDIO_DVD -> "DVD-Audio"
            DATA_CD -> "CD-ROM"
            AUDIO_CD -> "Audio CD"
            UNKNOWN -> "Unknown"
        }
}
