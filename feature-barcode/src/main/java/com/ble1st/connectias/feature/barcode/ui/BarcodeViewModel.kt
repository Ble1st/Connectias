package com.ble1st.connectias.feature.barcode.ui

import androidx.lifecycle.ViewModel
import com.ble1st.connectias.feature.barcode.data.BarcodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class BarcodeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BarcodeUiState())
    val state: StateFlow<BarcodeUiState> = _state

    fun setCameraPermission(granted: Boolean) {
        _state.update { it.copy(hasCameraPermission = granted, errorMessage = null) }
    }

    fun setScanning(active: Boolean) {
        _state.update { it.copy(isScanning = active) }
    }

    fun onCodesDetected(values: List<String>) {
        if (values.isEmpty()) return
        _state.update { state ->
            val merged = (state.scannedCodes + values).distinct()
            state.copy(scannedCodes = merged)
        }
    }

    fun onGeneratorInputChanged(value: String) {
        _state.update { it.copy(generatorInput = value) }
    }

    fun onBatchInputChanged(value: String) {
        _state.update { it.copy(batchInput = value) }
    }

    fun generateSingle() {
        val content = state.value.generatorInput
        val bitmap = BarcodeGenerator.generateQr(content)
        if (bitmap == null) {
            _state.update { it.copy(errorMessage = "Input required to generate QR") }
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

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
