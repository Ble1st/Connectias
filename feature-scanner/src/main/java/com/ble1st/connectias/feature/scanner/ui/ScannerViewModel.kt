package com.ble1st.connectias.feature.scanner.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.scanner.data.ScanProgress
import com.ble1st.connectias.feature.scanner.data.ScanSource
import com.ble1st.connectias.feature.scanner.data.ScannerDevice
import com.ble1st.connectias.feature.scanner.data.ScannerRepository
import android.net.Uri
import com.ble1st.connectias.feature.scanner.domain.EnhancementMode
import com.ble1st.connectias.feature.scanner.domain.ImageProcessor
import com.ble1st.connectias.feature.scanner.domain.OcrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class ScannerUiState(
    val devices: List<ScannerDevice> = emptyList(),
    val selectedDevice: ScannerDevice? = null,
    val isScanning: Boolean = false,
    val scanSource: ScanSource = ScanSource.FLATBED,
    val scanProgress: Int = 0,
    val scanMessage: String = "",
    val scannedPages: List<Bitmap> = emptyList(),
    val processedPages: List<Bitmap> = emptyList(), // Enhanced images
    val enhancementMode: EnhancementMode = EnhancementMode.ORIGINAL,
    val error: String? = null,
    val isDiscoveryActive: Boolean = false,
    val savedFilePath: String? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: ScannerRepository,
    private val imageProcessor: ImageProcessor,
    private val ocrRepository: OcrRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // Track discovery job to prevent multiple concurrent discoveries
    private var discoveryJob: Job? = null

    init {
        startDiscovery()
    }
    
    // ... Existing Discovery methods ...

    fun rotatePage(index: Int) {
        val currentScanned = _uiState.value.scannedPages
        if (index !in currentScanned.indices) return

        viewModelScope.launch {
            val originalBitmap = currentScanned[index]
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
            )
            
            val newScannedList = currentScanned.toMutableList().apply {
                this[index] = rotatedBitmap
            }
            
            _uiState.update { it.copy(scannedPages = newScannedList) }
            applyEnhancement(_uiState.value.enhancementMode)
        }
    }

    fun deletePage(index: Int) {
        val currentScanned = _uiState.value.scannedPages
        if (index !in currentScanned.indices) return

        viewModelScope.launch {
            val newScannedList = currentScanned.toMutableList().apply {
                removeAt(index)
            }
            
            _uiState.update { it.copy(scannedPages = newScannedList) }
            applyEnhancement(_uiState.value.enhancementMode)
        }
    }

    fun onPagesCaptured(bitmaps: List<Bitmap>) {
        _uiState.update { 
            it.copy(
                scannedPages = it.scannedPages + bitmaps,
                processedPages = it.processedPages + bitmaps // Initially distinct but same
            ) 
        }
        applyEnhancement(_uiState.value.enhancementMode)
    }

    fun setEnhancementMode(mode: EnhancementMode) {
        if (_uiState.value.enhancementMode == mode) return
        _uiState.update { it.copy(enhancementMode = mode) }
        applyEnhancement(mode)
    }

    private fun applyEnhancement(mode: EnhancementMode) {
        val originalPages = _uiState.value.scannedPages
        if (originalPages.isEmpty()) return

        viewModelScope.launch {
            val processed = originalPages.map { bmp ->
                imageProcessor.enhanceDocument(bmp, mode)
            }
            _uiState.update { it.copy(processedPages = processed) }
        }
    }

    fun saveDocumentToUri(uri: Uri, context: android.content.Context) {
        val pages = uiState.value.processedPages
        if (pages.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // 1. Create Temp File for PDF Gen
                val tempFile = File(context.cacheDir, "temp_scan.pdf")
                
                // 2. Generate PDF with OCR
                val success = ocrRepository.createSearchablePdf(pages, tempFile, applyOcr = true)
                
                if (success) {
                    // 3. Write to user selected URI
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    _uiState.update { it.copy(savedFilePath = uri.toString(), scanMessage = "Saved to storage") }
                } else {
                    _uiState.update { it.copy(error = "PDF Generation failed") }
                }
            } catch (e: Exception) {
                Timber.e(e, "Save failed")
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    // ... Existing methods (startScan, etc) ...
    // Note: I am replacing the file content completely to integrate properly, 
    // but preserving the logic.
    
    fun startDiscovery() {
        // Cancel previous discovery if running
        discoveryJob?.cancel()
        
        _uiState.update { it.copy(isDiscoveryActive = true, error = null) }
        discoveryJob = viewModelScope.launch {
            try {
                repository.discoverScanners().collect { devices ->
                    _uiState.update { it.copy(devices = devices) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Discovery error")
                _uiState.update { 
                    it.copy(
                        isDiscoveryActive = false,
                        error = "Discovery failed: ${e.message}"
                    ) 
                }
            } finally {
                _uiState.update { it.copy(isDiscoveryActive = false) }
            }
        }
    }

    fun selectDevice(device: ScannerDevice) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun setScanSource(source: ScanSource) {
        _uiState.update { it.copy(scanSource = source) }
    }

    fun startScan() {
        val device = uiState.value.selectedDevice ?: return
        val source = uiState.value.scanSource

        _uiState.update { it.copy(isScanning = true, scanProgress = 0, scanMessage = "Starting...", error = null) }

        viewModelScope.launch {
            repository.scanDocument(device, source).collect { progress ->
                when (progress) {
                    is ScanProgress.Progress -> {
                        _uiState.update { 
                            it.copy(
                                scanProgress = progress.percentage,
                                scanMessage = progress.message
                            ) 
                        }
                    }
                    is ScanProgress.PageScanned -> {
                        // Add to pages and trigger enhancement
                        val newPages = _uiState.value.scannedPages + progress.bitmap
                        _uiState.update { it.copy(scannedPages = newPages) }
                        applyEnhancement(_uiState.value.enhancementMode)
                    }
                    is ScanProgress.Completed -> {
                        _uiState.update { 
                            it.copy(
                                isScanning = false, 
                                scanMessage = "Scan Completed", 
                                scanProgress = 100
                            ) 
                        }
                    }
                    is ScanProgress.Error -> {
                        _uiState.update { 
                            it.copy(
                                isScanning = false, 
                                error = progress.message
                            ) 
                        }
                    }
                }
            }
        }
    }
    
    fun clearError() {
         _uiState.update { it.copy(error = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
}
