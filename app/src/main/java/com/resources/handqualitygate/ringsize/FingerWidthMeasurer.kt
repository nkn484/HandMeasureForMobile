package com.resources.handqualitygate.ringsize

import android.graphics.PointF
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class FingerWidthResult(
    val widthPx: Double,
    val widthMm: Double,
    val ringPointPx: PointF,
    val leftEdgePx: PointF,
    val rightEdgePx: PointF,
)

class FingerWidthMeasurer(
    private val edgeThreshold: Double = 35.0,
    private val roiRadiusPx: Int = 80,
    private val minWidthMm: Double = 12.0,
    private val maxWidthMm: Double = 30.0,
    private val symmetryTolerance: Double = 0.25,
) {
    fun measure(frame: FramePacket, hand: HandDetection, mmPerPx: Double): FingerWidthResult? {
        if (hand.landmarks2dPx.size < 15) return null
        if (mmPerPx <= 0.0) return null
        val jpeg = frame.toJpegBytes() ?: return null
        val byteMat = MatOfByte(*jpeg)
        val gray = Imgcodecs.imdecode(byteMat, Imgcodecs.IMREAD_GRAYSCALE)
        var roiMat: Mat? = null
        var gradX: Mat? = null
        var gradY: Mat? = null
        var gradMag: Mat? = null
        var gradAbs: Mat? = null

        return try {
            if (gray.empty()) return null

            val mcp = hand.landmarks2dPx[13]
            val pip = hand.landmarks2dPx[14]
            val ringPoint =
                PointF(
                    mcp.x + (pip.x - mcp.x) * 0.4f,
                    mcp.y + (pip.y - mcp.y) * 0.4f,
                )

            val axis = PointF(pip.x - mcp.x, pip.y - mcp.y)
            val len = hypot(axis.x.toDouble(), axis.y.toDouble())
            if (len == 0.0) return null
            val dir = PointF((axis.x / len).toFloat(), (axis.y / len).toFloat())
            val perp = PointF(-dir.y, dir.x)

            val cx = ringPoint.x.toInt()
            val cy = ringPoint.y.toInt()
            val roi = Rect(
                (cx - roiRadiusPx).coerceAtLeast(0),
                (cy - roiRadiusPx).coerceAtLeast(0),
                (roiRadiusPx * 2).coerceAtMost(gray.cols() - (cx - roiRadiusPx).coerceAtLeast(0)),
                (roiRadiusPx * 2).coerceAtMost(gray.rows() - (cy - roiRadiusPx).coerceAtLeast(0)),
            )
            if (roi.width <= 0 || roi.height <= 0) return null

            val roiMatLocal = Mat(gray, roi).also { roiMat = it }
            val gradXLocal = Mat().also { gradX = it }
            val gradYLocal = Mat().also { gradY = it }
            val gradMagLocal = Mat().also { gradMag = it }
            val gradAbsLocal = Mat().also { gradAbs = it }

            Imgproc.Sobel(roiMatLocal, gradXLocal, CvType.CV_32F, 1, 0)
            Imgproc.Sobel(roiMatLocal, gradYLocal, CvType.CV_32F, 0, 1)
            Core.magnitude(gradXLocal, gradYLocal, gradMagLocal)
            Core.convertScaleAbs(gradMagLocal, gradAbsLocal)

            val center = PointF((ringPoint.x - roi.x), (ringPoint.y - roi.y))
            val left = scanEdge(gradAbsLocal, center, perp, -1)
            val right = scanEdge(gradAbsLocal, center, perp, 1)

            if (left == null || right == null) return null
            val leftAbs = PointF(left.x + roi.x, left.y + roi.y)
            val rightAbs = PointF(right.x + roi.x, right.y + roi.y)
            val widthPx = distance(leftAbs, rightAbs)
            if (widthPx <= 1.0) return null

            val widthMm = widthPx * mmPerPx
            if (widthMm < minWidthMm || widthMm > maxWidthMm) return null

            val leftDist = distance(ringPoint, leftAbs)
            val rightDist = distance(ringPoint, rightAbs)
            val denom = max(leftDist, rightDist)
            if (denom == 0.0) return null
            val symmetryScore = abs(leftDist - rightDist) / denom
            if (symmetryScore > symmetryTolerance) return null

            FingerWidthResult(
                widthPx = widthPx,
                widthMm = widthMm,
                ringPointPx = ringPoint,
                leftEdgePx = leftAbs,
                rightEdgePx = rightAbs,
            )
        } finally {
            gradAbs?.release()
            gradMag?.release()
            gradY?.release()
            gradX?.release()
            roiMat?.release()
            gray.release()
            byteMat.release()
        }
    }

    private fun scanEdge(
        grad: Mat,
        center: PointF,
        direction: PointF,
        sign: Int,
    ): PointF? {
        val maxSteps = min(grad.cols(), grad.rows()) / 2
        var best: PointF? = null
        for (step in 3 until maxSteps) {
            val x = (center.x + direction.x * step * sign).toInt()
            val y = (center.y + direction.y * step * sign).toInt()
            if (x !in 1 until grad.cols() - 1 || y !in 1 until grad.rows() - 1) break
            val v = grad.get(y, x)[0]
            if (v >= edgeThreshold) {
                best = PointF(x.toFloat(), y.toFloat())
                break
            }
        }
        return best
    }

    private fun distance(a: PointF, b: PointF): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return hypot(dx, dy)
    }
}
