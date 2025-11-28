package com.ble1st.connectias.feature.wasm.security

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies plugin signatures using RSA PKCS#1 v1.5 with SHA-256.
 * 
 * Signature format matches the reference implementation:
 * - Signature stored in META-INF/SIGNATURE.RSA (Base64 encoded)
 * - Message is concatenation of sorted files: Path|Size|Content
 */
@Singleton
class PluginSignatureVerifier @Inject constructor() {
    
    private val tag = "PluginSignatureVerifier"
    
    /**
     * Verify plugin signature.
     * 
     * @param zipBytes Plugin ZIP file bytes
     * @param signatureBase64 Base64-encoded signature
     * @param publicKey Public key for verification
     * @return true if signature is valid
     */
    fun verifySignature(
        zipBytes: ByteArray,
        signatureBase64: String,
        publicKey: PublicKey
    ): Boolean {
        return try {
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            val message = createMessage(zipBytes)
            verifySignature(message, signatureBytes, publicKey)
        } catch (e: Exception) {
            Timber.e(e, "Signature verification failed")
            false
        }
    }
    
    /**
     * Create message for signature verification.
     * Message format: sorted files with Path|Size|Content
     */
    private fun createMessage(zipBytes: ByteArray): ByteArray {
        val entries = mutableListOf<Triple<String, Int, ByteArray>>()
        
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            
            while (entry != null) {
                val name = entry.name
                
                // Skip signature files
                if (name == "SIGNATURE" ||
                    name == "META-INF/SIGNATURE.RSA" ||
                    name.endsWith(".sig") ||
                    name.endsWith(".sig.asc") ||
                    name.endsWith("/SIGNATURE")
                ) {
                    entry = zip.nextEntry
                    continue
                }
                
                val content = zip.readBytes()
                entries.add(Triple(name, content.size, content))
                entry = zip.nextEntry
            }
        }
        
        // Sort by path
        entries.sortBy { it.first }
        
        // Create message: Path|Size|Content
        val message = mutableListOf<Byte>()
        for ((path, size, content) in entries) {
            message.addAll(path.toByteArray().toList())
            message.add('|'.code.toByte())
            message.addAll(size.toString().toByteArray().toList())
            message.add('|'.code.toByte())
            message.addAll(content.toList())
        }
        
        return message.toByteArray()
    }
    
    /**
     * Verify signature using RSA PKCS#1 v1.5 with SHA-256.
     */
    private fun verifySignature(
        message: ByteArray,
        signature: ByteArray,
        publicKey: PublicKey
    ): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(message)
            sig.verify(signature)
        } catch (e: Exception) {
            Timber.e(e, "Signature verification failed")
            false
        }
    }
    
    /**
     * Load public key from PEM string.
     */
    fun loadPublicKeyFromPem(pemString: String): PublicKey {
        val reader = StringReader(pemString)
        val pemParser = PEMParser(reader)
        
        return try {
            val pemObject = pemParser.readObject()
            when (pemObject) {
                is SubjectPublicKeyInfo -> {
                    val converter = JcaPEMKeyConverter()
                    converter.getPublicKey(pemObject)
                }
                is X509CertificateHolder -> {
                    val converter = JcaX509CertificateConverter()
                    val cert = converter.getCertificate(pemObject)
                    cert.publicKey
                }
                else -> throw IllegalArgumentException("Unsupported PEM object type")
            }
        } finally {
            pemParser.close()
        }
    }
}

