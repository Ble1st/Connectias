package com.ble1st.connectias.feature.network.ssl

import com.ble1st.connectias.feature.network.model.SslReport
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class SslScanner {
    private val rustAnalyzer = try {
        RustSslAnalyzer()
    } catch (e: Exception) {
        null // Fallback to Kotlin if Rust not available
    }

    suspend fun scan(host: String, port: Int = 443): SslReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, null)
        val factory = sslContext.socketFactory
        val socket = factory.createSocket() as SSLSocket

        val address = InetSocketAddress(host, port)
        socket.soTimeout = 5000
        socket.startHandshake(address)

        val session = socket.session
        val certs = session.peerCertificates
        val leaf = certs.firstOrNull() as? X509Certificate
            ?: throw IllegalStateException("Keine X509 Zertifikate gefunden")

        val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val hostnameValid = hostnameVerifier.verify(host, session)

        socket.close()

        // Try Rust implementation for certificate analysis (if available)
        val result = if (rustAnalyzer != null) {
            try {
                Timber.i("🔴 [SslScanner] Using RUST implementation for certificate analysis")
                val rustStartTime = System.currentTimeMillis()
                
                val rustResult = rustAnalyzer.analyzeCertificate(leaf, host)
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                
                Timber.i("✅ [SslScanner] RUST certificate analysis completed in ${rustDuration}ms")
                Timber.d("📊 [SslScanner] Total time (including overhead): ${totalDuration}ms")
                
                // Combine with hostname verification
                rustResult.copy(
                    isValidNow = rustResult.isValidNow && hostnameValid,
                    problems = rustResult.problems + if (!hostnameValid) listOf("Hostname stimmt nicht überein.") else emptyList()
                )
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [SslScanner] RUST certificate analysis failed after ${rustDuration}ms, using Kotlin")
                // Fall through to Kotlin implementation
                null
            }
        } else {
            null
        }
        
        // Fallback to Kotlin implementation
        if (result == null) {
            Timber.i("🟡 [SslScanner] Using KOTLIN implementation for certificate analysis")
            val kotlinStartTime = System.currentTimeMillis()
            
            val now = Instant.now()
            val validFrom = leaf.notBefore.toInstant()
            val validTo = leaf.notAfter.toInstant()
            val daysRemaining = Duration.between(now, validTo).toDays()
            val isValidNow = now.isAfter(validFrom) && now.isBefore(validTo)
            val keyAlgorithm = leaf.publicKey.algorithm
            val keySize = when (val key = leaf.publicKey) {
                is RSAPublicKey -> key.modulus.bitLength()
                is ECPublicKey -> key.params.curve.field.fieldSize
                else -> leaf.publicKey.encoded.size * 8
            }
            val signatureAlgorithm = leaf.sigAlgName

            val problems = mutableListOf<String>()
            if (!isValidNow) problems.add("Zertifikat ist nicht gültig (Zeitraum).")
            if (!hostnameValid) problems.add("Hostname stimmt nicht überein.")

            val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
            val totalDuration = System.currentTimeMillis() - startTime
            
            Timber.i("✅ [SslScanner] KOTLIN certificate analysis completed in ${kotlinDuration}ms")
            Timber.d("📊 [SslScanner] Total time (including overhead): ${totalDuration}ms")
            
            SslReport(
                subject = leaf.subjectX500Principal.name,
                issuer = leaf.issuerX500Principal.name,
                validFrom = validFrom,
                validTo = validTo,
                daysRemaining = daysRemaining,
                isValidNow = isValidNow && hostnameValid,
                keyAlgorithm = keyAlgorithm,
                keySize = keySize,
                signatureAlgorithm = signatureAlgorithm,
                problems = problems
            )
        } else {
            result
        }
    }

    private fun SSLSocket.startHandshake(address: InetSocketAddress) {
        try {
            connect(address, 5000)
            startHandshake()
        } catch (e: Exception) {
            Timber.e(e, "SSL handshake failed")
            throw e
        }
    }
}
