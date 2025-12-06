package com.ble1st.connectias.feature.deviceinfo.connectivity.ui.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NdefRecordType
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NfcEvent
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NfcTagInfo
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NfcWriteResult
import com.ble1st.connectias.feature.deviceinfo.connectivity.nfc.NfcToolsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for NFC Tools screen.
 */
@HiltViewModel
class NfcToolsViewModel @Inject constructor(
    private val nfcToolsProvider: NfcToolsProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(NfcToolsUiState())
    val uiState: StateFlow<NfcToolsUiState> = _uiState.asStateFlow()

    val isNfcEnabled = nfcToolsProvider.isEnabled
    val currentTag = nfcToolsProvider.currentTag

    init {
        viewModelScope.launch {
            nfcToolsProvider.events.collect { event ->
                handleNfcEvent(event)
            }
        }
        checkNfcAvailability()
    }

    private fun checkNfcAvailability() {
        _uiState.update { state ->
            state.copy(
                isNfcAvailable = nfcToolsProvider.isNfcAvailable(),
                isNfcEnabled = nfcToolsProvider.isNfcEnabled()
            )
        }
    }

    private fun handleNfcEvent(event: NfcEvent) {
        when (event) {
            is NfcEvent.TagDiscovered -> {
                _uiState.update { state ->
                    state.copy(
                        lastScannedTag = event.tagInfo,
                        isScanning = false,
                        snackbarMessage = "Tag discovered: ${event.tagInfo.type}"
                    )
                }
            }
            is NfcEvent.WriteSuccess -> {
                _uiState.update { state ->
                    state.copy(
                        isWriting = false,
                        snackbarMessage = "Successfully written ${event.bytesWritten} bytes",
                        showWriteDialog = false
                    )
                }
            }
            is NfcEvent.Error -> {
                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        isWriting = false,
                        snackbarMessage = event.message
                    )
                }
            }
            else -> {}
        }
    }

    /**
     * Updates NFC enabled state.
     */
    fun updateNfcState() {
        nfcToolsProvider.updateEnabledState()
        checkNfcAvailability()
    }

    /**
     * Processes an NFC intent from the activity.
     */
    fun processNfcIntent(intent: Intent) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                nfcToolsProvider.processIntent(intent)
            } catch (e: Exception) {
                Timber.e(e, "Error processing NFC intent")
                _uiState.update { state ->
                    state.copy(
                        isScanning = false,
                        snackbarMessage = "Error reading tag: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Enables foreground dispatch for NFC.
     */
    fun enableForegroundDispatch(activity: Activity) {
        nfcToolsProvider.enableForegroundDispatch(activity)
        _uiState.update { it.copy(isScanning = true) }
    }

    /**
     * Disables foreground dispatch.
     */
    fun disableForegroundDispatch(activity: Activity) {
        nfcToolsProvider.disableForegroundDispatch(activity)
        _uiState.update { it.copy(isScanning = false) }
    }

    /**
     * Shows the write dialog.
     */
    fun showWriteDialog() {
        _uiState.update { it.copy(showWriteDialog = true) }
    }

    /**
     * Hides the write dialog.
     */
    fun hideWriteDialog() {
        _uiState.update { it.copy(showWriteDialog = false) }
    }

    /**
     * Writes a URI record to the tag.
     */
    fun writeUriRecord(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWriting = true) }
            try {
                val record = nfcToolsProvider.createUriRecord(uri)
                val message = nfcToolsProvider.createNdefMessage(record)
                val result = nfcToolsProvider.writeNdefMessage(message)
                handleWriteResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error writing URI record")
                _uiState.update { state ->
                    state.copy(
                        isWriting = false,
                        snackbarMessage = "Error writing: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Writes a text record to the tag.
     */
    fun writeTextRecord(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWriting = true) }
            try {
                val record = nfcToolsProvider.createTextRecord(text)
                val message = nfcToolsProvider.createNdefMessage(record)
                val result = nfcToolsProvider.writeNdefMessage(message)
                handleWriteResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error writing text record")
                _uiState.update { state ->
                    state.copy(
                        isWriting = false,
                        snackbarMessage = "Error writing: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Writes a WiFi configuration record to the tag.
     */
    fun writeWifiRecord(ssid: String, password: String?, authType: String = "WPA") {
        viewModelScope.launch {
            _uiState.update { it.copy(isWriting = true) }
            try {
                val record = nfcToolsProvider.createWifiRecord(ssid, password, authType)
                val message = nfcToolsProvider.createNdefMessage(record)
                val result = nfcToolsProvider.writeNdefMessage(message)
                handleWriteResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error writing WiFi record")
                _uiState.update { state ->
                    state.copy(
                        isWriting = false,
                        snackbarMessage = "Error writing: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Writes a vCard record to the tag.
     */
    fun writeVCardRecord(name: String, phone: String?, email: String?, organization: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWriting = true) }
            try {
                val record = nfcToolsProvider.createVCardRecord(name, phone, email, organization)
                val message = nfcToolsProvider.createNdefMessage(record)
                val result = nfcToolsProvider.writeNdefMessage(message)
                handleWriteResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error writing vCard record")
                _uiState.update { state ->
                    state.copy(
                        isWriting = false,
                        snackbarMessage = "Error writing: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Formats the tag.
     */
    fun formatTag() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWriting = true) }
            try {
                val result = nfcToolsProvider.formatTag()
                handleWriteResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error formatting tag")
                _uiState.update { state ->
                    state.copy(
                        isWriting = false,
                        snackbarMessage = "Error formatting: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Makes the tag read-only.
     */
    fun makeTagReadOnly() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWriting = true) }
            try {
                val result = nfcToolsProvider.makeTagReadOnly()
                handleWriteResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error making tag read-only")
                _uiState.update { state ->
                    state.copy(
                        isWriting = false,
                        snackbarMessage = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    private fun handleWriteResult(result: NfcWriteResult) {
        val message = when (result) {
            is NfcWriteResult.Success -> "Successfully written ${result.bytesWritten} bytes"
            is NfcWriteResult.Error -> "Error: ${result.message}"
            NfcWriteResult.TagLost -> "Tag lost during operation"
            NfcWriteResult.TagNotWritable -> "Tag is not writable"
            NfcWriteResult.TagTooSmall -> "Tag has insufficient capacity"
        }
        _uiState.update { state ->
            state.copy(
                isWriting = false,
                snackbarMessage = message,
                showWriteDialog = result is NfcWriteResult.Success
            )
        }
    }

    /**
     * Clears the current tag.
     */
    fun clearCurrentTag() {
        nfcToolsProvider.clearCurrentTag()
        _uiState.update { it.copy(lastScannedTag = null) }
    }

    /**
     * Clears the snackbar message.
     */
    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Sets the selected record type for writing.
     */
    fun setSelectedRecordType(type: NdefRecordType) {
        _uiState.update { it.copy(selectedWriteType = type) }
    }
}

/**
 * UI state for NFC Tools screen.
 */
data class NfcToolsUiState(
    val isNfcAvailable: Boolean = false,
    val isNfcEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isWriting: Boolean = false,
    val lastScannedTag: NfcTagInfo? = null,
    val showWriteDialog: Boolean = false,
    val selectedWriteType: NdefRecordType = NdefRecordType.URI,
    val snackbarMessage: String? = null
)

