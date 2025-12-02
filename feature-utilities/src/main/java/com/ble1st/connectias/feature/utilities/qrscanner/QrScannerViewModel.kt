package com.ble1st.connectias.feature.utilities.qrscanner

import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.utilities.qrscanner.models.ParsedContent
import com.ble1st.connectias.feature.utilities.qrscanner.models.ScanHistoryEntry
import com.ble1st.connectias.feature.utilities.qrscanner.models.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for QR Scanner functionality.
 */
@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val qrScannerProvider: QrScannerProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    private val _scanHistory = MutableStateFlow<List<ScanHistoryEntry>>(emptyList())
    val scanHistory: StateFlow<List<ScanHistoryEntry>> = _scanHistory.asStateFlow()

    /**
     * Processes an image from the camera.
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processImage(imageProxy: ImageProxy) {
        if (_uiState.value.isProcessing || _uiState.value.isPaused) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            qrScannerProvider.processImageProxy(imageProxy).collect { result ->
                handleScanResult(result)
            }

            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    /**
     * Scans an image from the gallery.
     */
    fun scanFromGallery(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            try {
                val result = qrScannerProvider.scanFromUri(uri)
                handleScanResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error scanning from gallery")
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Failed to scan image: ${e.message}"
                    )
                }
            }

            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    /**
     * Handles a scan result.
     */
    private fun handleScanResult(result: ScanResult) {
        when (result) {
            is ScanResult.Success -> {
                Timber.d("Scan success: ${result.rawValue}")
                
                // Add to history
                addToHistory(result)

                _uiState.update {
                    it.copy(
                        lastScanResult = result,
                        isPaused = true, // Pause after successful scan
                        error = null
                    )
                }
            }

            is ScanResult.NoBarcode -> {
                // Continue scanning
            }

            is ScanResult.Error -> {
                Timber.e("Scan error: ${result.message}")
                _uiState.update {
                    it.copy(error = result.message)
                }
            }
        }
    }

    /**
     * Adds a scan result to history.
     */
    private fun addToHistory(result: ScanResult.Success) {
        val entry = ScanHistoryEntry(
            id = System.currentTimeMillis(),
            rawValue = result.rawValue,
            format = result.format,
            contentType = qrScannerProvider.getContentTypeDescription(result.parsedContent)
        )

        _scanHistory.update { history ->
            listOf(entry) + history.take(99) // Keep last 100 entries
        }
    }

    /**
     * Resumes scanning after viewing a result.
     */
    fun resumeScanning() {
        _uiState.update {
            it.copy(
                isPaused = false,
                lastScanResult = null
            )
        }
    }

    /**
     * Pauses scanning.
     */
    fun pauseScanning() {
        _uiState.update { it.copy(isPaused = true) }
    }

    /**
     * Clears the last scan result.
     */
    fun clearResult() {
        _uiState.update {
            it.copy(
                lastScanResult = null,
                error = null
            )
        }
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clears scan history.
     */
    fun clearHistory() {
        _scanHistory.update { emptyList() }
    }

    /**
     * Removes an entry from history.
     */
    fun removeFromHistory(id: Long) {
        _scanHistory.update { history ->
            history.filter { it.id != id }
        }
    }

    /**
     * Toggles favorite status of a history entry.
     */
    fun toggleFavorite(id: Long) {
        _scanHistory.update { history ->
            history.map {
                if (it.id == id) it.copy(isFavorite = !it.isFavorite)
                else it
            }
        }
    }

    /**
     * Sets the flash mode.
     */
    fun setFlashEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isFlashEnabled = enabled) }
    }

    /**
     * Toggles flash mode.
     */
    fun toggleFlash() {
        _uiState.update { it.copy(isFlashEnabled = !it.isFlashEnabled) }
    }

    override fun onCleared() {
        super.onCleared()
        qrScannerProvider.close()
    }
}

/**
 * UI state for QR Scanner.
 */
data class QrScannerUiState(
    val isProcessing: Boolean = false,
    val isPaused: Boolean = false,
    val isFlashEnabled: Boolean = false,
    val lastScanResult: ScanResult.Success? = null,
    val error: String? = null
) {
    val showResult: Boolean get() = lastScanResult != null
}

/**
 * Actions that can be performed on a scan result.
 */
sealed class ScanResultAction {
    data class OpenUrl(val url: String) : ScanResultAction()
    data class ConnectWifi(val ssid: String, val password: String?, val encryption: String) : ScanResultAction()
    data class AddContact(val content: ParsedContent.Contact) : ScanResultAction()
    data class SendEmail(val address: String, val subject: String?, val body: String?) : ScanResultAction()
    data class DialPhone(val number: String) : ScanResultAction()
    data class SendSms(val number: String, val message: String?) : ScanResultAction()
    data class OpenMap(val latitude: Double, val longitude: Double) : ScanResultAction()
    data class AddCalendarEvent(val content: ParsedContent.CalendarEvent) : ScanResultAction()
    data class CopyToClipboard(val text: String) : ScanResultAction()
    data class Share(val text: String) : ScanResultAction()
    data class SearchProduct(val code: String) : ScanResultAction()
}
