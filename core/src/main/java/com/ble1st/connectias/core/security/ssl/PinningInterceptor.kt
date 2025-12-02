package com.ble1st.connectias.core.security.ssl

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * OkHttp interceptor that performs additional certificate pinning validation.
 *
 * This interceptor provides:
 * - Additional validation on top of OkHttp's CertificatePinner
 * - Detailed logging for debugging
 * - Custom pin expiration handling
 */
class PinningInterceptor(
    private val sslPinningManager: SslPinningManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val hostname = request.url.host

        // Proceed with the request
        val response = chain.proceed(request)

        // Validate the certificate chain after connection
        try {
            val handshake = response.handshake
            if (handshake != null) {
                val certificates = handshake.peerCertificates

                if (certificates.isNotEmpty()) {
                    val x509Certs = certificates.filterIsInstance<X509Certificate>()
                    
                    if (x509Certs.isNotEmpty()) {
                        val validationResult = sslPinningManager.validateCertificateChain(
                            hostname = hostname,
                            chain = x509Certs.toTypedArray()
                        )

                        when (validationResult) {
                            is PinValidationResult.Valid -> {
                                Timber.d("Certificate pinning validated for $hostname")
                            }
                            is PinValidationResult.Invalid -> {
                                Timber.w("Certificate pinning failed for $hostname: ${validationResult.reason}")
                                // In strict mode, we could throw an exception here
                                // For now, we log and continue (OkHttp's CertificatePinner handles enforcement)
                            }
                            is PinValidationResult.NoPinsConfigured -> {
                                // No pins configured, continue normally
                                Timber.v("No certificate pins configured for $hostname")
                            }
                            is PinValidationResult.PinExpired -> {
                                Timber.w("Certificate pin has expired for $hostname")
                            }
                            is PinValidationResult.Error -> {
                                Timber.e(validationResult.exception, "Error validating certificate for $hostname")
                            }
                        }

                        // Log certificate info in debug builds
                        if (Timber.treeCount > 0) {
                            logCertificateChain(hostname, x509Certs)
                        }
                    }
                }
            }
        } catch (e: SSLPeerUnverifiedException) {
            Timber.e(e, "SSL peer not verified for $hostname")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error during certificate validation for $hostname")
            // Don't throw - let the connection proceed and rely on system validation
        }

        return response
    }

    private fun logCertificateChain(hostname: String, certificates: List<X509Certificate>) {
        Timber.v("Certificate chain for $hostname:")
        certificates.forEachIndexed { index, cert ->
            val info = sslPinningManager.getCertificateInfo(cert)
            Timber.v(
                "  [$index] Subject: ${info.subject}, " +
                "Issuer: ${info.issuer}, " +
                "Valid: ${info.isValid()}, " +
                "Days until expiry: ${info.daysUntilExpiration()}"
            )
        }
    }
}
