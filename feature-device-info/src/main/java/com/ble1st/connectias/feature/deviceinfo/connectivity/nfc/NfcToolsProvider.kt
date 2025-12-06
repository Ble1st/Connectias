package com.ble1st.connectias.feature.deviceinfo.connectivity.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NdefRecordInfo
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NdefRecordType
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NfcEvent
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NfcTagInfo
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NfcTagType
import com.ble1st.connectias.feature.deviceinfo.connectivity.models.NfcWriteResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.charset.Charset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for NFC functionality.
 *
 * Features:
 * - NFC tag reading
 * - NDEF message writing
 * - Tag formatting
 * - Various NDEF record types (URI, Text, vCard, WiFi)
 */
@Singleton
class NfcToolsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    private val _events = MutableSharedFlow<NfcEvent>(replay = 1)
    val events: SharedFlow<NfcEvent> = _events.asSharedFlow()

    private val _isEnabled = MutableStateFlow(nfcAdapter?.isEnabled ?: false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentTag = MutableStateFlow<NfcTagInfo?>(null)
    val currentTag: StateFlow<NfcTagInfo?> = _currentTag.asStateFlow()

    private var pendingTag: Tag? = null

    /**
     * Checks if NFC is available on this device.
     */
    fun isNfcAvailable(): Boolean = nfcAdapter != null

    /**
     * Checks if NFC is enabled.
     */
    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled ?: false

    /**
     * Updates the enabled state.
     */
    fun updateEnabledState() {
        _isEnabled.value = nfcAdapter?.isEnabled ?: false
    }

    /**
     * Enables foreground dispatch for NFC discovery.
     */
    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return

        val intent = Intent(activity, activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, pendingIntentFlags)

        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        val techList = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name),
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name)
        )

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList)
    }

    /**
     * Disables foreground dispatch.
     */
    fun disableForegroundDispatch(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    /**
     * Processes an NFC intent.
     */
    suspend fun processIntent(intent: Intent): NfcTagInfo? = withContext(Dispatchers.IO) {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return@withContext null

        pendingTag = tag
        val tagInfo = readTag(tag)
        _currentTag.value = tagInfo
        _events.emit(NfcEvent.TagDiscovered(tagInfo))
        tagInfo
    }

    /**
     * Reads NFC tag information.
     */
    suspend fun readTag(tag: Tag): NfcTagInfo = withContext(Dispatchers.IO) {
        val id = tag.id.toHexString()
        val techList = tag.techList.toList()
        val type = determineTagType(techList)

        var maxSize = 0
        var isWritable = false
        var canMakeReadOnly = false
        val ndefRecords = mutableListOf<NdefRecordInfo>()

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                maxSize = ndef.maxSize
                isWritable = ndef.isWritable
                canMakeReadOnly = ndef.canMakeReadOnly()

                ndef.ndefMessage?.records?.forEach { record ->
                    ndefRecords.add(parseNdefRecord(record))
                }

                ndef.close()
            } catch (e: Exception) {
                Timber.e(e, "Error reading NDEF tag")
            }
        } else {
            val ndefFormatable = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                isWritable = true
            }
        }

        NfcTagInfo(
            id = id,
            techList = techList,
            type = type,
            maxSize = maxSize,
            isWritable = isWritable,
            canMakeReadOnly = canMakeReadOnly,
            ndefRecords = ndefRecords
        )
    }

    /**
     * Writes NDEF message to tag.
     */
    suspend fun writeNdefMessage(message: NdefMessage): NfcWriteResult = withContext(Dispatchers.IO) {
        val tag = pendingTag ?: return@withContext NfcWriteResult.TagLost

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()

                if (!ndef.isWritable) {
                    ndef.close()
                    return@withContext NfcWriteResult.TagNotWritable
                }

                if (message.toByteArray().size > ndef.maxSize) {
                    ndef.close()
                    return@withContext NfcWriteResult.TagTooSmall
                }

                ndef.writeNdefMessage(message)
                val bytesWritten = message.toByteArray().size
                ndef.close()

                _events.emit(NfcEvent.WriteSuccess(bytesWritten))
                NfcWriteResult.Success(bytesWritten)
            } catch (e: Exception) {
                Timber.e(e, "Error writing NDEF message")
                NfcWriteResult.Error(e.message ?: "Unknown error", e)
            }
        } else {
            val ndefFormatable = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                try {
                    ndefFormatable.connect()
                    ndefFormatable.format(message)
                    val bytesWritten = message.toByteArray().size
                    ndefFormatable.close()

                    _events.emit(NfcEvent.WriteSuccess(bytesWritten))
                    NfcWriteResult.Success(bytesWritten)
                } catch (e: Exception) {
                    Timber.e(e, "Error formatting tag")
                    NfcWriteResult.Error(e.message ?: "Unknown error", e)
                }
            } else {
                NfcWriteResult.TagNotWritable
            }
        }
    }

    /**
     * Creates a URI record.
     */
    fun createUriRecord(uri: String): NdefRecord {
        return NdefRecord.createUri(uri)
    }

    /**
     * Creates a text record.
     */
    fun createTextRecord(text: String, locale: Locale = Locale.getDefault()): NdefRecord {
        val langBytes = locale.language.toByteArray(Charset.forName("US-ASCII"))
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))
        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0] = langBytes.size.toByte()
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
    }

    /**
     * Creates a WiFi configuration record.
     */
    fun createWifiRecord(ssid: String, password: String?, authType: String = "WPA"): NdefRecord {
        val wifiString = buildString {
            append("WIFI:")
            append("T:$authType;")
            append("S:$ssid;")
            password?.let { append("P:$it;") }
            append(";")
        }
        return NdefRecord.createMime("application/vnd.wfa.wsc", wifiString.toByteArray())
    }

    /**
     * Creates a vCard record.
     */
    fun createVCardRecord(
        name: String,
        phone: String? = null,
        email: String? = null,
        organization: String? = null
    ): NdefRecord {
        val vcard = buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$name")
            phone?.let { appendLine("TEL:$it") }
            email?.let { appendLine("EMAIL:$it") }
            organization?.let { appendLine("ORG:$it") }
            appendLine("END:VCARD")
        }
        return NdefRecord.createMime("text/vcard", vcard.toByteArray())
    }

    /**
     * Creates an NDEF message from records.
     */
    fun createNdefMessage(vararg records: NdefRecord): NdefMessage {
        return NdefMessage(records)
    }

    /**
     * Formats a tag with an empty NDEF message.
     */
    suspend fun formatTag(): NfcWriteResult = withContext(Dispatchers.IO) {
        val tag = pendingTag ?: return@withContext NfcWriteResult.TagLost

        val ndefFormatable = NdefFormatable.get(tag)
            ?: return@withContext NfcWriteResult.TagNotWritable

        try {
            ndefFormatable.connect()
            val emptyRecord = NdefRecord(NdefRecord.TNF_EMPTY, ByteArray(0), ByteArray(0), ByteArray(0))
            ndefFormatable.format(NdefMessage(arrayOf(emptyRecord)))
            ndefFormatable.close()
            NfcWriteResult.Success(0)
        } catch (e: Exception) {
            Timber.e(e, "Error formatting tag")
            NfcWriteResult.Error(e.message ?: "Unknown error", e)
        }
    }

    /**
     * Makes a tag read-only.
     */
    suspend fun makeTagReadOnly(): NfcWriteResult = withContext(Dispatchers.IO) {
        val tag = pendingTag ?: return@withContext NfcWriteResult.TagLost

        val ndef = Ndef.get(tag) ?: return@withContext NfcWriteResult.TagNotWritable

        try {
            ndef.connect()
            if (!ndef.canMakeReadOnly()) {
                ndef.close()
                return@withContext NfcWriteResult.Error("Tag cannot be made read-only")
            }
            ndef.makeReadOnly()
            ndef.close()
            NfcWriteResult.Success(0)
        } catch (e: Exception) {
            Timber.e(e, "Error making tag read-only")
            NfcWriteResult.Error(e.message ?: "Unknown error", e)
        }
    }

    /**
     * Clears the current tag.
     */
    fun clearCurrentTag() {
        pendingTag = null
        _currentTag.value = null
    }

    private fun determineTagType(techList: List<String>): NfcTagType {
        return when {
            techList.contains(Ndef::class.java.name) -> NfcTagType.NDEF
            techList.contains(NdefFormatable::class.java.name) -> NfcTagType.NDEF_FORMATABLE
            techList.contains("android.nfc.tech.MifareClassic") -> NfcTagType.MIFARE_CLASSIC
            techList.contains("android.nfc.tech.MifareUltralight") -> NfcTagType.MIFARE_ULTRALIGHT
            techList.contains(NfcA::class.java.name) -> NfcTagType.NFC_A
            techList.contains(NfcB::class.java.name) -> NfcTagType.NFC_B
            techList.contains(NfcF::class.java.name) -> NfcTagType.NFC_F
            techList.contains(NfcV::class.java.name) -> NfcTagType.NFC_V
            else -> NfcTagType.UNKNOWN
        }
    }

    private fun parseNdefRecord(record: NdefRecord): NdefRecordInfo {
        val recordType = when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && 
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> NdefRecordType.TEXT
            record.tnf == NdefRecord.TNF_WELL_KNOWN && 
                record.type.contentEquals(NdefRecord.RTD_URI) -> NdefRecordType.URI
            record.tnf == NdefRecord.TNF_WELL_KNOWN && 
                record.type.contentEquals(NdefRecord.RTD_SMART_POSTER) -> NdefRecordType.SMART_POSTER
            record.tnf == NdefRecord.TNF_MIME_MEDIA -> NdefRecordType.MIME
            record.tnf == NdefRecord.TNF_EXTERNAL_TYPE -> NdefRecordType.EXTERNAL
            else -> NdefRecordType.UNKNOWN
        }

        val payload = when (recordType) {
            NdefRecordType.TEXT -> parseTextPayload(record.payload)
            NdefRecordType.URI -> record.toUri()?.toString() ?: ""
            else -> record.payload.toHexString()
        }

        return NdefRecordInfo(
            tnf = record.tnf.toInt(),
            type = record.type.decodeToString(),
            payload = payload,
            recordType = recordType
        )
    }

    private fun parseTextPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val langLength = payload[0].toInt() and 0x3F
        return String(payload, 1 + langLength, payload.size - 1 - langLength, Charsets.UTF_8)
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }
}
