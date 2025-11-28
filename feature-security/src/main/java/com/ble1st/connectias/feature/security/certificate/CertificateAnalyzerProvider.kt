package com.ble1st.connectias.feature.security.certificate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/**
 * Provider for SSL/TLS certificate analysis.
 * Analyzes certificates from URLs and certificate chains.
 */
@Singleton
class CertificateAnalyzerProvider @Inject constructor() {

    /**
     * Analyzes certificate from a URL.
     * 
     * @param urlString The URL to analyze (must be HTTPS)
     * @return CertificateInfo or null if failed
     */
    suspend fun analyzeCertificateFromUrl(urlString: String): CertificateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            if (url.protocol != "https") {
                return@withContext null
            }

            val connection = url.openConnection() as HttpsURLConnection
            connection.connect()
            
            val certificates = connection.serverCertificates
            if (certificates.isEmpty()) {
                return@withContext null
            }

            val cert = certificates[0] as X509Certificate
            connection.disconnect()
            
            analyzeCertificate(cert)
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze certificate from URL: $urlString")
            null
        }
    }

    /**
     * Analyzes an X509Certificate.
     */
    suspend fun analyzeCertificate(certificate: X509Certificate): CertificateInfo = withContext(Dispatchers.Default) {
        val now = Date()
        val notBefore = certificate.notBefore
        val notAfter = certificate.notAfter
        
        val isExpired = now.after(notAfter)
        val isNotYetValid = now.before(notBefore)
        val daysUntilExpiry = if (!isExpired && !isNotYetValid) {
            ((notAfter.time - now.time) / (1000 * 60 * 60 * 24)).toInt()
        } else {
            0
        }
        
        val isSelfSigned = isSelfSigned(certificate)
        
        val issuer = certificate.issuerDN.name
        val subject = certificate.subjectDN.name
        
        CertificateInfo(
            subject = subject,
            issuer = issuer,
            notBefore = formatDate(notBefore),
            notAfter = formatDate(notAfter),
            isExpired = isExpired,
            isNotYetValid = isNotYetValid,
            daysUntilExpiry = daysUntilExpiry,
            isSelfSigned = isSelfSigned,
            signatureAlgorithm = certificate.sigAlgName,
            publicKeyAlgorithm = certificate.publicKey.algorithm,
            serialNumber = certificate.serialNumber.toString(),
            version = certificate.version
        )
    }

    /**
     * Analyzes certificate chain.
     * 
     * @param certificates List of certificates in the chain
     * @return List of CertificateInfo
     */
    suspend fun analyzeCertificateChain(certificates: List<Certificate>): List<CertificateInfo> = withContext(Dispatchers.Default) {
        certificates.mapNotNull { cert ->
            if (cert is X509Certificate) {
                analyzeCertificate(cert)
            } else {
                null
            }
        }
    }

    /**
     * Checks if certificate is self-signed.
     */
    private fun isSelfSigned(certificate: X509Certificate): Boolean {
        return try {
            certificate.verify(certificate.publicKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Formats date for display.
     */
    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }
}

/**
 * Certificate information.
 */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val notBefore: String,
    val notAfter: String,
    val isExpired: Boolean,
    val isNotYetValid: Boolean,
    val daysUntilExpiry: Int,
    val isSelfSigned: Boolean,
    val signatureAlgorithm: String,
    val publicKeyAlgorithm: String,
    val serialNumber: String,
    val version: Int
)

