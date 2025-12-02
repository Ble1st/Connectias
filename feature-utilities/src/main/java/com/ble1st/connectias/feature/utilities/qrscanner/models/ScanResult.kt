package com.ble1st.connectias.feature.utilities.qrscanner.models

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Represents the result of a barcode/QR code scan.
 */
sealed class ScanResult {
    /**
     * Successful scan with decoded content.
     */
    data class Success(
        val rawValue: String,
        val format: BarcodeFormat,
        val parsedContent: ParsedContent,
        val boundingBox: android.graphics.Rect? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : ScanResult()

    /**
     * No barcode was detected in the image.
     */
    data object NoBarcode : ScanResult()

    /**
     * An error occurred during scanning.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : ScanResult()
}

/**
 * Supported barcode formats.
 */
enum class BarcodeFormat(val mlKitFormat: Int) {
    QR_CODE(Barcode.FORMAT_QR_CODE),
    AZTEC(Barcode.FORMAT_AZTEC),
    CODABAR(Barcode.FORMAT_CODABAR),
    CODE_39(Barcode.FORMAT_CODE_39),
    CODE_93(Barcode.FORMAT_CODE_93),
    CODE_128(Barcode.FORMAT_CODE_128),
    DATA_MATRIX(Barcode.FORMAT_DATA_MATRIX),
    EAN_8(Barcode.FORMAT_EAN_8),
    EAN_13(Barcode.FORMAT_EAN_13),
    ITF(Barcode.FORMAT_ITF),
    PDF417(Barcode.FORMAT_PDF417),
    UPC_A(Barcode.FORMAT_UPC_A),
    UPC_E(Barcode.FORMAT_UPC_E),
    UNKNOWN(Barcode.FORMAT_UNKNOWN);

    companion object {
        fun fromMlKit(format: Int): BarcodeFormat {
            return entries.find { it.mlKitFormat == format } ?: UNKNOWN
        }
    }
}

/**
 * Parsed content from a barcode.
 */
sealed class ParsedContent {
    /**
     * Plain text content.
     */
    data class Text(val text: String) : ParsedContent()

    /**
     * URL content.
     */
    data class Url(val url: String, val title: String? = null) : ParsedContent()

    /**
     * WiFi network credentials.
     */
    data class Wifi(
        val ssid: String,
        val password: String?,
        val encryptionType: WifiEncryption
    ) : ParsedContent()

    /**
     * Contact information (vCard).
     */
    data class Contact(
        val name: String?,
        val organization: String?,
        val title: String?,
        val phones: List<Phone>,
        val emails: List<Email>,
        val addresses: List<Address>,
        val urls: List<String>
    ) : ParsedContent() {
        data class Phone(val number: String, val type: PhoneType)
        data class Email(val address: String, val type: EmailType)
        data class Address(val lines: List<String>, val type: AddressType)

        enum class PhoneType { UNKNOWN, WORK, HOME, FAX, MOBILE }
        enum class EmailType { UNKNOWN, WORK, HOME }
        enum class AddressType { UNKNOWN, WORK, HOME }
    }

    /**
     * Email content.
     */
    data class Email(
        val address: String,
        val subject: String?,
        val body: String?
    ) : ParsedContent()

    /**
     * Phone number.
     */
    data class Phone(val number: String) : ParsedContent()

    /**
     * SMS content.
     */
    data class Sms(val phoneNumber: String, val message: String?) : ParsedContent()

    /**
     * Geographic location.
     */
    data class GeoPoint(val latitude: Double, val longitude: Double) : ParsedContent()

    /**
     * Calendar event.
     */
    data class CalendarEvent(
        val summary: String?,
        val description: String?,
        val location: String?,
        val start: Long?,
        val end: Long?,
        val organizer: String?
    ) : ParsedContent()

    /**
     * Driver's license (US).
     */
    data class DriversLicense(
        val firstName: String?,
        val lastName: String?,
        val middleName: String?,
        val gender: String?,
        val birthDate: String?,
        val addressStreet: String?,
        val addressCity: String?,
        val addressState: String?,
        val addressZip: String?,
        val licenseNumber: String?,
        val issueDate: String?,
        val expiryDate: String?,
        val issuingCountry: String?
    ) : ParsedContent()

    /**
     * ISBN number.
     */
    data class Isbn(val isbn: String) : ParsedContent()

    /**
     * Product code (EAN/UPC).
     */
    data class Product(val code: String, val format: BarcodeFormat) : ParsedContent()

    /**
     * Unknown or unparseable content.
     */
    data class Unknown(val rawValue: String) : ParsedContent()
}

/**
 * WiFi encryption types.
 */
enum class WifiEncryption {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA3,
    UNKNOWN
}

/**
 * Scan history entry for storing scan results.
 */
data class ScanHistoryEntry(
    val id: Long = 0,
    val rawValue: String,
    val format: BarcodeFormat,
    val contentType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
