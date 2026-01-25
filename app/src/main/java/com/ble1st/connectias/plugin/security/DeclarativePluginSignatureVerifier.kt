package com.ble1st.connectias.plugin.security

import android.util.Base64
import com.ble1st.connectias.plugin.declarative.model.DeclarativeJson
import com.ble1st.connectias.plugin.declarative.model.DeclarativePluginValidator
import java.io.File
import java.security.MessageDigest
import java.security.Signature
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import timber.log.Timber

/**
 * Verifies ".cplug" declarative plugin packages:
 * - Structure: mandatory files present
 * - Manifest schema: strict validation
 * - Content digest: canonical hash over package entries
 * - Signature: ECDSA P-256 over contentDigestHex
 * - Truststore: developerId must be trusted (public key pinned by developerId)
 */
@Singleton
class DeclarativePluginSignatureVerifier @Inject constructor(
    private val trustStore: DeclarativeDeveloperTrustStore
) {

    sealed class Result {
        data class Ok(val developerId: String) : Result()
        data class Failed(val reason: String) : Result()
        data class Suspicious(val warnings: List<String>) : Result()
    }

    fun verifyPackage(file: File): Result {
        if (!file.name.endsWith(".cplug")) return Result.Failed("Not a .cplug file")
        if (!file.exists()) return Result.Failed("Package file not found")

        val warnings = mutableListOf<String>()

        return try {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry("plugin-manifest.json")
                    ?: return Result.Failed("Missing plugin-manifest.json")
                val signatureEntry = zip.getEntry("signature.json")
                    ?: return Result.Failed("Missing signature.json")

                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val signatureJson = zip.getInputStream(signatureEntry).bufferedReader().use { it.readText() }

                val manifestObj = JSONObject(manifestJson)
                val signatureObj = JSONObject(signatureJson)

                val manifest = DeclarativeJson.parseManifest(manifestObj)
                val sig = DeclarativeJson.parseSignature(signatureObj)

                val manifestValidation = DeclarativePluginValidator.validateManifest(manifest)
                if (!manifestValidation.ok) {
                    return Result.Failed("Invalid manifest: ${manifestValidation.errors.joinToString("; ")}")
                }
                warnings.addAll(manifestValidation.warnings)

                if (sig.developerId != manifest.developerId) {
                    return Result.Failed("developerId mismatch between manifest and signature")
                }

                // Truststore enforcement (default-deny for unknown developer keys)
                val trustedKeyB64 = trustStore.getPublicKeyBase64(manifest.developerId)
                    ?: return Result.Failed("Developer key not trusted: ${manifest.developerId}")

                if (trustedKeyB64.trim() != sig.publicKeyBase64.trim()) {
                    return Result.Failed("Public key mismatch for developerId=${manifest.developerId}")
                }

                val publicKey = trustStore.decodePublicKey(sig.publicKeyBase64)
                    ?: return Result.Failed("Invalid developer public key encoding")

                val computedDigestHex = computeContentDigestHex(zip)
                if (!computedDigestHex.equals(sig.contentDigestHex, ignoreCase = true)) {
                    return Result.Failed("Content digest mismatch")
                }

                if (!verifySignature(publicKey, sig.contentDigestHex, sig.signatureBase64, sig.algorithm)) {
                    return Result.Failed("Signature verification failed")
                }

                if (warnings.isNotEmpty()) Result.Suspicious(warnings) else Result.Ok(manifest.developerId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Declarative package verification failed")
            Result.Failed(e.message ?: "Verification error")
        }
    }

    /**
     * Canonical digest:
     * - Include: all entries except "signature.json" and any directory entries.
     * - Sort by entry name.
     * - Update digest with UTF-8 entry name + 0 byte delimiter + file bytes.
     */
    private fun computeContentDigestHex(zip: ZipFile): String {
        val md = MessageDigest.getInstance("SHA-256")
        val names = zip.entries().toList()
            .filter { !it.isDirectory }
            .map { it.name }
            .filter { it != "signature.json" }
            .sorted()

        for (name in names) {
            md.update(name.toByteArray(Charsets.UTF_8))
            md.update(0)
            val entry = zip.getEntry(name) ?: continue
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    md.update(buffer, 0, read)
                }
            }
        }
        return md.digest().toHexLower()
    }

    private fun verifySignature(
        publicKey: java.security.PublicKey,
        contentDigestHex: String,
        signatureBase64: String,
        algorithm: String
    ): Boolean {
        val sigAlg = algorithm.ifBlank { "SHA256withECDSA" }
        return try {
            val verifier = Signature.getInstance(sigAlg)
            verifier.initVerify(publicKey)
            verifier.update(contentDigestHex.toByteArray(Charsets.UTF_8))
            val sigBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            Timber.e(e, "Declarative signature verification error")
            false
        }
    }

    private fun ByteArray.toHexLower(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(((b.toInt() shr 4) and 0xF).toString(16))
            sb.append((b.toInt() and 0xF).toString(16))
        }
        return sb.toString()
    }
}

