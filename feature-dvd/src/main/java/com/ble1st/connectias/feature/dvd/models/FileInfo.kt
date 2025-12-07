package com.ble1st.connectias.feature.dvd.models

/**
 * File information for directory listings.
 */
data class FileInfo(
    val name: String,
    val path: String = "",
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long = 0L
)

