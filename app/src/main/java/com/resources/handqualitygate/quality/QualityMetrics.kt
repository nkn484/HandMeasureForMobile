package com.resources.handqualitygate.quality

import android.graphics.Rect
import android.util.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object QualityMetrics {
    data class ExposureStats(
        val mean: Double,
        val std: Double,
        val pctHigh: Double,
        val pctLow: Double,
    )

    fun computeBlurVoL(luma: ByteArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val idx = row + x
                val c = luma[idx].toInt() and 0xFF
                val l = luma[idx - 1].toInt() and 0xFF
                val r = luma[idx + 1].toInt() and 0xFF
                val u = luma[idx - width].toInt() and 0xFF
                val d = luma[idx + width].toInt() and 0xFF
                val lap = (u + d + l + r - 4 * c).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }

        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    fun computeMotionMAD(curr: ByteArray, prev: ByteArray): Double {
        if (curr.size != prev.size || curr.isEmpty()) return 0.0
        var sum = 0.0
        for (i in curr.indices) {
            val a = curr[i].toInt() and 0xFF
            val b = prev[i].toInt() and 0xFF
            sum += abs(a - b)
        }
        return sum / curr.size
    }

    fun computeExposureStats(luma: ByteArray): ExposureStats {
        if (luma.isEmpty()) return ExposureStats(0.0, 0.0, 0.0, 0.0)
        val n = luma.size.toDouble()
        var sum = 0.0
        var sumSq = 0.0
        var high = 0
        var low = 0
        for (b in luma) {
            val v = b.toInt() and 0xFF
            sum += v
            sumSq += v.toDouble() * v.toDouble()
            if (v > 250) high++
            if (v < 5) low++
        }
        val mean = sum / n
        val variance = max(0.0, (sumSq / n) - (mean * mean))
        val std = sqrt(variance)
        return ExposureStats(
            mean = mean,
            std = std,
            pctHigh = high / n,
            pctLow = low / n,
        )
    }

    fun computeRoiScore(roiRect: Rect, frameSize: Size): Float {
        val fw = frameSize.width.toFloat()
        val fh = frameSize.height.toFloat()
        if (fw <= 0f || fh <= 0f) return 0f
        val rw = roiRect.width().toFloat()
        val rh = roiRect.height().toFloat()
        if (rw <= 0f || rh <= 0f) return 0f

        val roiRatio = (rw * rh) / (fw * fh)
        val minTarget = 0.18f
        val maxTarget = 0.45f

        val sizeScore =
            when {
                roiRatio < minTarget -> roiRatio / minTarget
                roiRatio > maxTarget -> maxTarget / roiRatio
                else -> 1f
            }.coerceIn(0f, 1f)

        val marginX = 0.04f * fw
        val marginY = 0.04f * fh
        var edgePenalty = 1f
        if (roiRect.left <= marginX || roiRect.right >= (fw - marginX)) edgePenalty *= 0.6f
        if (roiRect.top <= marginY || roiRect.bottom >= (fh - marginY)) edgePenalty *= 0.6f

        return (sizeScore * edgePenalty).coerceIn(0f, 1f)
    }

    fun normalizeBlur(vol: Double, blurLow: Double, blurOk: Double): Float {
        if (blurOk <= blurLow) return 0f
        return ((vol - blurLow) / (blurOk - blurLow)).toFloat().coerceIn(0f, 1f)
    }

    fun normalizeMotion(mad: Double, motionLow: Double, motionHigh: Double): Float {
        if (motionHigh <= motionLow) return 0f
        val t = ((mad - motionLow) / (motionHigh - motionLow)).toFloat().coerceIn(0f, 1f)
        return (1f - t).coerceIn(0f, 1f)
    }

    fun normalizeExposure(
        stats: ExposureStats,
        minMean: Double,
        maxMean: Double,
        minStd: Double,
        pctClipMax: Double,
    ): Float {
        if (stats.pctHigh > pctClipMax || stats.pctLow > pctClipMax) return 0f
        if (maxMean <= minMean) return 0f

        val center = (minMean + maxMean) / 2.0
        val halfRange = (maxMean - minMean) / 2.0
        val meanDist = abs(stats.mean - center)
        val meanQ = (1.0 - min(1.0, meanDist / halfRange)).toFloat().coerceIn(0f, 1f)

        val stdQ = ((stats.std - minStd) / max(1.0, minStd)).toFloat().coerceIn(0f, 1f)
        return (meanQ * stdQ).coerceIn(0f, 1f)
    }
}

