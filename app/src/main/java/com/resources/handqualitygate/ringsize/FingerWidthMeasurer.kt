package com.resources.handqualitygate.ringsize

import android.graphics.PointF
import org.opencv.core.Core
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
) {
    fun measure(frame: FramePacket, hand: HandDetection, mmPerPx: Double): FingerWidthResult? {
        if (hand.landmarks2dPx.size < 15) return null
        val jpeg = frame.toJpegBytes() ?: return null
        val gray = Imgcodecs.imdecode(MatOfByte(*jpeg), Imgcodecs.IMREAD_GRAYSCALE)
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

        val roiMat = Mat(gray, roi)
        val grad = Mat()
        Imgproc.Sobel(roiMat, grad, -1, 1, 0)
        val gradAbs = Mat()
        Core.convertScaleAbs(grad, gradAbs)

        val center = PointF((ringPoint.x - roi.x), (ringPoint.y - roi.y))
        val left = scanEdge(gradAbs, center, perp, -1)
        val right = scanEdge(gradAbs, center, perp, 1)

        if (left == null || right == null) return null
        val leftAbs = PointF(left.x + roi.x, left.y + roi.y)
        val rightAbs = PointF(right.x + roi.x, right.y + roi.y)
        val widthPx = distance(leftAbs, rightAbs)
        if (widthPx <= 1.0) return null

        return FingerWidthResult(
            widthPx = widthPx,
            widthMm = widthPx * mmPerPx,
            ringPointPx = ringPoint,
            leftEdgePx = leftAbs,
            rightEdgePx = rightAbs,
        )
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
