package com.ble1st.connectias.feature.document.export

import android.content.ContentResolver
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ble1st.connectias.feature.document.model.PdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class PdfExporter {

    suspend fun export(
        context: Context,
        pages: List<PdfPage>,
        targetUri: Uri,
        includeTextLayer: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pdf = PdfDocument()
            pages.forEachIndexed { index, page ->
                val bitmap = page.bitmap
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val pdfPage = pdf.startPage(pageInfo)
                val canvas = pdfPage.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                if (includeTextLayer && page.ocr != null) {
                    val paint = Paint().apply {
                        color = android.graphics.Color.BLACK
                        alpha = 1 // visible for search, but effectively transparent
                        textSize = 14f
                    }
                    page.ocr.blocks.forEach { block ->
                        val rect = block.boundingBox
                        canvas.drawText(block.text, rect.left, rect.bottom, paint)
                    }
                }

                pdf.finishPage(pdfPage)
            }

            context.contentResolver.openOutputStream(targetUri, "w").use { stream ->
                if (stream == null) error("Cannot open output stream for $targetUri")
                pdf.writeTo(stream)
                stream.flush()
            }
            pdf.close()
        }.onFailure { Timber.e(it, "Failed to export PDF to $targetUri") }
    }
}
