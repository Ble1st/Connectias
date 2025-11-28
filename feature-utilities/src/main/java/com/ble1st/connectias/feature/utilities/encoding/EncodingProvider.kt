package com.ble1st.connectias.feature.utilities.encoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for encoding and decoding operations.
 * Supports Base64, Base32, Hex, URL, HTML Entity, and Unicode encoding/decoding.
 */
@Singleton
class EncodingProvider @Inject constructor() {

    /**
     * Supported encoding types.
     */
    enum class EncodingType {
        BASE64,
        BASE32,
        HEX,
        URL,
        HTML_ENTITY,
        UNICODE
    }

    /**
     * Encodes text using the specified encoding type.
     */
    suspend fun encode(text: String, encodingType: EncodingType): String? = withContext(Dispatchers.IO) {
        try {
            when (encodingType) {
                EncodingType.BASE64 -> {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
                EncodingType.BASE32 -> {
                    // Base32 encoding implementation
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    base32Encode(bytes)
                }
                EncodingType.HEX -> {
                    text.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
                }
                EncodingType.URL -> {
                    URLEncoder.encode(text, "UTF-8")
                }
                EncodingType.HTML_ENTITY -> {
                    htmlEntityEncode(text)
                }
                EncodingType.UNICODE -> {
                    text.map { "\\u%04x".format(it.code) }.joinToString("")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to encode text with encoding type ${encodingType.name}")
            null
        }
    }

    /**
     * Decodes text using the specified encoding type.
     */
    suspend fun decode(text: String, encodingType: EncodingType): String? = withContext(Dispatchers.IO) {
        try {
            when (encodingType) {
                EncodingType.BASE64 -> {
                    val bytes = android.util.Base64.decode(text, android.util.Base64.NO_WRAP)
                    String(bytes, Charsets.UTF_8)
                }
                EncodingType.BASE32 -> {
                    val bytes = base32Decode(text)
                    String(bytes, Charsets.UTF_8)
                }
                EncodingType.HEX -> {
                    val bytes = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    String(bytes, Charsets.UTF_8)
                }
                EncodingType.URL -> {
                    URLDecoder.decode(text, "UTF-8")
                }
                EncodingType.HTML_ENTITY -> {
                    htmlEntityDecode(text)
                }
                EncodingType.UNICODE -> {
                    unicodeDecode(text)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode text with encoding type ${encodingType.name}")
            null
        }
    }

    /**
     * Base32 encoding implementation.
     */
    private fun base32Encode(bytes: ByteArray): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0

        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8

            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                result.append(base32Chars[index])
                bitsLeft -= 5
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            result.append(base32Chars[index])
        }

        return result.toString()
    }

    /**
     * Base32 decoding implementation.
     */
    private fun base32Decode(encoded: String): ByteArray {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val upperEncoded = encoded.uppercase().replace(" ", "")
        val result = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (char in upperEncoded) {
            val index = base32Chars.indexOf(char)
            if (index == -1) continue

            buffer = (buffer shl 5) or index
            bitsLeft += 5

            if (bitsLeft >= 8) {
                result.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }

        return result.toByteArray()
    }

    /**
     * HTML entity encoding.
     */
    private fun htmlEntityEncode(text: String): String {
        return text.map { char ->
            when (char.code) {
                in 0..127 -> char.toString()
                else -> "&#${char.code};"
            }
        }.joinToString("")
    }

    /**
     * HTML entity decoding.
     */
    private fun htmlEntityDecode(text: String): String {
        val pattern = Regex("&#(\\d+);")
        return pattern.replace(text) { matchResult ->
            val code = matchResult.groupValues[1].toInt()
            code.toChar().toString()
        }
    }

    /**
     * Unicode decoding.
     */
    private fun unicodeDecode(text: String): String {
        val pattern = Regex("\\\\u([0-9a-fA-F]{4})")
        return pattern.replace(text) { matchResult ->
            val code = matchResult.groupValues[1].toInt(16)
            code.toChar().toString()
        }
    }
}

