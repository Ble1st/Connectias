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

    suspend fun scan(host: String, port: Int = 443): SslReport = withContext(Dispatchers.IO) {
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

        val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val hostnameValid = hostnameVerifier.verify(host, session)

        val problems = mutableListOf<String>()
        if (!isValidNow) problems.add("Zertifikat ist nicht gültig (Zeitraum).")
        if (!hostnameValid) problems.add("Hostname stimmt nicht überein.")

        socket.close()

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
