package com.ble1st.connectias.analytics.export

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec

class EncryptedAnalyticsExportFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `encryptZipTo produces decryptable zip with expected files`() {
        val bundle = AnalyticsExportBundle(
            metadata = AnalyticsExportMetadata(
                schemaVersion = 1,
                createdAtEpochMillis = 1_700_000_000_000,
                windowLabel = "Last 24h",
                windowStartEpochMillis = 1_700_000_000_000,
                windowEndEpochMillis = 1_700_000_100_000
            ),
            pluginStats = listOf(
                AnalyticsPluginStat(
                    pluginId = "plugin-a",
                    samples = 10,
                    avgCpu = 1.5f,
                    peakCpu = 10.0f,
                    avgMemMB = 42.0f,
                    peakMemMB = 100,
                    netInBytes = 1234,
                    netOutBytes = 5678,
                    uiActions = 9,
                    rateLimitHits = 3
                )
            )
        )

        val out = ByteArrayOutputStream()
        val passphrase = "correct horse battery staple".toCharArray()
        try {
            val result = EncryptedAnalyticsExportFormat.encryptZipTo(out, passphrase, bundle)
            assertTrue(result.fileCount >= 2)
            assertTrue(result.plaintextBytesWritten > 0)
        } finally {
            passphrase.fill('\u0000')
        }

        val decryptedFiles = decryptExport(out.toByteArray(), "correct horse battery staple".toCharArray())
        assertTrue(decryptedFiles.containsKey("analytics.json"))
        assertTrue(decryptedFiles.containsKey("plugin_stats.csv"))

        val jsonText = decryptedFiles["analytics.json"]!!.toString(Charsets.UTF_8)
        val decoded = json.decodeFromString<AnalyticsExportBundle>(jsonText)
        assertEquals("Last 24h", decoded.metadata.windowLabel)
        assertEquals(1, decoded.pluginStats.size)
        assertEquals("plugin-a", decoded.pluginStats.first().pluginId)
    }

    private fun decryptExport(encryptedBytes: ByteArray, passphrase: CharArray): Map<String, ByteArray> {
        try {
            val dis = DataInputStream(ByteArrayInputStream(encryptedBytes))

            val magic = dis.readUTF()
            assertEquals(EncryptedAnalyticsExportFormat.MAGIC, magic)

            val version = dis.readInt()
            assertEquals(EncryptedAnalyticsExportFormat.FORMAT_VERSION, version)

            val kdfAlg = dis.readUTF()
            assertEquals(EncryptedAnalyticsExportFormat.PBKDF2_ALG, kdfAlg)

            val iterations = dis.readInt()
            val saltLen = dis.readInt()
            val salt = ByteArray(saltLen)
            dis.readFully(salt)

            val cipherAlg = dis.readUTF()
            assertEquals(EncryptedAnalyticsExportFormat.CIPHER_ALG, cipherAlg)

            val tagBits = dis.readInt()
            val ivLen = dis.readInt()
            val iv = ByteArray(ivLen)
            dis.readFully(iv)

            val key = EncryptedAnalyticsExportFormat.deriveKey(passphrase = passphrase, salt = salt, iterations = iterations)
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

