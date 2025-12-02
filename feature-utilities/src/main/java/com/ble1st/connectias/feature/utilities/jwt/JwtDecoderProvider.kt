package com.ble1st.connectias.feature.utilities.jwt

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for JWT decoding and analysis functionality.
 */
@Singleton
class JwtDecoderProvider @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Decodes a JWT token.
     */
    fun decodeToken(token: String): JwtToken {
        return try {
            val parts = token.split(".")
            if (parts.size < 2 || parts.size > 3) {
                return JwtToken(
                    rawToken = token,
                    isValid = false,
                    error = "Invalid JWT format: expected 2 or 3 parts, got ${parts.size}"
                )
            }

            val header = decodeBase64Url(parts[0])
            val payload = decodeBase64Url(parts[1])
            val signature = if (parts.size == 3) parts[2] else null

            val headerJson = json.parseToJsonElement(header).jsonObject
            val payloadJson = json.parseToJsonElement(payload).jsonObject

            // Extract common claims
            val exp = payloadJson["exp"]?.jsonPrimitive?.long
            val iat = payloadJson["iat"]?.jsonPrimitive?.long
            val nbf = payloadJson["nbf"]?.jsonPrimitive?.long
            val sub = payloadJson["sub"]?.jsonPrimitive?.content
            val iss = payloadJson["iss"]?.jsonPrimitive?.content
            val aud = payloadJson["aud"]?.jsonPrimitive?.content

            val isExpired = exp?.let { it * 1000 < System.currentTimeMillis() } ?: false
            val isNotYetValid = nbf?.let { it * 1000 > System.currentTimeMillis() } ?: false

            JwtToken(
                rawToken = token,
                header = headerJson,
                payload = payloadJson,
                signature = signature,
                algorithm = headerJson["alg"]?.jsonPrimitive?.content,
                type = headerJson["typ"]?.jsonPrimitive?.content,
                expirationTime = exp?.let { Instant.ofEpochSecond(it) },
                issuedAt = iat?.let { Instant.ofEpochSecond(it) },
                notBefore = nbf?.let { Instant.ofEpochSecond(it) },
                subject = sub,
                issuer = iss,
                audience = aud,
                isExpired = isExpired,
                isNotYetValid = isNotYetValid,
                isValid = !isExpired && !isNotYetValid
            )
        } catch (e: Exception) {
            Timber.e(e, "Error decoding JWT")
            JwtToken(
                rawToken = token,
                isValid = false,
                error = "Decode error: ${e.message}"
            )
        }
    }

    /**
     * Checks if a token is expired.
     */
    fun isExpired(token: JwtToken): Boolean = token.isExpired

    /**
     * Gets the expiration time.
     */
    fun getExpirationTime(token: JwtToken): Instant? = token.expirationTime

    /**
     * Decodes base64url encoded string.
     */
    private fun decodeBase64Url(encoded: String): String {
        val padded = when (encoded.length % 4) {
            2 -> "$encoded=="
            3 -> "$encoded="
            else -> encoded
        }
        val base64 = padded.replace('-', '+').replace('_', '/')
        val decoded = Base64.decode(base64, Base64.DEFAULT)
        return String(decoded, StandardCharsets.UTF_8)
    }

    /**
     * Formats JWT payload as pretty JSON.
     */
    fun formatPayload(token: JwtToken): String {
        return try {
            val prettyJson = Json { prettyPrint = true }
            token.payload?.let { prettyJson.encodeToString(JsonObject.serializer(), it) } ?: ""
        } catch (e: Exception) {
            token.payload?.toString() ?: ""
        }
    }

    /**
     * Gets common JWT patterns.
     */
    fun getCommonPatterns(): List<JwtPattern> = listOf(
        JwtPattern("Access Token", "Short-lived token for API access", "exp, sub, scope"),
        JwtPattern("Refresh Token", "Long-lived token to get new access tokens", "exp, sub, jti"),
        JwtPattern("ID Token", "OpenID Connect identity token", "sub, iss, aud, exp, iat, nonce"),
        JwtPattern("Service Token", "Machine-to-machine authentication", "iss, sub, aud, exp")
    )
}

/**
 * Decoded JWT token.
 */
@Serializable
data class JwtToken(
    val rawToken: String,
    val header: JsonObject? = null,
    val payload: JsonObject? = null,
    val signature: String? = null,
    val algorithm: String? = null,
    val type: String? = null,
    @Serializable(with = InstantSerializer::class)
    val expirationTime: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val issuedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val notBefore: Instant? = null,
    val subject: String? = null,
    val issuer: String? = null,
    val audience: String? = null,
    val isExpired: Boolean = false,
    val isNotYetValid: Boolean = false,
    val isValid: Boolean = true,
    val error: String? = null
)

/**
 * Common JWT pattern.
 */
data class JwtPattern(
    val name: String,
    val description: String,
    val typicalClaims: String
)

/**
 * Serializer for Instant.
 */
object InstantSerializer : kotlinx.serialization.KSerializer<Instant> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Instant", 
        kotlinx.serialization.descriptors.PrimitiveKind.LONG
    )
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) {
        encoder.encodeLong(value.epochSecond)
    }
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Instant {
        return Instant.ofEpochSecond(decoder.decodeLong())
    }
}
