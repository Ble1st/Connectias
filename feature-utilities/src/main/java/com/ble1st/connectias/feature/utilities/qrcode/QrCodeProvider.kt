package com.ble1st.connectias.feature.utilities.qrcode

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Hashtable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for QR Code generation and scanning operations.
 */
@Singleton
class QrCodeProvider @Inject constructor() {

    /**
     * Generates a QR Code bitmap from the given text.
     * 
     * @param text The text to encode
     * @param width The width of the QR code image
     * @param height The height of the QR code image
     * @return The QR code bitmap, or null if generation failed
     */
    suspend fun generateQrCode(
        text: String,
        width: Int = 512,
        height: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val hints = Hashtable<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1

            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: WriterException) {
            Timber.e(e, "Failed to generate QR code")
            null
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error generating QR code")
            null
        }
    }

    /**
     * Generates a QR Code for WiFi credentials.
     * Format: WIFI:T:WPA;S:SSID;P:Password;;
     */
    suspend fun generateWifiQrCode(
        ssid: String,
        password: String,
        securityType: String = "WPA",
        width: Int = 512,
        height: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        val wifiString = "WIFI:T:$securityType;S:$ssid;P:$password;;"
        generateQrCode(wifiString, width, height)
    }

    /**
     * Generates a QR Code for contact information (vCard format).
     */
    suspend fun generateContactQrCode(
        name: String,
        phone: String? = null,
        email: String? = null,
        organization: String? = null,
        width: Int = 512,
        height: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        val vCard = buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$name")
            phone?.let { appendLine("TEL:$it") }
            email?.let { appendLine("EMAIL:$it") }
            organization?.let { appendLine("ORG:$it") }
            appendLine("END:VCARD")
        }
        generateQrCode(vCard.trim(), width, height)
    }

    /**
     * Generates a QR Code for URL.
     */
    suspend fun generateUrlQrCode(
        url: String,
        width: Int = 512,
        height: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        val urlString = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        generateQrCode(urlString, width, height)
    }
}

