package com.ble1st.connectias.feature.barcode.ui

import androidx.compose.ui.graphics.ImageBitmap

data class GeneratedCode(
    val content: String,
    val bitmap: ImageBitmap
)

data class BarcodeUiState(
    val hasCameraPermission: Boolean = false,
    val isScanning: Boolean = false,
    val scannedCodes: List<String> = emptyList(),
    val generatorInput: String = "",
    val batchInput: String = "",
    val generatedCodes: List<GeneratedCode> = emptyList(),
    val errorMessage: String? = null
)
