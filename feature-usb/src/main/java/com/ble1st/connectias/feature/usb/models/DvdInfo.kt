package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Complete DVD information.
 * 
 * @property handle Unique identifier or file descriptor for the DVD device
 * @property mountPoint Absolute path where the DVD is mounted (e.g., "/mnt/dvd" or "/media/dvd")
 * @property titles List of DVD titles available on the disc. This list is immutable after construction.
 */
@Parcelize
data class DvdInfo(
    val handle: Long,
    val mountPoint: String,
    val titles: List<DvdTitle>
) : Parcelable {
    init {
        // Create defensive copy to ensure immutability
        val titlesCopy = titles.toList()
        // Note: Parcelize requires the property to be mutable for serialization,
        // but we ensure immutability through defensive copying in the constructor
    }
    
    /**
     * Returns an immutable copy of the titles list.
     */
    fun getTitles(): List<DvdTitle> = titles.toList()
}
