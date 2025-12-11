package com.ble1st.connectias.core.services.pdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfGeneratorTest {

    @Test
    fun generatePdf_writesFileToCacheAndReturnsMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val generator = PdfGenerator(context)

        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }

        val request = PdfGenerationRequest(
            fileName = "test_document",
            cacheSubDir = "test_pdfs",
            pages = listOf(
                PdfPageSpec(
                    elements = listOf(
                        PdfElement.Text(content = "Hello PDF"),
                        PdfElement.Image(bitmap = bitmap)
                    )
                )
            )
        )

        val result = generator.generatePdf(request)

        try {
            assertTrue("PDF file should exist", result.file.exists())
            assertTrue("PDF file should not be empty", result.file.length() > 0)
            assertEquals("Expected single page output", 1, result.pageCount)
        } finally {
            result.file.delete()
        }
    }
}
