package com.ble1st.connectias.feature.utilities.qrcode

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for QR Code Tools.
 */
@HiltViewModel
class QrCodeViewModel @Inject constructor(
    private val qrCodeProvider: QrCodeProvider
) : ViewModel() {

    private val _qrCodeState = MutableStateFlow<QrCodeState>(QrCodeState.Idle)
    val qrCodeState: StateFlow<QrCodeState> = _qrCodeState.asStateFlow()

    /**
     * Generates a QR code from text.
     */
    fun generateQrCode(text: String, width: Int = 512, height: Int = 512) {
        if (text.isBlank()) {
            _qrCodeState.value = QrCodeState.Error("Text cannot be empty")
            return
        }

        viewModelScope.launch {
            _qrCodeState.value = QrCodeState.Loading
            val bitmap = qrCodeProvider.generateQrCode(text, width, height)
            _qrCodeState.value = if (bitmap != null) {
                QrCodeState.Success(bitmap)
            } else {
                QrCodeState.Error("Failed to generate QR code")
            }
        }
    }

    /**
     * Generates a WiFi QR code.
     */
    fun generateWifiQrCode(
        ssid: String,
        password: String,
        securityType: String = "WPA"
    ) {
        if (ssid.isBlank()) {
            _qrCodeState.value = QrCodeState.Error("SSID cannot be empty")
            return
        }

        viewModelScope.launch {
            _qrCodeState.value = QrCodeState.Loading
            val bitmap = qrCodeProvider.generateWifiQrCode(ssid, password, securityType)
            _qrCodeState.value = if (bitmap != null) {
                QrCodeState.Success(bitmap)
            } else {
                QrCodeState.Error("Failed to generate WiFi QR code")
            }
        }
    }

    /**
     * Generates a contact QR code.
     */
    fun generateContactQrCode(
        name: String,
        phone: String? = null,
        email: String? = null,
        organization: String? = null
    ) {
        if (name.isBlank()) {
            _qrCodeState.value = QrCodeState.Error("Name cannot be empty")
            return
        }

        viewModelScope.launch {
            _qrCodeState.value = QrCodeState.Loading
            val bitmap = qrCodeProvider.generateContactQrCode(name, phone, email, organization)
            _qrCodeState.value = if (bitmap != null) {
                QrCodeState.Success(bitmap)
            } else {
                QrCodeState.Error("Failed to generate contact QR code")
            }
        }
    }

    /**
     * Generates a URL QR code.
     */
    fun generateUrlQrCode(url: String) {
        if (url.isBlank()) {
            _qrCodeState.value = QrCodeState.Error("URL cannot be empty")
            return
        }

        viewModelScope.launch {
            _qrCodeState.value = QrCodeState.Loading
            val bitmap = qrCodeProvider.generateUrlQrCode(url)
            _qrCodeState.value = if (bitmap != null) {
                QrCodeState.Success(bitmap)
            } else {
                QrCodeState.Error("Failed to generate URL QR code")
            }
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _qrCodeState.value = QrCodeState.Idle
    }
}

/**
 * State representation for QR code operations.
 */
sealed class QrCodeState {
    object Idle : QrCodeState()
    object Loading : QrCodeState()
    data class Success(val bitmap: Bitmap) : QrCodeState()
    data class Error(val message: String) : QrCodeState()
}

