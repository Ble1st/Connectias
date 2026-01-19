package com.ble1st.connectias.core.security.ssl

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.CertificatePinner
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SSL/TLS certificate pinning for secure network connections.
 *
 * This class provides:
 * - Certificate pinning configuration
 * - OkHttpClient with pinning enabled
 * - Manual certificate validation
 * - Pin management (add, remove, update)
 */
@Singleton
class SslPinningManager @Inject constructor(
) {
    private val pins = ConcurrentHashMap<String, CertificatePin>()
    private var cachedOkHttpClient: OkHttpClient? = null
    private val clientLock = Any()

    init {
        // Load default pins
        // TODO: Provide defaults from secure remote configuration if needed
    }

    /**
     * Adds or updates a certificate pin.
     */
    fun addPin(pin: CertificatePin) {
        pins[pin.hostname] = pin
        invalidateClient()
    }

    /**
     * Removes a pin by hostname.
     */
    fun removePin(hostname: String) {
        pins.remove(hostname)
        invalidateClient()
    }

    /**
     * Clears all configured pins.
     */
    fun clearPins() {
        pins.clear()
        invalidateClient()
    }

    /**
     * Returns a cached OkHttpClient with certificate pinning enabled when pins exist.
     */
    fun getOkHttpClient(): OkHttpClient {
        cachedOkHttpClient?.let { return it }

        synchronized(clientLock) {
            cachedOkHttpClient?.let { return it }

            val activePins = pins.values.filterNot { it.isExpired() }
            val builder = OkHttpClient.Builder()

            if (activePins.isNotEmpty()) {
                val certificatePinner = CertificatePinner.Builder().apply {
                    activePins.forEach { pin ->
                        val hostnames = resolvePinnedHostnames(pin)
                        hostnames.forEach { hostname ->
                            pin.pins.forEach { hash ->
                                add(hostname, hash)
                            }
                        }
                    }
                }.build()

                builder.certificatePinner(certificatePinner)
            }

            return builder.build().also { cachedOkHttpClient = it }
        }
    }

    /**
     * Validates a certificate chain against configured pins for the hostname.
     */
    fun validateCertificateChain(
        hostname: String,
        certificateChain: List<X509Certificate>
    ): PinValidationResult {
        return try {
            val pin = findPinForHostname(hostname) ?: return PinValidationResult.NoPinsConfigured
            if (pin.isExpired()) return PinValidationResult.PinExpired

            val validPins = pin.pins.toSet()
            val matches = certificateChain.any { certificate ->
                val hash = calculatePublicKeyHash(certificate)
                validPins.contains("sha256/$hash")
            }

            if (matches) PinValidationResult.Valid else PinValidationResult.Invalid
        } catch (e: Exception) {
            PinValidationResult.Error
        }
    }

    /**
     * Finds the matching pin for a hostname.
     */
    private fun findPinForHostname(hostname: String): CertificatePin? {
        // Exact match first
        pins[hostname]?.let { return it }

        // Check for matching pins (including subdomains and wildcards)
        return pins.values.find { it.matchesHostname(hostname) }
    }

    /**
     * Calculates the SHA-256 hash of a certificate's public key.
     */
    fun calculatePublicKeyHash(certificate: X509Certificate): String {
        val publicKey = certificate.publicKey.encoded
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun resolvePinnedHostnames(pin: CertificatePin): Set<String> {
        if (!pin.includeSubdomains) return setOf(pin.hostname)
        if (pin.hostname.startsWith("*.")) return setOf(pin.hostname)

        return setOf(pin.hostname, "*.${pin.hostname}")
    }

    private fun invalidateClient() {
        synchronized(clientLock) {
            cachedOkHttpClient = null
        }
    }

    companion object
}

/**
 * Certificate information for debugging and display.
 */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val notBefore: Long,
    val notAfter: Long,
    val publicKeyHash: String,
    val signatureAlgorithm: String
) {

}
