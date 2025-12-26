package com.ble1st.connectias.core.services.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import kotlin.math.min
import androidx.core.graphics.scale

/**
 * Generates PDFs with a simple flow layout (text + images) and stores them in the app cache.
 *
 * Layout rules:
 * - A4 portrait (595x842) with configurable margins.
 * - Text wraps automatically to the available width.
 * - Images are scaled down to fit the available width/height while keeping aspect ratio.
 * - Elements are rendered top-down; if the remaining space is insufficient, a new page is started.
 */
class PdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @VisibleForTesting
    internal data class LayoutConfig(
        val pageWidth: Int = 595, // A4 at ~72dpi
        val pageHeight: Int = 842,
        val margin: Float = 40f,
        val lineSpacingMultiplier: Float = 1.2f
    )

    private val layout = LayoutConfig()

    /**
     * Generate a PDF for the given request and save it under cacheDir/cacheSubDir.
     *
     * @throws IllegalArgumentException if the request is invalid.
     * @throws IllegalStateException for rendering or IO errors.
     */
    fun generatePdf(request: PdfGenerationRequest): PdfGenerationResult {
        require(request.pages.isNotEmpty()) { "At least one page is required" }

        val sanitizedName = sanitizeFileName(request.fileName).ifBlank { "document" }
        val outputDir = File(context.cacheDir, request.cacheSubDir).apply { mkdirs() }
        val outputFile = File(
            outputDir,
            "${sanitizedName}_${System.currentTimeMillis()}.pdf"
        )

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = createPage(pdfDocument, pageNumber)
        var canvas = page.canvas
        var cursorY = layout.margin

        try {
            request.pages.forEachIndexed { index, pageSpec ->
                pageSpec.elements.forEach { element ->
                    when (element) {
                        is PdfElement.Text -> {
                            val paint = createTextPaint(element)
                            val lines = wrapText(element.content, paint, layout.pageWidth - (2 * layout.margin))
                            val lineHeight = paint.lineHeight(layout.lineSpacingMultiplier)
                            val requiredHeight = lines.size * lineHeight

                            if (needsNewPage(cursorY, requiredHeight)) {
                                pageNumber += 1
                                page = startNewPage(pdfDocument, pageNumber, page).also { canvas = it.canvas }
                                cursorY = layout.margin
                            }

                            lines.forEach { line ->
                                cursorY += lineHeight
                                canvas.drawText(line, layout.margin, cursorY, paint)
                            }
                        }
                        is PdfElement.Image -> {
                            val scaled = scaleBitmap(element.bitmap, element, layout.pageWidth - (2 * layout.margin))
                            val requiredHeight = scaled.height.toFloat()

                            if (needsNewPage(cursorY, requiredHeight)) {
                                pageNumber += 1
                                page = startNewPage(pdfDocument, pageNumber, page).also { canvas = it.canvas }
                                cursorY = layout.margin
                            }

                            canvas.drawBitmap(
                                scaled,
                                layout.margin,
                                cursorY,
                                null
                            )
                            cursorY += requiredHeight
                        }
                    }
                }

                if (index < request.pages.lastIndex) {
                    // Finish current page and start a new one for the next pageSpec
                    pageNumber += 1
                    page = startNewPage(pdfDocument, pageNumber, page).also { canvas = it.canvas }
                    cursorY = layout.margin
                }
            }

            pdfDocument.finishPage(page)

            FileOutputStream(outputFile).use { output ->
                pdfDocument.writeTo(output)
            }

            return PdfGenerationResult(
                file = outputFile,
                uri = outputFile.toUri(),
                pageCount = pageNumber
            )
        } catch (t: Throwable) {
            outputFile.delete()
            throw IllegalStateException("Failed to generate PDF: ${t.message}", t)
        } finally {
            pdfDocument.close()
        }
    }

    private fun needsNewPage(cursorY: Float, requiredHeight: Float): Boolean {
        val maxY = layout.pageHeight - layout.margin
        return cursorY + requiredHeight > maxY
    }

    private fun createTextPaint(element: PdfElement.Text): Paint {
        return Paint().apply {
            isAntiAlias = true
            color = element.color
            textSize = element.textSize
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (element.isBold) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    private fun wrapText(content: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        content.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                lines.add("")
            } else {
                val words = paragraph.trim().split(Regex("\\s+"))
                val builder = StringBuilder()
                for (word in words) {
                    val candidate = if (builder.isEmpty()) word else "$builder $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        builder.clear()
                        builder.append(candidate)
                    } else {
                        if (builder.isNotEmpty()) {
                            lines.add(builder.toString())
                        }
                        if (paint.measureText(word) <= maxWidth) {
                            builder.clear()
                            builder.append(word)
                        } else {
                            // Hard wrap very long words
                            lines.addAll(hardWrapWord(word, paint, maxWidth))
                            builder.clear()
                        }
                    }
                }
                if (builder.isNotEmpty()) {
                    lines.add(builder.toString())
                }
            }
        }
        return lines
    }

    private fun hardWrapWord(word: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = start + 1
            while (end <= word.length && paint.measureText(word, start, end) <= maxWidth) {
                end++
            }
            if (end == start + 1) {
                // Single character exceeds width; force add to avoid infinite loop
                end = start + 1
            } else {
                end -= 1
            }
            result.add(word.substring(start, end))
            start = end
        }
        return result
    }

    private fun scaleBitmap(
        bitmap: Bitmap,
        element: PdfElement.Image,
        maxContentWidth: Float
    ): Bitmap {
        val targetWidth = min(
            maxContentWidth,
            element.maxWidth?.toFloat() ?: maxContentWidth
        )
        val targetHeight = element.maxHeight?.toFloat() ?: Float.MAX_VALUE
        val widthScale = targetWidth / bitmap.width.toFloat()
        val heightScale = targetHeight / bitmap.height.toFloat()
        val scale = min(1f, min(widthScale, heightScale))
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return if (scaledWidth == bitmap.width && scaledHeight == bitmap.height) {
            bitmap
        } else {
            bitmap.scale(scaledWidth, scaledHeight)
        }
    }

    private fun Paint.lineHeight(lineSpacingMultiplier: Float): Float {
        val metrics = fontMetrics
        return (metrics.descent - metrics.ascent) * lineSpacingMultiplier
    }

    private fun createPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page {
        return document.startPage(
            PdfDocument.PageInfo.Builder(
                layout.pageWidth,
                layout.pageHeight,
                pageNumber
            ).create()
        )
    }

    private fun startNewPage(
        document: PdfDocument,
        pageNumber: Int,
        currentPage: PdfDocument.Page
    ): PdfDocument.Page {
        document.finishPage(currentPage)
        return createPage(document, pageNumber)
    }

    private fun sanitizeFileName(input: String): String {
        return input.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9._-]"), "_")
            .trim('_')
    }
}
