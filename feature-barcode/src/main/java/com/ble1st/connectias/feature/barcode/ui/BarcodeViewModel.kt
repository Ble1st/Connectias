package com.ble1st.connectias.feature.barcode.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.barcode.data.BarcodeFormatters
import com.ble1st.connectias.feature.barcode.data.BarcodeGenerator
import com.ble1st.connectias.feature.barcode.data.BarcodeRepository
import com.ble1st.connectias.feature.barcode.data.ScannedBarcode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val repository: BarcodeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BarcodeUiState())
    val state: StateFlow<BarcodeUiState> = _state

    init {
        viewModelScope.launch {
            repository.allBarcodes.collect { codes ->
                _state.update { it.copy(scannedCodes = codes) }
            }
        }
    }

    // ... (Permission, Scanning, Torch, Zoom methods same as before) ...
    fun setCameraPermission(granted: Boolean) {
        _state.update { it.copy(hasCameraPermission = granted, errorMessage = null) }
    }

    fun toggleTorch() {
        _state.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun setZoom(ratio: Float) {
        _state.update { it.copy(zoomRatio = ratio) }
    }
    
    fun setMaxZoom(max: Float) {
        _state.update { it.copy(maxZoomRatio = max) }
    }

    fun onCodesDetected(values: List<String>) {
        if (values.isEmpty()) return
        
        viewModelScope.launch {
            val newCodes = values.map { ScannedBarcode.from(it) }
            val currentCodes = _state.value.scannedCodes.map { it.content }.toSet()
            
            newCodes.filter { it.content !in currentCodes }.forEach { code ->
                repository.saveBarcode(code)
            }
        }
    }

    // Generator Inputs
    fun setGeneratorType(type: GeneratorType) {
        _state.update { it.copy(selectedGeneratorType = type) }
    }
    
    fun onGeneratorInputChanged(value: String) {
        _state.update { it.copy(generatorInput = value) }
    }

    fun onBatchInputChanged(value: String) {
        _state.update { it.copy(batchInput = value) }
    }
    
    fun updateWifiSsid(value: String) {
        _state.update { it.copy(wifiSsid = value) }
    }
    
    fun updateWifiPassword(value: String) {
        _state.update { it.copy(wifiPassword = value) }
    }
    
    fun updateContactName(value: String) {
        _state.update { it.copy(contactName = value) }
    }
    
    fun updateContactPhone(value: String) {
        _state.update { it.copy(contactPhone = value) }
    }

    fun generateSingle() {
        val content = when(state.value.selectedGeneratorType) {
            GeneratorType.WIFI -> {
                BarcodeFormatters.formatWifi(state.value.wifiSsid, state.value.wifiPassword)
            }
            GeneratorType.CONTACT -> {
                val names = state.value.contactName.split(" ", limit = 2)
                val first = names.getOrElse(0) { "" }
                val last = names.getOrElse(1) { "" }
                BarcodeFormatters.formatVCard(first, last, "", state.value.contactPhone)
            }
            else -> state.value.generatorInput // TEXT
        }
        
        if (content.isBlank()) {
             _state.update { it.copy(errorMessage = "Input required") }
             return
        }

        val bitmap = BarcodeGenerator.generateQr(content)
        if (bitmap == null) {
            _state.update { it.copy(errorMessage = "Failed to generate QR") }
            return
        }
        val code = GeneratedCode(content = content, bitmap = bitmap)
        _state.update { it.copy(generatedCodes = listOf(code), errorMessage = null) }
    }

    fun generateBatch() {
        val lines = state.value.batchInput.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            _state.update { it.copy(errorMessage = "No entries to generate") }
            return
        }
        val codes = lines.mapNotNull { line ->
            BarcodeGenerator.generateQr(line)?.let { GeneratedCode(content = line, bitmap = it) }
        }
        _state.update { it.copy(generatedCodes = codes, errorMessage = null) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
