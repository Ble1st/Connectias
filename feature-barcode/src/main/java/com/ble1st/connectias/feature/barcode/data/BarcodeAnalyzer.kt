package com.ble1st.connectias.feature.barcode.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import androidx.camera.core.ExperimentalGetImage

class BarcodeAnalyzer(
    private val onCodesDetected: (List<String>) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_DATA_MATRIX
                )
                .build()
        )
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val values = barcodes.mapNotNull(Barcode::getRawValue)
                if (values.isNotEmpty()) {
                    onCodesDetected(values)
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Barcode scan failed")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
