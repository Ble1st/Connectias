package com.ble1st.connectias.feature.network.ssl

import com.ble1st.connectias.feature.network.model.SslReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant

/**
 * Rust-based SSL certificate analyzer.
 * 
 * Note: TLS handshake remains in Kotlin (Android SSLContext works well).
 * Rust is used for certificate parsing if needed in the future.
 */
class RustSslAnalyzer {
    
    companion object {
        private var libraryLoaded = false
        init {
            try {
                System.loadLibrary("connectias_port_scanner")
                libraryLoaded = true
                Timber.d("Rust SSL analyzer library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to load Rust SSL analyzer library, will use Kotlin fallback")
                libraryLoaded = false
            }
        }
    }

    /**
     * Native method to analyze certificate using Rust implementation.
     * 
     * @param certDer Certificate DER bytes
     * @param hostname Hostname for verification
     * @return JSON string with SslAnalysisResult
     */
    private external fun nativeAnalyzeCertificate(certDer: ByteArray, hostname: String): String

    /**
     * Initialize Rust logging (Android-specific)
     */
    private external fun nativeInit()

    private var isInitialized = false

    private fun ensureInitialized() {
        if (!isInitialized && libraryLoaded) {
            try {
                nativeInit()
                isInitialized = true
            } catch (e: UnsatisfiedLinkError) {
                Timber.w(e, "Failed to initialize Rust logging (non-critical) - function not found")
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize Rust logging (non-critical)")
            }
        }
    }

    /**
     * Analyze SSL certificate.
     * 
     * Note: Currently uses Kotlin implementation as primary.
     * Rust implementation can be added for certificate parsing if needed.
     */
    suspend fun analyzeCertificate(
        certificate: X509Certificate,
        hostname: String
    ): SslReport = withContext(Dispatchers.Default) {
        // For now, use Kotlin implementation
        // Rust can be added later for certificate parsing if needed
        val now = Instant.now()
        val validFrom = certificate.notBefore.toInstant()
        val validTo = certificate.notAfter.toInstant()
        val daysRemaining = Duration.between(now, validTo).toDays()
        val isValidNow = now.isAfter(validFrom) && now.isBefore(validTo)
        
        val keyAlgorithm = certificate.publicKey.algorithm
        val keySize = when (val key = certificate.publicKey) {
            is java.security.interfaces.RSAPublicKey -> key.modulus.bitLength()
            is java.security.interfaces.ECPublicKey -> key.params.curve.field.fieldSize
            else -> certificate.publicKey.encoded.size * 8
        }
        val signatureAlgorithm = certificate.sigAlgName
        
        val problems = mutableListOf<String>()
        if (!isValidNow) problems.add("Certificate is not valid (time period)")
        
        SslReport(
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            validFrom = validFrom,
            validTo = validTo,
            daysRemaining = daysRemaining,
            isValidNow = isValidNow,
            keyAlgorithm = keyAlgorithm,
            keySize = keySize,
            signatureAlgorithm = signatureAlgorithm,
            problems = problems
        )
    }
}

