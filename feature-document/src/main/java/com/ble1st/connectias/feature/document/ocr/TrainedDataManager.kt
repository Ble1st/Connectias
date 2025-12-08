package com.ble1st.connectias.feature.document.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

class TrainedDataManager(private val context: Context) {

    private val tessDir: File
        get() = File(context.filesDir, "tessdata")

    suspend fun ensureLanguages(languages: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (!tessDir.exists() && !tessDir.mkdirs()) {
            Timber.e("Failed to create tessdata directory at ${tessDir.absolutePath}")
            return@withContext false
        }
        var allPresent = true
        languages.forEach { lang ->
            val target = File(tessDir, "$lang.traineddata")
            if (!target.exists()) {
                val copied = copyFromAssets(lang, target)
                if (!copied) {
                    Timber.w("Missing traineddata for $lang; OCR will be skipped")
                    allPresent = false
                }
            }
        }
        allPresent
    }

    fun dataPath(): String = context.filesDir.absolutePath + File.separator

    private fun copyFromAssets(lang: String, target: File): Boolean {
        return try {
            context.assets.open("tessdata/$lang.traineddata").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Timber.i("Copied tessdata for $lang from assets")
            true
        } catch (io: IOException) {
            false
        }
    }
}
