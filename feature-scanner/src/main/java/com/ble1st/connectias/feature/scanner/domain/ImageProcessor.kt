package com.ble1st.connectias.feature.scanner.domain

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageProcessor @Inject constructor() {

    init {
        // Ensure OpenCV is loaded
        // Note: Real apps should handle LoaderCallbackInterface.SUCCESS
        // For simplicity assuming static link or successful load for this snippet
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            // Handle error or rely on OpenCV Loader
        }
    }

    fun enhanceDocument(bitmap: Bitmap, mode: EnhancementMode): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val dest = Mat()

        when (mode) {
            EnhancementMode.GRAYSCALE -> {
                Imgproc.cvtColor(src, dest, Imgproc.COLOR_RGB2GRAY)
            }
            EnhancementMode.BLACK_AND_WHITE -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
                // Adaptive Thresholding for clear text (Magic Filter alike)
                // Increased blockSize to 31 and C to 15.0 to reduce noise and "hollow" text effect
                Imgproc.adaptiveThreshold(
                    gray, dest, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, 31, 15.0
                )
                gray.release()
            }
            EnhancementMode.COLOR_ENHANCE -> {
                // Simple sharpening / contrast boost
                // For now just return original or apply detail enhance
                src.copyTo(dest) 
                // Could implement DetailEnhance here if desired
            }
            EnhancementMode.ORIGINAL -> src.copyTo(dest)
        }

        val result = createBitmap(dest.cols(), dest.rows())
        Utils.matToBitmap(dest, result)
        
        src.release()
        dest.release()
        return result
    }
}

enum class EnhancementMode {
    ORIGINAL,
    GRAYSCALE,
    BLACK_AND_WHITE, // Best for OCR
    COLOR_ENHANCE
}
