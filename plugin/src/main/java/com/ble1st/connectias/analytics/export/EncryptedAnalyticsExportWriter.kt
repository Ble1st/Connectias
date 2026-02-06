package com.ble1st.connectias.analytics.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream

object EncryptedAnalyticsExportWriter {
    data class Result(
        val bytesWritten: Long,
        val fileCount: Int
    )

    suspend fun writeEncryptedZip(
        context: Context,
        outputUri: Uri,
        passphrase: CharArray,
        exportBundle: AnalyticsExportBundle
    ): Result = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        resolver.openOutputStream(outputUri, "w")?.use { rawOut ->
            BufferedOutputStream(rawOut).use { bufferedOut ->
                val res = EncryptedAnalyticsExportFormat.encryptZipTo(
                    outputStream = bufferedOut,
                    passphrase = passphrase,
                    exportBundle = exportBundle
                )
                Result(bytesWritten = res.plaintextBytesWritten, fileCount = res.fileCount)
            }
        } ?: error("Failed to open output stream for Uri: $outputUri")
    }
}

