package com.ble1st.connectias.core.security.ssl

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import timber.log.Timber
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

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
    @ApplicationContext private val context: Context
) {
    private val pins = ConcurrentHashMap<String, CertificatePin>()
    private var cachedOkHttpClient: OkHttpClient? = null
    private val clientLock = Any()

    init {
        // Load default pins
        loadDefaultPins()
    }

    /**
     * Loads default certificate pins for known services.
     */
    private fun loadDefaultPins() {
        // Example pins - these should be updated with actual certificate hashes
        // In production, these would be loaded from a secure configuration
        
        // Note: These are placeholder pins. Real pins must be generated from actual certificates.
        // Use: openssl s_client -connect host:443 | openssl x509 -pubkey -noout | 
        //      openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
    }

    /**
     * Adds a certificate pin for a hostname.
     */
    fun addPin(pin: CertificatePin) {
        pins[pin.hostname] = pin
        invalidateClient()
        Timber.d("Added certificate pin for ${pin.hostname}")
    }

    /**
     * Adds a certificate pin for a hostname with SHA-256 hashes.
     */
    fun addPin(hostname: String, vararg sha256Pins: String, includeSubdomains: Boolean = true) {
        val pin = CertificatePin(
            hostname = hostname,
            pins = sha256Pins.toList(),
            includeSubdomains = includeSubdomains
        )
        addPin(pin)
    }

    /**
     * Removes the certificate pin for a hostname.
     */
    fun removePin(hostname: String) {
        pins.remove(hostname)
        invalidateClient()
        Timber.d("Removed certificate pin for $hostname")
    }

    /**
     * Gets all configured pins.
     */
    fun getPins(): List<CertificatePin> = pins.values.toList()

    /**
     * Checks if pinning is enabled for a hostname.
     */
    fun isPinningEnabled(hostname: String): Boolean {
        return findPinForHostname(hostname) != null
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
     * Invalidates the cached OkHttpClient to force recreation with new pins.
     */
    private fun invalidateClient() {
        synchronized(clientLock) {
            cachedOkHttpClient = null
        }
    }

    /**
     * Creates an OkHttpClient with certificate pinning enabled.
     */
    fun getOkHttpClient(
        connectTimeout: Long = 30,
        readTimeout: Long = 30,
        writeTimeout: Long = 30
    ): OkHttpClient {
        synchronized(clientLock) {
            cachedOkHttpClient?.let { return it }

            val builder = OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)

            // Add certificate pinning if any pins are configured
            if (pins.isNotEmpty()) {
                val pinnerBuilder = CertificatePinner.Builder()
                
                pins.values.forEach { pin ->
                    if (!pin.isExpired()) {
                        pin.pins.forEach { pinHash ->
                            pinnerBuilder.add(pin.hostname, pinHash)
                        }
                    }
                }
                
                builder.certificatePinner(pinnerBuilder.build())
            }

            // Add pinning interceptor for additional validation
            builder.addInterceptor(PinningInterceptor(this))

            val client = builder.build()
            cachedOkHttpClient = client
            return client
        }
    }

    /**
     * Validates a certificate chain against configured pins.
     */
    fun validateCertificateChain(
        hostname: String,
        chain: Array<out Certificate>
    ): PinValidationResult {
        return try {
            val pin = findPinForHostname(hostname)
                ?: return PinValidationResult.NoPinsConfigured

            if (pin.isExpired()) {
                return PinValidationResult.PinExpired(hostname)
            }

            val chainHashes = chain.mapNotNull { cert ->
                if (cert is X509Certificate) {
                    calculatePublicKeyHash(cert)
                } else null
            }

            // Check if any certificate in the chain matches a pin
            for (hash in chainHashes) {
                val matchingPin = pin.pins.find { configuredPin ->
                    val pinHash = if (configuredPin.startsWith("sha256/")) {
                        configuredPin.substring(7)
                    } else {
                        configuredPin
                    }
                    pinHash == hash
                }

                if (matchingPin != null) {
                    return PinValidationResult.Valid(matchingPin)
                }
            }

            PinValidationResult.Invalid(
                reason = "No certificate in chain matches configured pins",
                certificateChain = chainHashes
            )
        } catch (e: Exception) {
            Timber.e(e, "Error validating certificate chain for $hostname")
            PinValidationResult.Error(e)
        }
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

    /**
     * Extracts certificate information for debugging.
     */
    fun getCertificateInfo(certificate: X509Certificate): CertificateInfo {
        return CertificateInfo(
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            serialNumber = certificate.serialNumber.toString(16),
            notBefore = certificate.notBefore.time,
            notAfter = certificate.notAfter.time,
            publicKeyHash = calculatePublicKeyHash(certificate),
            signatureAlgorithm = certificate.sigAlgName
        )
    }

    /**
     * Gets the default TrustManager for system certificates.
     */
    fun getDefaultTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as java.security.KeyStore?)
        
        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }

    /**
     * Creates an SSLContext with the default trust manager.
     */
    fun createSslContext(): SSLContext {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(getDefaultTrustManager()), null)
        return sslContext
    }

    companion object {
        private const val TAG = "SslPinningManager"
    }
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
    /**
     * Checks if the certificate is currently valid.
     */
    fun isValid(): Boolean {
        val now = System.currentTimeMillis()
        return now in notBefore..notAfter
    }

    /**
     * Gets the number of days until expiration.
     */
    fun daysUntilExpiration(): Long {
        val now = System.currentTimeMillis()
        return (notAfter - now) / (24 * 60 * 60 * 1000)
    }
}
