package com.ble1st.connectias.feature.barcode.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.ble1st.connectias.feature.barcode.data.ScannedBarcode

data class GeneratedCode(
    val content: String,
    val bitmap: ImageBitmap
)

enum class GeneratorType {
    TEXT, WIFI, CONTACT, BATCH
}

data class BarcodeUiState(
    val hasCameraPermission: Boolean = false,
    val isScanning: Boolean = false,
    val scannedCodes: List<ScannedBarcode> = emptyList(),
    
    // Camera Controls
    val isTorchOn: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 4.0f,
    
    // Generator
    val selectedGeneratorType: GeneratorType = GeneratorType.TEXT,
    val generatorInput: String = "", // Used for Text
    val batchInput: String = "",
    
    // WiFi Inputs
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    
    // Contact Inputs
    val contactName: String = "",
    val contactPhone: String = "",
    
    val generatedCodes: List<GeneratedCode> = emptyList(),
    val errorMessage: String? = null
)
