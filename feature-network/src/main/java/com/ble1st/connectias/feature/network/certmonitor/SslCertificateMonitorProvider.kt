package com.ble1st.connectias.feature.network.certmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.URL
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Provider for SSL certificate monitoring functionality.
 */
@Singleton
class SslCertificateMonitorProvider @Inject constructor() {

    private val _monitoredDomains = MutableStateFlow<List<MonitoredDomain>>(emptyList())
    val monitoredDomains: StateFlow<List<MonitoredDomain>> = _monitoredDomains.asStateFlow()

    /**
     * Checks SSL certificate for a domain.
     */
    suspend fun checkCertificate(domain: String): CertificateInfo = withContext(Dispatchers.IO) {
        try {
            val url = if (domain.startsWith("https://")) domain else "https://$domain"
            val connection = URL(url).openConnection() as HttpsURLConnection

            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Use a custom trust manager to capture certificates
            var capturedCerts: Array<X509Certificate>? = null

            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
                    capturedCerts = chain
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            connection.sslSocketFactory = sslContext.socketFactory
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }

            connection.connect()
            connection.disconnect()

            val cert = capturedCerts?.firstOrNull()
                ?: return@withContext CertificateInfo(domain = domain, isValid = false, error = "No certificate found")

            val now = System.currentTimeMillis()
            val expiryDate = cert.notAfter.time
            val daysUntilExpiry = TimeUnit.MILLISECONDS.toDays(expiryDate - now)

            CertificateInfo(
                domain = domain,
                isValid = true,
                issuer = cert.issuerX500Principal.name,
                subject = cert.subjectX500Principal.name,
                serialNumber = cert.serialNumber.toString(16),
                algorithm = cert.sigAlgName,
                validFrom = cert.notBefore.time,
                validUntil = cert.notAfter.time,
                daysUntilExpiry = daysUntilExpiry.toInt(),
                isExpired = now > expiryDate,
                isExpiringSoon = daysUntilExpiry in 0..30,
                publicKeyAlgorithm = cert.publicKey.algorithm,
                version = cert.version,
                subjectAlternativeNames = extractSANs(cert),
                fingerprint = calculateFingerprint(cert)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking certificate for $domain")
            CertificateInfo(
                domain = domain,
                isValid = false,
                error = e.message
            )
        }
    }

    /**
     * Adds a domain to monitor.
     */
    fun addDomain(domain: String, warningDays: Int = 30) {
        val monitoredDomain = MonitoredDomain(
            domain = domain,
            warningDays = warningDays
        )
        _monitoredDomains.update { it + monitoredDomain }
    }

    /**
     * Removes a domain from monitoring.
     */
    fun removeDomain(domain: String) {
        _monitoredDomains.update { it.filter { d -> d.domain != domain } }
    }

    /**
     * Checks all monitored domains.
     */
    suspend fun checkAllDomains(): List<CertificateInfo> = withContext(Dispatchers.IO) {
        _monitoredDomains.value.map { domain ->
            val info = checkCertificate(domain.domain)
            updateDomainStatus(domain.domain, info)
            info
        }
    }

    /**
     * Gets domains expiring soon.
     */
    suspend fun getExpiringDomains(withinDays: Int = 30): List<CertificateInfo> = withContext(Dispatchers.IO) {
        checkAllDomains().filter { it.isValid && (it.daysUntilExpiry ?: 0) <= withinDays }
    }

    /**
     * Gets expired domains.
     */
    suspend fun getExpiredDomains(): List<CertificateInfo> = withContext(Dispatchers.IO) {
        checkAllDomains().filter { it.isExpired }
    }

    private fun updateDomainStatus(domain: String, info: CertificateInfo) {
        _monitoredDomains.update { domains ->
            domains.map { d ->
                if (d.domain == domain) {
                    d.copy(
                        lastCheck = System.currentTimeMillis(),
                        daysUntilExpiry = info.daysUntilExpiry,
                        isValid = info.isValid,
                        error = info.error
                    )
                } else d
            }
        }
    }

    private fun extractSANs(cert: X509Certificate): List<String> {
        return try {
            cert.subjectAlternativeNames?.mapNotNull { san ->
                when (san[0] as Int) {
                    2 -> san[1] as String // DNS name
                    7 -> san[1] as String // IP address
                    else -> null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateFingerprint(cert: X509Certificate): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert.encoded)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * Certificate information.
 */
@Serializable
data class CertificateInfo(
    val domain: String,
    val isValid: Boolean,
    val issuer: String? = null,
    val subject: String? = null,
    val serialNumber: String? = null,
    val algorithm: String? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val daysUntilExpiry: Int? = null,
    val isExpired: Boolean = false,
    val isExpiringSoon: Boolean = false,
    val publicKeyAlgorithm: String? = null,
    val version: Int? = null,
    val subjectAlternativeNames: List<String> = emptyList(),
    val fingerprint: String? = null,
    val error: String? = null
)

/**
 * Monitored domain.
 */
@Serializable
data class MonitoredDomain(
    val domain: String,
    val warningDays: Int = 30,
    val lastCheck: Long? = null,
    val daysUntilExpiry: Int? = null,
    val isValid: Boolean? = null,
    val error: String? = null
)
