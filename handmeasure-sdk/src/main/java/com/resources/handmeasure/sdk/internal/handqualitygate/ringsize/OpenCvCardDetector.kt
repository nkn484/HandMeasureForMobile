package com.resources.handmeasure.sdk.internal.ringsize

import android.graphics.PointF
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.core.CvType
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class OpenCvCardDetector(
    private val minAreaRatio: Double = 0.03,
    private val aspectTarget: Double = 85.60 / 53.98,
    private val aspectTolerance: Double = 0.18,
    private val minAngleScore: Float = 0.65f,
) : CardDetector {
    override fun detect(frame: FramePacket): CardDetection? {
        val matsToRelease = ArrayList<Mat>()
        val contours = ArrayList<MatOfPoint>()
        var mat: Mat? = null
        var matOfByte: MatOfByte? = null
        var fullLumaMat: Mat? = null
        var lumaMat: Mat? = null

        try {
            val proxy = frame.imageProxy
            if (proxy != null) {
                val luma = buildLumaMat(proxy) ?: return null
                fullLumaMat = luma.fullMat
                lumaMat = luma.croppedMat
                mat = lumaMat
            } else {
                val jpeg = frame.toJpegBytes() ?: return null
                matOfByte = MatOfByte(*jpeg)
                mat = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_GRAYSCALE)
                if (mat.empty()) return null
            }

            val blurred = Mat()
            matsToRelease += blurred
            Imgproc.GaussianBlur(mat, blurred, Size(5.0, 5.0), 0.0)

            val edges = Mat()
            matsToRelease += edges
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val matLocal = mat ?: return null
            val frameArea = (matLocal.width() * matLocal.height()).toDouble()
            var best: Candidate? = null

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < frameArea * minAreaRatio) continue

                val contourPoints = contour.toArray()
                val contour2f = MatOfPoint2f(*contourPoints)
                matsToRelease += contour2f
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                matsToRelease += approx
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                val points = approx.toArray()
                if (points.size != 4) continue

                val rectMat = MatOfPoint(*points)
                matsToRelease += rectMat
                val rect = Imgproc.boundingRect(rectMat)
                val corners = orderCorners(points)
                val aspect = estimateAspect(corners)
                val aspectScore = aspectScore(aspect)
                if (aspectScore <= 0f) continue

                val angleScore = angleScore(corners)
                if (angleScore < minAngleScore) continue

                val areaScore = (area / frameArea).toFloat().coerceIn(0f, 1f)
                val cutoffPenalty = cutoffPenalty(rect, matLocal.width(), matLocal.height())

                val confidence =
                    (0.5f * aspectScore + 0.3f * angleScore + 0.2f * areaScore) * cutoffPenalty

                val candidate = Candidate(corners, aspectScore, angleScore, areaScore, confidence)
                if (best == null || candidate.confidence > best.confidence) {
                    best = candidate
                }
            }

            return best?.toDetection()
        } finally {
            contours.forEach { it.release() }
            matsToRelease.forEach { it.release() }
            releaseIfDistinct(lumaMat, fullLumaMat)
            releaseIfDistinct(mat, lumaMat)
            fullLumaMat?.release()
            matOfByte?.release()
        }
    }

    private data class Candidate(
        val corners: List<PointF>,
        val aspectScore: Float,
        val angleScore: Float,
        val areaScore: Float,
        val confidence: Float,
    ) {
        fun toDetection() =
            CardDetection(
                cornersPx = corners,
                aspectScore = aspectScore,
                angleScore = angleScore,
                areaScore = areaScore,
                confidence = confidence,
            )
    }

    private fun orderCorners(points: Array<Point>): List<PointF> {
        val pts = points.map { PointF(it.x.toFloat(), it.y.toFloat()) }
        val sum = pts.map { it.x + it.y }
        val diff = pts.map { it.x - it.y }
        val topLeft = pts[sum.indexOf(sum.minOrNull()!!)]
        val bottomRight = pts[sum.indexOf(sum.maxOrNull()!!)]
        val topRight = pts[diff.indexOf(diff.maxOrNull()!!)]
        val bottomLeft = pts[diff.indexOf(diff.minOrNull()!!)]
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun estimateAspect(corners: List<PointF>): Double {
        val w1 = distance(corners[0], corners[1])
        val w2 = distance(corners[2], corners[3])
        val h1 = distance(corners[0], corners[3])
        val h2 = distance(corners[1], corners[2])
        val width = (w1 + w2) / 2.0
        val height = (h1 + h2) / 2.0
        if (height <= 0.0) return 0.0
        return width / height
    }

    private fun aspectScore(aspect: Double): Float {
        val diff = abs(aspect - aspectTarget) / aspectTarget
        return (1.0 - diff / aspectTolerance).toFloat().coerceIn(0f, 1f)
    }

    private fun angleScore(corners: List<PointF>): Float {
        val angles = mutableListOf<Double>()
        for (i in corners.indices) {
            val prev = corners[(i + 3) % 4]
            val curr = corners[i]
            val next = corners[(i + 1) % 4]
            val angle = angle(prev, curr, next)
            angles.add(angle)
        }
        val score = angles.map { (1.0 - abs(it - 90.0) / 90.0).coerceIn(0.0, 1.0) }
        return score.average().toFloat()
    }

    private fun angle(a: PointF, b: PointF, c: PointF): Double {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val dot = abx * cbx + aby * cby
        val mag1 = sqrt((abx * abx + aby * aby).toDouble())
        val mag2 = sqrt((cbx * cbx + cby * cby).toDouble())
        if (mag1 * mag2 == 0.0) return 0.0
        val cos = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(Math.acos(cos))
    }

    private fun distance(a: PointF, b: PointF): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    private fun cutoffPenalty(rect: Rect, width: Int, height: Int): Float {
        val margin = 0.02f
        val box = RectF(
            rect.x.toFloat() / width,
            rect.y.toFloat() / height,
            (rect.x + rect.width).toFloat() / width,
            (rect.y + rect.height).toFloat() / height,
        )
        val cut =
            box.left < margin ||
                box.top < margin ||
                box.right > (1f - margin) ||
                box.bottom > (1f - margin)
        return if (cut) 0.6f else 1f
    }

    private data class LumaMatResult(
        val fullMat: Mat,
        val croppedMat: Mat,
    )

    private fun buildLumaMat(proxy: ImageProxy): LumaMatResult? {
        if (proxy.planes.isEmpty()) return null
        val yPlane = proxy.planes[0]
        val buffer = yPlane.buffer
        val width = proxy.width
        val height = proxy.height
        val rowStride = yPlane.rowStride
        if (width <= 0 || height <= 0 || rowStride <= 0) return null

        buffer.rewind()
        val fullMat = Mat(height, rowStride, CvType.CV_8UC1, buffer)
        val croppedMat =
            if (rowStride == width) {
                fullMat
            } else {
                fullMat.colRange(0, width)
            }
        return LumaMatResult(fullMat = fullMat, croppedMat = croppedMat)
    }

    private fun releaseIfDistinct(
        primary: Mat?,
        secondary: Mat?,
    ) {
        if (primary == null) return
        if (primary === secondary) return
        primary.release()
    }
}
