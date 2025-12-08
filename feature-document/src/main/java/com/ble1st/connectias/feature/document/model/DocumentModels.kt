package com.ble1st.connectias.feature.document.model

import android.graphics.Bitmap
import android.graphics.RectF

data class DocumentPage(
    val bitmap: Bitmap,
    val cropOutline: List<android.graphics.PointF> = emptyList(),
    val rotation: Int = 0
)

data class OcrBlock(
    val boundingBox: RectF,
    val text: String,
    val confidence: Float
)

data class OcrPageResult(
    val text: String,
    val blocks: List<OcrBlock>
)

data class PdfPage(
    val bitmap: Bitmap,
    val ocr: OcrPageResult?
)
