package com.ble1st.connectias.feature.scanner.data

import android.graphics.Bitmap

enum class ScanSource {
    FLATBED,
    ADF
}

data class ScannedDocument(
    val pages: List<Bitmap>,
    val name: String,
    val timestamp: Long
)

data class ScannerDevice(
    val name: String,
    val address: String,
    val isMopriaCompliant: Boolean
)
