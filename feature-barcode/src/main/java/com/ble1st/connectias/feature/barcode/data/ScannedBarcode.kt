package com.ble1st.connectias.feature.barcode.data

import android.util.Patterns

enum class BarcodeType {
    URL, WIFI, TEXT
}

data class ScannedBarcode(
    val content: String,
    val type: BarcodeType,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun from(content: String): ScannedBarcode {
            val type = when {
                Patterns.WEB_URL.matcher(content).matches() || content.startsWith("http") -> BarcodeType.URL
                content.startsWith("WIFI:") -> BarcodeType.WIFI
                else -> BarcodeType.TEXT
            }
            return ScannedBarcode(content, type)
        }
    }
}
