package com.ble1st.connectias.core.services.pdf

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * Describes elements that can be placed on a PDF page.
 */
sealed class PdfElement {
    /**
     * Simple text element rendered with a configurable size and style.
     *
     * @param content Text content to render.
     * @param textSize Text size in sp-like pixels (passed directly to Paint).
     * @param color ARGB color int.
     * @param isBold Whether to render the text in bold style.
     */
    data class Text(
        val content: String,
        val textSize: Float = 14f,
        val color: Int = 0xFF000000.toInt(),
        val isBold: Boolean = false
    ) : PdfElement()

    /**
     * Bitmap image element placed in the flow.
     *
     * @param bitmap Image to render.
     * @param maxWidth Optional max width in pixels to constrain scaling.
     * @param maxHeight Optional max height in pixels to constrain scaling.
     * @param description Optional description for accessibility/diagnostics.
     */
    data class Image(
        val bitmap: Bitmap,
        val maxWidth: Int? = null,
        val maxHeight: Int? = null
    ) : PdfElement()
}

/**
 * Collection of elements that belong to a single PDF page.
 */
data class PdfPageSpec(
    val elements: List<PdfElement>
)

/**
 * Request describing how to generate a PDF.
 *
 * @param fileName Base name for the PDF file (without extension).
 * @param pages Pages to render in order.
 * @param cacheSubDir Optional subdirectory inside cacheDir to store the PDF.
 */
data class PdfGenerationRequest(
    val fileName: String,
    val pages: List<PdfPageSpec>,
    val cacheSubDir: String = "pdfs"
)

/**
 * Result of a PDF generation operation.
 *
 * @param file The generated PDF file in cache.
 * @param uri A Uri pointing to the generated PDF (file://).
 * @param pageCount Number of rendered pages.
 */
data class PdfGenerationResult(
    val file: File,
    val uri: Uri,
    val pageCount: Int
)
