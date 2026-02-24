package com.resources.handmeasure.sdk.internal.ringsize

import android.graphics.PointF
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class ScaleEstimate(
    val mmPerPx: Double,
    val rectifiedWidthPx: Double,
    val rectifiedHeightPx: Double,
)

class ScaleEstimator(
    private val referenceWidthMm: Double = 85.60,
    private val referenceHeightMm: Double = 53.98,
) {
    fun estimate(card: CardDetection): ScaleEstimate? {
        if (card.cornersPx.size != 4) return null
        val ordered = card.cornersPx
        val widthPx = (distance(ordered[0], ordered[1]) + distance(ordered[2], ordered[3])) / 2.0
        val heightPx = (distance(ordered[0], ordered[3]) + distance(ordered[1], ordered[2])) / 2.0
        if (widthPx <= 1.0 || heightPx <= 1.0) return null

        val mmPerPx = ((referenceWidthMm / widthPx) + (referenceHeightMm / heightPx)) / 2.0
        return ScaleEstimate(mmPerPx = mmPerPx, rectifiedWidthPx = widthPx, rectifiedHeightPx = heightPx)
    }

    fun rectifyCard(
        srcGray: Mat,
        corners: List<PointF>,
        outWidth: Int,
        outHeight: Int,
    ): Mat {
        val src = MatOfPoint2f(
            Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
            Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
            Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
            Point(corners[3].x.toDouble(), corners[3].y.toDouble()),
        )
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outWidth.toDouble(), 0.0),
            Point(outWidth.toDouble(), outHeight.toDouble()),
            Point(0.0, outHeight.toDouble()),
        )
        val warp = Imgproc.getPerspectiveTransform(src, dst)
        val out = Mat()
        Imgproc.warpPerspective(srcGray, out, warp, Size(outWidth.toDouble(), outHeight.toDouble()))
        return out
    }

    private fun distance(a: PointF, b: PointF): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

