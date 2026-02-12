package com.ble1st.connectias.feature.scanner.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrRepository @Inject constructor() {
    private val tessBaseApi = TessBaseAPI()
    private var isInitialized = false

    suspend fun createSearchablePdf(
        pages: List<Bitmap>,
        outputFile: File,
        applyOcr: Boolean = true
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            val pdfDocument = PdfDocument()
            Paint()

            pages.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // 1. Draw Image
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                // 2. OCR Overlay (Invisible Text)
                if (applyOcr && isInitialized) {
                    try {
                        tessBaseApi.setImage(bitmap)
                        // Get HOCR or Words
                        // For simplicity in this demo, we just extract full text and put it hidden
                        // Real "searchable PDF" requires precise bounding box placement
                        val text = tessBaseApi.utF8Text
                        if (!text.isNullOrEmpty()) {
                            val invisiblePaint = Paint().apply {
                                color = Color.TRANSPARENT
                                textSize = 12f
                            }
                            // Simplified: Draw text at top-left. 
                            // True implementation iterates words and draws them at their rects.
                            canvas.drawText(text, 10f, 10f, invisiblePaint)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "OCR failed for page $index")
                    }
                }

                pdfDocument.finishPage(page)
            }

            withContext(Dispatchers.IO) {
                FileOutputStream(outputFile).use { out ->
                    pdfDocument.writeTo(out)
                }
            }
            
            pdfDocument.close()
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "PDF creation failed")
            return@withContext false
        }
    }
}
