package com.ble1st.connectias.privacy.export

import kotlinx.serialization.json.Json
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Stream-based export format: header + AES-GCM encrypted ZIP payload.
 *
 * This is separated from SAF-specific handling so it can be unit-tested on the JVM.
 */
object EncryptedZipExportFormat {

    data class Result(
        val plaintextBytesWritten: Long,
        val fileCount: Int
    )

    internal const val MAGIC: String = "CONNECTIAS_GDPR_EXPORT"
    internal const val FORMAT_VERSION: Int = 1

    // PBKDF2 parameters
    internal const val PBKDF2_ALG: String = "PBKDF2WithHmacSHA256"
    internal const val PBKDF2_ITERATIONS: Int = 150_000
    internal const val SALT_LENGTH_BYTES: Int = 16

    // AES-GCM parameters
    internal const val CIPHER_ALG: String = "AES/GCM/NoPadding"
    internal const val GCM_IV_LENGTH_BYTES: Int = 12
    internal const val GCM_TAG_LENGTH_BITS: Int = 128

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encryptZipTo(
        outputStream: OutputStream,
        passphrase: CharArray,
        exportBundle: PrivacyExportBundle
    ): Result {
        val dos = DataOutputStream(outputStream)

        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

        // Header (unencrypted)
        writeHeader(dos = dos, salt = salt, iv = iv, iterations = PBKDF2_ITERATIONS)

        val key = deriveKey(passphrase = passphrase, salt = salt, iterations = PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance(CIPHER_ALG).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }

        var plaintextBytesWritten = 0L
        var fileCount = 0

        CipherOutputStream(dos, cipher).use { cipherOut ->
            ZipOutputStream(cipherOut).use { zipOut ->
                fun putTextEntry(name: String, content: String) {
                    zipOut.putNextEntry(ZipEntry(name))
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    zipOut.write(bytes)
                    zipOut.closeEntry()
                    plaintextBytesWritten += bytes.size
                    fileCount += 1
                }

                // JSON payload
                val jsonText = json.encodeToString(exportBundle)
                putTextEntry("export.json", jsonText)

                // CSV payloads
                putTextEntry("audit_events.csv", PrivacyExportCsv.auditEventsCsv(exportBundle.auditEvents))
                putTextEntry("permission_usage.csv", PrivacyExportCsv.permissionUsageCsv(exportBundle.permissionUsage))
                putTextEntry("network_usage.csv", PrivacyExportCsv.networkUsageCsv(exportBundle.networkUsage))
                putTextEntry("data_leakage.csv", PrivacyExportCsv.dataLeakageCsv(exportBundle.dataLeakageEvents))
            }
        }

        dos.flush()
        return Result(plaintextBytesWritten = plaintextBytesWritten, fileCount = fileCount)
    }

    internal fun writeHeader(dos: DataOutputStream, salt: ByteArray, iv: ByteArray, iterations: Int) {
        // Magic + version
        dos.writeUTF(MAGIC)
        dos.writeInt(FORMAT_VERSION)

        // KDF parameters
        dos.writeUTF(PBKDF2_ALG)
        dos.writeInt(iterations)
        dos.writeInt(salt.size)
        dos.write(salt)

        // Cipher parameters
        dos.writeUTF(CIPHER_ALG)
        dos.writeInt(GCM_TAG_LENGTH_BITS)
        dos.writeInt(iv.size)
        dos.write(iv)
    }

    internal fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALG)
        val keyBytes = factory.generateSecret(spec).encoded
        // Best-effort cleanup
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }
}

