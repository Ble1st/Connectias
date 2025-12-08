package com.ble1st.connectias.feature.document.ui

import android.graphics.Bitmap
import com.ble1st.connectias.feature.document.export.PdfExporter
import com.ble1st.connectias.feature.document.model.DocumentPage
import com.ble1st.connectias.feature.document.ocr.OcrEngine
import com.ble1st.connectias.feature.document.scan.ImageProcessor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentScannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun runOcrWithoutResultsSetsErrorStatus() = runTest(dispatcher) {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val imageProcessor = mockk<ImageProcessor> {
            every { process(any()) } returns DocumentPage(bitmap)
        }
        val ocrEngine = mockk<OcrEngine> {
            coEvery { runOcr(any(), any()) } returns null
        }
        val pdfExporter = mockk<PdfExporter>(relaxed = true)

        val viewModel = DocumentScannerViewModel(imageProcessor, ocrEngine, pdfExporter)
        viewModel.addPage(bitmap)
        advanceUntilIdle()

        viewModel.runOcr()
        advanceUntilIdle()

        assertTrue(viewModel.status.value is DocumentStatus.Error)
    }
}
