package com.ble1st.connectias.feature.security.certgen

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import javax.security.auth.x500.X500Principal

/**
 * Provider for certificate generation functionality.
 *
 * Features:
 * - Self-signed certificate generation
 * - CSR (Certificate Signing Request) creation
 * - Key pair generation (RSA, EC)
 * - Certificate export (PEM, DER)
 */
@Singleton
class CertificateGeneratorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Generates a self-signed certificate.
     */
    suspend fun generateSelfSignedCertificate(
        config: CertificateConfig
    ): CertificateResult = withContext(Dispatchers.Default) {
        try {
            // Generate key pair
            val keyPair = generateKeyPair(config.keyAlgorithm, config.keySize)

            // Build certificate
            val certificate = buildSelfSignedCertificate(
                keyPair = keyPair,
                subjectDn = config.subjectDn,
                validityDays = config.validityDays,
                signatureAlgorithm = config.signatureAlgorithm
            )

            val pemCert = exportToPem(certificate)
            val pemKey = exportPrivateKeyToPem(keyPair.private)

            CertificateResult.Success(
                certificate = pemCert,
                privateKey = pemKey,
                publicKey = exportPublicKeyToPem(keyPair.public),
                serialNumber = certificate.serialNumber.toString(),
                fingerprint = calculateFingerprint(certificate),
                validFrom = certificate.notBefore.time,
                validUntil = certificate.notAfter.time
            )
        } catch (e: Exception) {
            Timber.e(e, "Error generating self-signed certificate")
            CertificateResult.Error("Failed to generate certificate: ${e.message}", e)
        }
    }

    /**
     * Generates a Certificate Signing Request (CSR).
     */
    suspend fun generateCSR(
        config: CsrConfig
    ): CsrResult = withContext(Dispatchers.Default) {
        try {
            val keyPair = generateKeyPair(config.keyAlgorithm, config.keySize)
            val csr = buildCsr(keyPair, config.subjectDn, config.signatureAlgorithm)

            CsrResult.Success(
                csr = csr,
                privateKey = exportPrivateKeyToPem(keyPair.private),
                publicKey = exportPublicKeyToPem(keyPair.public)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error generating CSR")
            CsrResult.Error("Failed to generate CSR: ${e.message}", e)
        }
    }

    /**
     * Generates a key pair.
     */
    suspend fun generateKeyPair(
        algorithm: KeyAlgorithm = KeyAlgorithm.RSA,
        keySize: Int = 2048
    ): KeyPair = withContext(Dispatchers.Default) {
        val keyPairGenerator = KeyPairGenerator.getInstance(algorithm.name)
        keyPairGenerator.initialize(keySize, SecureRandom())
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Generates a key pair in Android Keystore.
     */
    suspend fun generateKeystoreKeyPair(
        alias: String,
        algorithm: KeyAlgorithm = KeyAlgorithm.RSA,
        keySize: Int = 2048
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                algorithm.name,
                "AndroidKeyStore"
            )

            val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            val spec = KeyGenParameterSpec.Builder(alias, purposes)
                .setKeySize(keySize)
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384,
                    KeyProperties.DIGEST_SHA512
                )
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
            true
        } catch (e: Exception) {
            Timber.e(e, "Error generating keystore key pair")
            false
        }
    }

    /**
     * Lists certificates in keystore.
     */
    suspend fun listKeystoreCertificates(): List<KeystoreEntry> = withContext(Dispatchers.IO) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            keyStore.aliases().toList().mapNotNull { alias ->
                val certificate = keyStore.getCertificate(alias) as? X509Certificate
                    ?: return@mapNotNull null

                KeystoreEntry(
                    alias = alias,
                    subject = certificate.subjectX500Principal.name,
                    issuer = certificate.issuerX500Principal.name,
                    validFrom = certificate.notBefore.time,
                    validUntil = certificate.notAfter.time,
                    algorithm = certificate.sigAlgName
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error listing keystore certificates")
            emptyList()
        }
    }

    /**
     * Deletes a keystore entry.
     */
    suspend fun deleteKeystoreEntry(alias: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(alias)
            true
        } catch (e: Exception) {
            Timber.e(e, "Error deleting keystore entry")
            false
        }
    }

    /**
     * Validates a PEM certificate.
     */
    suspend fun validatePemCertificate(pem: String): CertificateValidation = 
        withContext(Dispatchers.Default) {
            try {
                val cleanPem = pem
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim()

                val derBytes = Base64.getDecoder().decode(cleanPem)
                val certificateFactory = java.security.cert.CertificateFactory.getInstance("X.509")
                val certificate = certificateFactory.generateCertificate(
                    derBytes.inputStream()
                ) as X509Certificate

                val now = Date()
                val isExpired = now.after(certificate.notAfter)
                val isNotYetValid = now.before(certificate.notBefore)

                CertificateValidation(
                    isValid = !isExpired && !isNotYetValid,
                    subject = certificate.subjectX500Principal.name,
                    issuer = certificate.issuerX500Principal.name,
                    serialNumber = certificate.serialNumber.toString(),
                    validFrom = certificate.notBefore.time,
                    validUntil = certificate.notAfter.time,
                    isExpired = isExpired,
                    isNotYetValid = isNotYetValid,
                    isSelfSigned = certificate.subjectX500Principal == certificate.issuerX500Principal,
                    algorithm = certificate.sigAlgName,
                    fingerprint = calculateFingerprint(certificate)
                )
            } catch (e: Exception) {
                Timber.e(e, "Error validating PEM certificate")
                CertificateValidation(
                    isValid = false,
                    error = "Invalid certificate: ${e.message}"
                )
            }
        }

    // Helper methods

    private fun buildSelfSignedCertificate(
        keyPair: KeyPair,
        subjectDn: String,
        validityDays: Int,
        signatureAlgorithm: SignatureAlgorithm
    ): X509Certificate {
        val subject = X500Principal(subjectDn)
        val now = Calendar.getInstance()
        val notBefore = now.time
        now.add(Calendar.DAY_OF_YEAR, validityDays)
        val notAfter = now.time
        val serialNumber = BigInteger(160, SecureRandom())

        // Build TBS certificate
        val tbsCert = buildTbsCertificate(
            serialNumber = serialNumber,
            issuer = subject,
            subject = subject,
            notBefore = notBefore,
            notAfter = notAfter,
            publicKey = keyPair.public,
            signatureAlgorithm = signatureAlgorithm
        )

        // Sign
        val signature = Signature.getInstance(signatureAlgorithm.algorithm)
        signature.initSign(keyPair.private)
        signature.update(tbsCert)
        val signatureBytes = signature.sign()

        // Build complete certificate
        val certBytes = buildX509Certificate(tbsCert, signatureAlgorithm, signatureBytes)

        val certificateFactory = java.security.cert.CertificateFactory.getInstance("X.509")
        return certificateFactory.generateCertificate(certBytes.inputStream()) as X509Certificate
    }

    private fun buildTbsCertificate(
        serialNumber: BigInteger,
        issuer: X500Principal,
        subject: X500Principal,
        notBefore: Date,
        notAfter: Date,
        publicKey: PublicKey,
        signatureAlgorithm: SignatureAlgorithm
    ): ByteArray {
        val version = byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02) // Version 3
        val serial = encodeInteger(serialNumber)
        val algId = encodeAlgorithmIdentifier(signatureAlgorithm)
        val issuerBytes = issuer.encoded
        val validity = encodeValidity(notBefore, notAfter)
        val subjectBytes = subject.encoded
        val publicKeyInfo = publicKey.encoded

        val content = version + serial + algId + issuerBytes + validity + subjectBytes + publicKeyInfo
        return wrapSequence(content)
    }

    private fun buildX509Certificate(
        tbsCert: ByteArray,
        signatureAlgorithm: SignatureAlgorithm,
        signature: ByteArray
    ): ByteArray {
        val algId = encodeAlgorithmIdentifier(signatureAlgorithm)
        val signatureBits = byteArrayOf(0x03, (signature.size + 1).toByte(), 0x00) + signature
        return wrapSequence(tbsCert + algId + signatureBits)
    }

    private fun buildCsr(
        keyPair: KeyPair,
        subjectDn: String,
        signatureAlgorithm: SignatureAlgorithm
    ): String {
        val subject = X500Principal(subjectDn)
        val certReqInfo = buildCertReqInfo(subject, keyPair.public)

        val signature = Signature.getInstance(signatureAlgorithm.algorithm)
        signature.initSign(keyPair.private)
        signature.update(certReqInfo)
        val signatureBytes = signature.sign()

        val algId = encodeAlgorithmIdentifier(signatureAlgorithm)
        val signatureBits = byteArrayOf(0x03, (signatureBytes.size + 1).toByte(), 0x00) + signatureBytes
        val csr = wrapSequence(certReqInfo + algId + signatureBits)

        return "-----BEGIN CERTIFICATE REQUEST-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(csr) +
                "\n-----END CERTIFICATE REQUEST-----"
    }

    private fun buildCertReqInfo(subject: X500Principal, publicKey: PublicKey): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00) // Version 0
        val subjectBytes = subject.encoded
        val publicKeyInfo = publicKey.encoded
        val attributes = byteArrayOf(0xA0.toByte(), 0x00) // Empty attributes

        return wrapSequence(version + subjectBytes + publicKeyInfo + attributes)
    }

    private fun encodeInteger(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return byteArrayOf(0x02, bytes.size.toByte()) + bytes
    }

    private fun encodeAlgorithmIdentifier(algorithm: SignatureAlgorithm): ByteArray {
        val oid = when (algorithm) {
            SignatureAlgorithm.SHA256_RSA -> byteArrayOf(
                0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
                0x0D, 0x01, 0x01, 0x0B
            )
            SignatureAlgorithm.SHA384_RSA -> byteArrayOf(
                0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
                0x0D, 0x01, 0x01, 0x0C
            )
            SignatureAlgorithm.SHA512_RSA -> byteArrayOf(
                0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
                0x0D, 0x01, 0x01, 0x0D
            )
        }
        return wrapSequence(oid + byteArrayOf(0x05, 0x00))
    }

    private fun encodeValidity(notBefore: Date, notAfter: Date): ByteArray {
        val dateFormat = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")

        val notBeforeStr = dateFormat.format(notBefore)
        val notAfterStr = dateFormat.format(notAfter)

        val notBeforeBytes = byteArrayOf(0x17, notBeforeStr.length.toByte()) + notBeforeStr.toByteArray()
        val notAfterBytes = byteArrayOf(0x17, notAfterStr.length.toByte()) + notAfterStr.toByteArray()

        return wrapSequence(notBeforeBytes + notAfterBytes)
    }

    private fun wrapSequence(data: ByteArray): ByteArray {
        return if (data.size < 128) {
            byteArrayOf(0x30, data.size.toByte()) + data
        } else {
            val sizeBytes = encodeLongLength(data.size)
            byteArrayOf(0x30) + sizeBytes + data
        }
    }

    private fun encodeLongLength(length: Int): ByteArray {
        return when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
            length < 0x10000 -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
            else -> byteArrayOf(
                0x83.toByte(),
                (length shr 16).toByte(),
                (length shr 8).toByte(),
                length.toByte()
            )
        }
    }

    private fun exportToPem(certificate: X509Certificate): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(certificate.encoded)
        return "-----BEGIN CERTIFICATE-----\n$base64\n-----END CERTIFICATE-----"
    }

    private fun exportPrivateKeyToPem(privateKey: PrivateKey): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(privateKey.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----"
    }

    private fun exportPublicKeyToPem(publicKey: PublicKey): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(publicKey.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    private fun calculateFingerprint(certificate: X509Certificate): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(certificate.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}

/**
 * Key algorithm.
 */
enum class KeyAlgorithm {
    RSA,
    EC
}

/**
 * Signature algorithm.
 */
enum class SignatureAlgorithm(val algorithm: String) {
    SHA256_RSA("SHA256withRSA"),
    SHA384_RSA("SHA384withRSA"),
    SHA512_RSA("SHA512withRSA")
}

/**
 * Certificate configuration.
 */
@Serializable
data class CertificateConfig(
    val subjectDn: String,
    val validityDays: Int = 365,
    val keyAlgorithm: KeyAlgorithm = KeyAlgorithm.RSA,
    val keySize: Int = 2048,
    val signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.SHA256_RSA
)

/**
 * CSR configuration.
 */
@Serializable
data class CsrConfig(
    val subjectDn: String,
    val keyAlgorithm: KeyAlgorithm = KeyAlgorithm.RSA,
    val keySize: Int = 2048,
    val signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.SHA256_RSA
)

/**
 * Certificate generation result.
 */
sealed class CertificateResult {
    data class Success(
        val certificate: String,
        val privateKey: String,
        val publicKey: String,
        val serialNumber: String,
        val fingerprint: String,
        val validFrom: Long,
        val validUntil: Long
    ) : CertificateResult()

    data class Error(val message: String, val exception: Throwable? = null) : CertificateResult()
}

/**
 * CSR generation result.
 */
sealed class CsrResult {
    data class Success(
        val csr: String,
        val privateKey: String,
        val publicKey: String
    ) : CsrResult()

    data class Error(val message: String, val exception: Throwable? = null) : CsrResult()
}

/**
 * Keystore entry information.
 */
@Serializable
data class KeystoreEntry(
    val alias: String,
    val subject: String,
    val issuer: String,
    val validFrom: Long,
    val validUntil: Long,
    val algorithm: String
)

/**
 * Certificate validation result.
 */
@Serializable
data class CertificateValidation(
    val isValid: Boolean,
    val subject: String? = null,
    val issuer: String? = null,
    val serialNumber: String? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val isExpired: Boolean = false,
    val isNotYetValid: Boolean = false,
    val isSelfSigned: Boolean = false,
    val algorithm: String? = null,
    val fingerprint: String? = null,
    val error: String? = null
)
