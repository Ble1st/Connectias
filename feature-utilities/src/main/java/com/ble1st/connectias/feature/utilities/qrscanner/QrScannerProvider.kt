package com.ble1st.connectias.feature.utilities.qrscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.ble1st.connectias.feature.utilities.qrscanner.models.BarcodeFormat
import com.ble1st.connectias.feature.utilities.qrscanner.models.ParsedContent
import com.ble1st.connectias.feature.utilities.qrscanner.models.ScanResult
import com.ble1st.connectias.feature.utilities.qrscanner.models.WifiEncryption
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Provider for QR code and barcode scanning functionality.
 *
 * Uses ML Kit for barcode detection and provides:
 * - Real-time camera scanning
 * - Image file scanning
 * - Content parsing (URLs, WiFi, contacts, etc.)
 * - Scan history management
 */
@Singleton
class QrScannerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_ALL_FORMATS
        )
        .enableAllPotentialBarcodes()
        .build()

    private val barcodeScanner = BarcodeScanning.getClient(scannerOptions)

    /**
     * Processes an ImageProxy from CameraX and returns scan results.
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processImageProxy(imageProxy: ImageProxy): Flow<ScanResult> = flow {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                emit(ScanResult.NoBarcode)
                return@flow
            }

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            val result = scanImage(inputImage)
            emit(result)
        } catch (e: Exception) {
            Timber.e(e, "Error processing image proxy")
            emit(ScanResult.Error("Failed to process image", e))
        } finally {
            imageProxy.close()
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Scans a barcode from a URI (gallery image).
     */
    suspend fun scanFromUri(uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ScanResult.Error("Could not open image")

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                return@withContext ScanResult.Error("Could not decode image")
            }

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            scanImage(inputImage)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning image from URI: $uri")
            ScanResult.Error("Failed to scan image", e)
        }
    }

    /**
     * Scans a barcode from a Bitmap.
     */
    suspend fun scanFromBitmap(bitmap: Bitmap): ScanResult = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            scanImage(inputImage)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning bitmap")
            ScanResult.Error("Failed to scan bitmap", e)
        }
    }

    /**
     * Internal method to scan an InputImage.
     */
    private suspend fun scanImage(inputImage: InputImage): ScanResult {
        return suspendCancellableCoroutine { continuation ->
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isEmpty()) {
                        continuation.resume(ScanResult.NoBarcode)
                    } else {
                        // Return the first valid barcode
                        val barcode = barcodes.first()
                        val rawValue = barcode.rawValue ?: ""
                        val format = BarcodeFormat.fromMlKit(barcode.format)
                        val parsedContent = parseContent(barcode)

                        continuation.resume(
                            ScanResult.Success(
                                rawValue = rawValue,
                                format = format,
                                parsedContent = parsedContent,
                                boundingBox = barcode.boundingBox
                            )
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    Timber.e(exception, "ML Kit barcode scanning failed")
                    continuation.resumeWithException(exception)
                }

            continuation.invokeOnCancellation {
                // Scanner cleanup if needed
            }
        }
    }

    /**
     * Parses the content of a barcode into a structured format.
     */
    fun parseContent(barcode: Barcode): ParsedContent {
        return when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                val url = barcode.url
                ParsedContent.Url(
                    url = url?.url ?: barcode.rawValue ?: "",
                    title = url?.title
                )
            }

            Barcode.TYPE_WIFI -> {
                val wifi = barcode.wifi
                ParsedContent.Wifi(
                    ssid = wifi?.ssid ?: "",
                    password = wifi?.password,
                    encryptionType = when (wifi?.encryptionType) {
                        Barcode.WiFi.TYPE_OPEN -> WifiEncryption.OPEN
                        Barcode.WiFi.TYPE_WEP -> WifiEncryption.WEP
                        Barcode.WiFi.TYPE_WPA -> WifiEncryption.WPA
                        else -> WifiEncryption.UNKNOWN
                    }
                )
            }

            Barcode.TYPE_CONTACT_INFO -> {
                val contact = barcode.contactInfo
                ParsedContent.Contact(
                    name = contact?.name?.formattedName,
                    organization = contact?.organization,
                    title = contact?.title,
                    phones = contact?.phones?.map { phone ->
                        ParsedContent.Contact.Phone(
                            number = phone.number ?: "",
                            type = when (phone.type) {
                                Barcode.Phone.TYPE_WORK -> ParsedContent.Contact.PhoneType.WORK
                                Barcode.Phone.TYPE_HOME -> ParsedContent.Contact.PhoneType.HOME
                                Barcode.Phone.TYPE_FAX -> ParsedContent.Contact.PhoneType.FAX
                                Barcode.Phone.TYPE_MOBILE -> ParsedContent.Contact.PhoneType.MOBILE
                                else -> ParsedContent.Contact.PhoneType.UNKNOWN
                            }
                        )
                    } ?: emptyList(),
                    emails = contact?.emails?.map { email ->
                        ParsedContent.Contact.Email(
                            address = email.address ?: "",
                            type = when (email.type) {
                                Barcode.Email.TYPE_WORK -> ParsedContent.Contact.EmailType.WORK
                                Barcode.Email.TYPE_HOME -> ParsedContent.Contact.EmailType.HOME
                                else -> ParsedContent.Contact.EmailType.UNKNOWN
                            }
                        )
                    } ?: emptyList(),
                    addresses = contact?.addresses?.map { address ->
                        ParsedContent.Contact.Address(
                            lines = address.addressLines?.toList() ?: emptyList(),
                            type = when (address.type) {
                                Barcode.Address.TYPE_WORK -> ParsedContent.Contact.AddressType.WORK
                                Barcode.Address.TYPE_HOME -> ParsedContent.Contact.AddressType.HOME
                                else -> ParsedContent.Contact.AddressType.UNKNOWN
                            }
                        )
                    } ?: emptyList(),
                    urls = contact?.urls ?: emptyList()
                )
            }

            Barcode.TYPE_EMAIL -> {
                val email = barcode.email
                ParsedContent.Email(
                    address = email?.address ?: "",
                    subject = email?.subject,
                    body = email?.body
                )
            }

            Barcode.TYPE_PHONE -> {
                ParsedContent.Phone(
                    number = barcode.phone?.number ?: barcode.rawValue ?: ""
                )
            }

            Barcode.TYPE_SMS -> {
                val sms = barcode.sms
                ParsedContent.Sms(
                    phoneNumber = sms?.phoneNumber ?: "",
                    message = sms?.message
                )
            }

            Barcode.TYPE_GEO -> {
                val geo = barcode.geoPoint
                ParsedContent.GeoPoint(
                    latitude = geo?.lat ?: 0.0,
                    longitude = geo?.lng ?: 0.0
                )
            }

            Barcode.TYPE_CALENDAR_EVENT -> {
                val event = barcode.calendarEvent
                ParsedContent.CalendarEvent(
                    summary = event?.summary,
                    description = event?.description,
                    location = event?.location,
                    start = event?.start?.rawValue?.let { parseCalendarDateTime(it) },
                    end = event?.end?.rawValue?.let { parseCalendarDateTime(it) },
                    organizer = event?.organizer
                )
            }

            Barcode.TYPE_DRIVER_LICENSE -> {
                val license = barcode.driverLicense
                ParsedContent.DriversLicense(
                    firstName = license?.firstName,
                    lastName = license?.lastName,
                    middleName = license?.middleName,
                    gender = license?.gender,
                    birthDate = license?.birthDate,
                    addressStreet = license?.addressStreet,
                    addressCity = license?.addressCity,
                    addressState = license?.addressState,
                    addressZip = license?.addressZip,
                    licenseNumber = license?.licenseNumber,
                    issueDate = license?.issueDate,
                    expiryDate = license?.expiryDate,
                    issuingCountry = license?.issuingCountry
                )
            }

            Barcode.TYPE_ISBN -> {
                ParsedContent.Isbn(isbn = barcode.rawValue ?: "")
            }

            Barcode.TYPE_PRODUCT -> {
                ParsedContent.Product(
                    code = barcode.rawValue ?: "",
                    format = BarcodeFormat.fromMlKit(barcode.format)
                )
            }

            Barcode.TYPE_TEXT -> {
                ParsedContent.Text(text = barcode.rawValue ?: "")
            }

            else -> {
                ParsedContent.Unknown(rawValue = barcode.rawValue ?: "")
            }
        }
    }

    /**
     * Parses a raw string value into a ParsedContent object.
     * Used when the barcode type is unknown or for manual parsing.
     */
    fun parseRawContent(rawValue: String): ParsedContent {
        return when {
            // URL detection
            rawValue.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE)) -> {
                ParsedContent.Url(url = rawValue)
            }

            // WiFi detection (WIFI:T:WPA;S:MyNetwork;P:MyPassword;;)
            rawValue.startsWith("WIFI:", ignoreCase = true) -> {
                parseWifiString(rawValue)
            }

            // Email detection
            rawValue.matches(Regex("^mailto:.*", RegexOption.IGNORE_CASE)) -> {
                parseMailtoString(rawValue)
            }

            // Phone detection
            rawValue.matches(Regex("^tel:.*", RegexOption.IGNORE_CASE)) -> {
                ParsedContent.Phone(number = rawValue.removePrefix("tel:"))
            }

            // SMS detection
            rawValue.matches(Regex("^sms:.*", RegexOption.IGNORE_CASE)) -> {
                parseSmsString(rawValue)
            }

            // Geo detection
            rawValue.matches(Regex("^geo:.*", RegexOption.IGNORE_CASE)) -> {
                parseGeoString(rawValue)
            }

            // vCard detection
            rawValue.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                parseVCardString(rawValue)
            }

            else -> ParsedContent.Text(text = rawValue)
        }
    }

    private fun parseWifiString(value: String): ParsedContent.Wifi {
        val ssidMatch = Regex("S:([^;]*);").find(value)
        val passwordMatch = Regex("P:([^;]*);").find(value)
        val typeMatch = Regex("T:([^;]*);").find(value)

        val encryptionType = when (typeMatch?.groupValues?.get(1)?.uppercase()) {
            "WEP" -> WifiEncryption.WEP
            "WPA" -> WifiEncryption.WPA
            "WPA2" -> WifiEncryption.WPA2
            "WPA3" -> WifiEncryption.WPA3
            "NOPASS", "" -> WifiEncryption.OPEN
            else -> WifiEncryption.UNKNOWN
        }

        return ParsedContent.Wifi(
            ssid = ssidMatch?.groupValues?.get(1) ?: "",
            password = passwordMatch?.groupValues?.get(1),
            encryptionType = encryptionType
        )
    }

    private fun parseMailtoString(value: String): ParsedContent.Email {
        val uri = Uri.parse(value)
        return ParsedContent.Email(
            address = uri.schemeSpecificPart?.substringBefore("?") ?: "",
            subject = uri.getQueryParameter("subject"),
            body = uri.getQueryParameter("body")
        )
    }

    private fun parseSmsString(value: String): ParsedContent.Sms {
        val uri = Uri.parse(value)
        return ParsedContent.Sms(
            phoneNumber = uri.schemeSpecificPart?.substringBefore("?") ?: "",
            message = uri.getQueryParameter("body")
        )
    }

    private fun parseGeoString(value: String): ParsedContent.GeoPoint {
        val coords = value.removePrefix("geo:").split(",")
        return ParsedContent.GeoPoint(
            latitude = coords.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
            longitude = coords.getOrNull(1)?.substringBefore("?")?.toDoubleOrNull() ?: 0.0
        )
    }

    private fun parseVCardString(value: String): ParsedContent.Contact {
        // Simple vCard parsing - for full parsing consider using a library
        val nameMatch = Regex("FN:(.*)").find(value)
        val orgMatch = Regex("ORG:(.*)").find(value)
        val telMatches = Regex("TEL[^:]*:(.*)").findAll(value)
        val emailMatches = Regex("EMAIL[^:]*:(.*)").findAll(value)

        return ParsedContent.Contact(
            name = nameMatch?.groupValues?.get(1)?.trim(),
            organization = orgMatch?.groupValues?.get(1)?.trim(),
            title = null,
            phones = telMatches.map {
                ParsedContent.Contact.Phone(
                    number = it.groupValues[1].trim(),
                    type = ParsedContent.Contact.PhoneType.UNKNOWN
                )
            }.toList(),
            emails = emailMatches.map {
                ParsedContent.Contact.Email(
                    address = it.groupValues[1].trim(),
                    type = ParsedContent.Contact.EmailType.UNKNOWN
                )
            }.toList(),
            addresses = emptyList(),
            urls = emptyList()
        )
    }

    private fun parseCalendarDateTime(value: String): Long? {
        return try {
            // Parse iCalendar date-time format (YYYYMMDDTHHMMSS or YYYYMMDD)
            val formatter = if (value.contains("T")) {
                java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US)
            } else {
                java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            }
            formatter.parse(value)?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the content type description for display.
     */
    fun getContentTypeDescription(content: ParsedContent): String {
        return when (content) {
            is ParsedContent.Text -> "Text"
            is ParsedContent.Url -> "URL"
            is ParsedContent.Wifi -> "WiFi"
            is ParsedContent.Contact -> "Contact"
            is ParsedContent.Email -> "Email"
            is ParsedContent.Phone -> "Phone"
            is ParsedContent.Sms -> "SMS"
            is ParsedContent.GeoPoint -> "Location"
            is ParsedContent.CalendarEvent -> "Event"
            is ParsedContent.DriversLicense -> "License"
            is ParsedContent.Isbn -> "ISBN"
            is ParsedContent.Product -> "Product"
            is ParsedContent.Unknown -> "Unknown"
        }
    }

    /**
     * Closes the barcode scanner when no longer needed.
     */
    fun close() {
        barcodeScanner.close()
    }
}
