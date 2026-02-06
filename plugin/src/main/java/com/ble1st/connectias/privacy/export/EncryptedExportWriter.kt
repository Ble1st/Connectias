package com.ble1st.connectias.privacy.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream

/**
 * Writes an always-encrypted GDPR export to a SAF Uri.
 *
 * Format:
 * - Custom header (magic + parameters)
 * - AES-256-GCM encrypted ZIP payload containing JSON + CSV files
 *
 * The encryption is passphrase-based to allow portability outside of the device.
 */
object EncryptedExportWriter {

    data class Result(
        val bytesWritten: Long,
        val fileCount: Int
    )

    suspend fun writeEncryptedZip(
        context: Context,
        outputUri: Uri,
        passphrase: CharArray,
        exportBundle: PrivacyExportBundle
    ): Result = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver

        resolver.openOutputStream(outputUri, "w")?.use { rawOut ->
            BufferedOutputStream(rawOut).use { bufferedOut ->
                val res = EncryptedZipExportFormat.encryptZipTo(
                    outputStream = bufferedOut,
                    passphrase = passphrase,
                    exportBundle = exportBundle
                )
                return@withContext Result(bytesWritten = res.plaintextBytesWritten, fileCount = res.fileCount)
            }
        } ?: error("Failed to open output stream for Uri: $outputUri")
    }
}

