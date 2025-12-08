package com.ble1st.connectias.feature.document.scan

import android.graphics.Bitmap
import android.graphics.PointF
import com.ble1st.connectias.feature.document.model.DocumentPage
import org.opencv.android.Utils
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import timber.log.Timber

class ImageProcessor {

    init {
        // Safe to call multiple times; returns false if not available
        OpenCVLoader.initDebug()
    }

    fun process(source: Bitmap): DocumentPage {
        val result = runCatching { detectAndWarp(source) }.getOrElse { error ->
            Timber.w(error, "Falling back to original bitmap after processing failure")
            return DocumentPage(bitmap = source, cropOutline = emptyList(), rotation = 0)
        }
        return result
    }

    private fun detectAndWarp(source: Bitmap): DocumentPage {
        val srcMat = Mat()
        Utils.bitmapToMat(source, srcMat)

        val gray = Mat()
        Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val edged = Mat()
        Imgproc.Canny(gray, edged, 75.0, 200.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val largest = contours.maxByOrNull { Imgproc.contourArea(it) }
        if (largest == null || Imgproc.contourArea(largest) < 1000) {
            Timber.i("No significant contour detected, returning original bitmap")
            return DocumentPage(bitmap = source, cropOutline = emptyList(), rotation = 0)
        }

        val peri = Imgproc.arcLength(MatOfPoint2f(*largest.toArray()), true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(MatOfPoint2f(*largest.toArray()), approx, 0.02 * peri, true)

        val points = approx.toArray()
        if (points.size != 4) {
            Timber.i("Detected contour is not a quadrilateral, returning original bitmap")
            return DocumentPage(bitmap = source, cropOutline = emptyList(), rotation = 0)
        }

        val ordered = orderPoints(points)
        val widthA = distance(ordered[0], ordered[1])
        val widthB = distance(ordered[2], ordered[3])
        val maxWidth = maxOf(widthA, widthB).toInt()

        val heightA = distance(ordered[0], ordered[3])
        val heightB = distance(ordered[1], ordered[2])
        val maxHeight = maxOf(heightA, heightB).toInt()

        val dstMat = Mat(maxHeight, maxWidth, CvType.CV_8UC3)
        val srcPoints = MatOfPoint2f(*ordered)
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth.toDouble(), 0.0),
            Point(maxWidth.toDouble(), maxHeight.toDouble()),
            Point(0.0, maxHeight.toDouble())
        )

        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        Imgproc.warpPerspective(srcMat, dstMat, transform, dstMat.size())

        val outBitmap = Bitmap.createBitmap(dstMat.cols(), dstMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dstMat, outBitmap)

        val outline = ordered.map { PointF(it.x.toFloat(), it.y.toFloat()) }
        return DocumentPage(bitmap = outBitmap, cropOutline = outline, rotation = 0)
    }

    private fun orderPoints(points: Array<Point>): Array<Point> {
        val sorted = points.sortedWith(compareBy({ it.x + it.y }, { it.x }))
        val topLeft = sorted.first()
        val bottomRight = sorted.last()
        val remaining = sorted.slice(1 until sorted.size - 1)
        val (topRight, bottomLeft) = remaining.partition { p -> p.x > p.y }.let {
            if (it.first.isNotEmpty() && it.second.isNotEmpty()) {
                Pair(it.first.maxBy { p -> p.x }, it.second.minBy { p -> p.x })
            } else Pair(remaining.first(), remaining.last())
        }
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun distance(a: Point, b: Point): Double {
        return kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    }
}
