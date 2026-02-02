package com.ble1st.connectias.privacy.export

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec

class EncryptedZipExportFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `encryptZipTo produces decryptable zip with expected files`() {
        val bundle = PrivacyExportBundle(
            metadata = PrivacyExportMetadata(
                schemaVersion = 1,
                createdAtEpochMillis = 1_700_000_000_000,
                timeWindow = ExportTimeWindow(
                    startEpochMillis = 1_700_000_000_000,
                    endEpochMillis = 1_700_000_100_000
                ),
                appVersionName = "test"
            ),
            auditEvents = listOf(
                AuditEventRecord(
                    id = "e1",
                    timestamp = 123,
                    eventType = "TEST_EVENT",
                    severity = "INFO",
                    source = "UnitTest",
                    pluginId = "plugin-a",
                    message = "hello",
                    details = mapOf("k" to "v")
                )
            ),
            permissionUsage = listOf(
                PermissionUsageRecord(
                    pluginId = "plugin-a",
                    permission = "android.permission.CAMERA",
                    granted = true,
                    timestamp = 456,
                    context = "test"
                )
            ),
            networkUsage = listOf(
                NetworkUsageRecord(
                    pluginId = "plugin-a",
                    bytesReceived = 10,
                    bytesSent = 20,
                    connectionsOpened = 1,
                    connectionsFailed = 0,
                    domainsAccessed = listOf("example.com"),
                    portsUsed = listOf(443),
                    firstActivity = 100,
                    lastActivity = 200
                )
            ),
            dataLeakageEvents = listOf(
                DataLeakageRecord(
                    pluginId = "plugin-a",
                    timestamp = 789,
                    dataType = "EMAIL",
                    operation = "READ",
                    suspicious = true,
                    dataPattern = "*** REDACTED ***"
                )
            )
        )

        val out = ByteArrayOutputStream()
        val passphrase = "correct horse battery staple".toCharArray()
        try {
            val result = EncryptedZipExportFormat.encryptZipTo(out, passphrase, bundle)
            assertTrue(result.fileCount >= 5)
            assertTrue(result.plaintextBytesWritten > 0)
        } finally {
            passphrase.fill('\u0000')
        }

        val decryptedFiles = decryptExport(out.toByteArray(), "correct horse battery staple".toCharArray())

        assertTrue(decryptedFiles.containsKey("export.json"))
        assertTrue(decryptedFiles.containsKey("audit_events.csv"))
        assertTrue(decryptedFiles.containsKey("permission_usage.csv"))
        assertTrue(decryptedFiles.containsKey("network_usage.csv"))
        assertTrue(decryptedFiles.containsKey("data_leakage.csv"))

        val jsonText = decryptedFiles["export.json"]!!.toString(Charsets.UTF_8)
        val decoded = json.decodeFromString<PrivacyExportBundle>(jsonText)
        assertEquals(1, decoded.metadata.schemaVersion)
        assertEquals(1, decoded.auditEvents.size)
        assertEquals(1, decoded.permissionUsage.size)
        assertEquals(1, decoded.networkUsage.size)
        assertEquals(1, decoded.dataLeakageEvents.size)
        assertNotNull(decoded.metadata.timeWindow)
    }

    private fun decryptExport(encryptedBytes: ByteArray, passphrase: CharArray): Map<String, ByteArray> {
        try {
            val dis = DataInputStream(ByteArrayInputStream(encryptedBytes))

            val magic = dis.readUTF()
            assertEquals(EncryptedZipExportFormat.MAGIC, magic)

            val version = dis.readInt()
            assertEquals(EncryptedZipExportFormat.FORMAT_VERSION, version)

            val kdfAlg = dis.readUTF()
            assertEquals(EncryptedZipExportFormat.PBKDF2_ALG, kdfAlg)

            val iterations = dis.readInt()
            val saltLen = dis.readInt()
            val salt = ByteArray(saltLen)
            dis.readFully(salt)

            val cipherAlg = dis.readUTF()
            assertEquals(EncryptedZipExportFormat.CIPHER_ALG, cipherAlg)

            val tagBits = dis.readInt()
            val ivLen = dis.readInt()
            val iv = ByteArray(ivLen)
            dis.readFully(iv)

            val key = EncryptedZipExportFormat.deriveKey(passphrase = passphrase, salt = salt, iterations = iterations)
            val cipher = Cipher.getInstance(cipherAlg).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(tagBits, iv))
            }

            val files = mutableMapOf<String, ByteArray>()
            CipherInputStream(dis, cipher).use { cis ->
                ZipInputStream(cis).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val bytes = zis.readBytes()
                        files[entry.name] = bytes
                        zis.closeEntry()
                    }
                }
            }

            return files
        } finally {
            passphrase.fill('\u0000')
        }
    }
}

