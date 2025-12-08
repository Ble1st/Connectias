package com.ble1st.connectias.feature.document.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.document.export.PdfExporter
import com.ble1st.connectias.feature.document.model.DocumentPage
import com.ble1st.connectias.feature.document.model.OcrPageResult
import com.ble1st.connectias.feature.document.model.PdfPage
import com.ble1st.connectias.feature.document.ocr.OcrEngine
import com.ble1st.connectias.feature.document.scan.ImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DocumentMode { SCAN_TO_PDF, OCR_ONLY }

sealed class DocumentStatus {
    data object Idle : DocumentStatus()
    data object Scanning : DocumentStatus()
    data object OcrRunning : DocumentStatus()
    data object Exporting : DocumentStatus()
    data class Error(val message: String) : DocumentStatus()
    data class Success(val message: String) : DocumentStatus()
}

@HiltViewModel
class DocumentScannerViewModel @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val ocrEngine: OcrEngine,
    private val pdfExporter: PdfExporter
) : ViewModel() {

    private val _pages = MutableStateFlow<List<DocumentPage>>(emptyList())
    val pages: StateFlow<List<DocumentPage>> = _pages

    private val _ocrPages = MutableStateFlow<List<OcrPageResult?>>(emptyList())
    val ocrPages: StateFlow<List<OcrPageResult?>> = _ocrPages

    private val _status = MutableStateFlow<DocumentStatus>(DocumentStatus.Idle)
    val status: StateFlow<DocumentStatus> = _status

    private val _mode = MutableStateFlow(DocumentMode.SCAN_TO_PDF)
    val mode: StateFlow<DocumentMode> = _mode

    fun setMode(mode: DocumentMode) {
        _mode.value = mode
    }

    fun clearStatus() {
        _status.value = DocumentStatus.Idle
    }

    fun addPage(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            _status.value = DocumentStatus.Scanning
            val processed = imageProcessor.process(bitmap)
            _pages.update { it + processed }
            // Keep OCR list aligned
            _ocrPages.update { it + listOf(null) }
            _status.value = DocumentStatus.Idle
        }
    }

    fun removePage(index: Int) {
        if (index < 0 || index >= _pages.value.size) return
        _pages.update { current -> current.toMutableList().also { it.removeAt(index) } }
        _ocrPages.update { current -> current.toMutableList().also { it.removeAt(index) } }
    }

    fun clearAll() {
        _pages.value = emptyList()
        _ocrPages.value = emptyList()
        _status.value = DocumentStatus.Idle
    }

    fun runOcr(languages: List<String> = listOf("eng", "deu")) {
        viewModelScope.launch {
            _status.value = DocumentStatus.OcrRunning
            val results = mutableListOf<OcrPageResult?>()
            pages.value.forEach { page ->
                val ocr = ocrEngine.runOcr(page, languages)
                results.add(ocr)
            }
            _ocrPages.value = results
            _status.value = if (results.all { it == null }) {
                DocumentStatus.Error("OCR fehlgeschlagen (fehlende Sprachdaten?)")
            } else {
                DocumentStatus.Idle
            }
        }
    }

    fun exportPdf(context: android.content.Context, uri: Uri, includeTextLayer: Boolean) {
        viewModelScope.launch {
            _status.value = DocumentStatus.Exporting
            val pdfPages = pages.value.mapIndexed { index, doc ->
                PdfPage(bitmap = doc.bitmap, ocr = _ocrPages.value.getOrNull(index))
            }
            val result = pdfExporter.export(context, pdfPages, uri, includeTextLayer)
            _status.value = if (result.isSuccess) {
                DocumentStatus.Success("PDF gespeichert")
            } else {
                DocumentStatus.Error(result.exceptionOrNull()?.message ?: "Export fehlgeschlagen")
            }
        }
    }

    fun combinedOcrText(): String {
        return _ocrPages.value.filterNotNull().joinToString("\n") { it.text }
    }
}
