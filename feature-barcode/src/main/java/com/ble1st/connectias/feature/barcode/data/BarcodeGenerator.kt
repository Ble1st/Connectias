package com.ble1st.connectias.feature.barcode.data

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object BarcodeGenerator {

    fun generateQr(content: String, size: Int = 512): ImageBitmap? {
        if (content.isBlank()) return null
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        return bitMatrixToBitmap(matrix).asImageBitmap()
    }

    private fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }
}
