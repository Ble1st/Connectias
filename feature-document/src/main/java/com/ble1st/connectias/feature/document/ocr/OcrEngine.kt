package com.ble1st.connectias.feature.document.ocr

import android.graphics.Rect
import com.ble1st.connectias.feature.document.model.DocumentPage
import com.ble1st.connectias.feature.document.model.OcrBlock
import com.ble1st.connectias.feature.document.model.OcrPageResult
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class OcrEngine(
    private val trainedDataManager: TrainedDataManager
) {

    suspend fun runOcr(
        page: DocumentPage,
        languages: List<String> = listOf("eng", "deu")
    ): OcrPageResult? = withContext(Dispatchers.Default) {
        val langsReady = trainedDataManager.ensureLanguages(languages)
        if (!langsReady) {
            Timber.w("Skipping OCR because trained data is missing: $languages")
            return@withContext null
        }

        val api = TessBaseAPI()
        val langCombined = languages.joinToString("+")
        return@withContext try {
            api.init(trainedDataManager.dataPath(), langCombined)
            api.setImage(page.bitmap)
            val text = api.utF8Text ?: ""
            val blocks = collectBlocks(api)
            OcrPageResult(text = text, blocks = blocks)
        } catch (e: Exception) {
            Timber.e(e, "OCR failed")
            null
        } finally {
            api.end()
        }
    }

    private fun collectBlocks(api: TessBaseAPI): List<OcrBlock> {
        val result = mutableListOf<OcrBlock>()
        val iterator = api.resultIterator ?: return result
        val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
        iterator.begin()
        do {
            val box = iterator.getBoundingBox(level)
            val text = iterator.getUTF8Text(level) ?: ""
            val confidence = iterator.confidence(level) / 100f
            if (box != null && box.size >= 4 && text.isNotBlank()) {
                val rect = Rect(box[0], box[1], box[2], box[3])
                result.add(
                    OcrBlock(
                        boundingBox = android.graphics.RectF(rect),
                        text = text,
                        confidence = confidence
                    )
                )
            }
        } while (iterator.next(level))
        return result
    }
}
