package com.resources.handqualitygate.tracking

import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.resources.handqualitygate.ringsize.FramePacket
import com.resources.handqualitygate.ringsize.HandLandmarkerEngine
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class MediaPipeHandTracker(
    private val handEngine: HandLandmarkerEngine,
    private val bboxPaddingRatio: Float = 0.18f,
) : HandTracker {
    override fun observe(image: ImageProxy): HandObservation {
        val timestampMs = TimeUnit.NANOSECONDS.toMillis(image.imageInfo.timestamp)
        val framePacket = FramePacket(timestampMs = timestampMs, qualityScore = 0f, imageProxy = image)
        val detection = handEngine.detect(framePacket) ?: return emptyObservation(image.width, image.height)
        if (detection.landmarks2dPx.isEmpty()) return emptyObservation(image.width, image.height)

        val minX = detection.landmarks2dPx.minOf { it.x }
        val maxX = detection.landmarks2dPx.maxOf { it.x }
        val minY = detection.landmarks2dPx.minOf { it.y }
        val maxY = detection.landmarks2dPx.maxOf { it.y }

        val width = max(1f, maxX - minX)
        val height = max(1f, maxY - minY)
        val padX = width * bboxPaddingRatio
        val padY = height * bboxPaddingRatio

        val left = clampInt((minX - padX).toInt(), 0, image.width - 1)
        val top = clampInt((minY - padY).toInt(), 0, image.height - 1)
        val right = clampInt((maxX + padX).toInt(), left + 1, image.width)
        val bottom = clampInt((maxY + padY).toInt(), top + 1, image.height)

        val roiPx = Rect(left, top, right, bottom)
        val roiNorm =
            RectF(
                roiPx.left.toFloat() / image.width.toFloat(),
                roiPx.top.toFloat() / image.height.toFloat(),
                roiPx.right.toFloat() / image.width.toFloat(),
                roiPx.bottom.toFloat() / image.height.toFloat(),
            )

        return HandObservation(
            roiNormalized = roiNorm,
            roiPixel = roiPx,
            confidence = detection.confidence.coerceIn(0f, 1f),
            hasHand = true,
        )
    }

    private fun emptyObservation(frameW: Int, frameH: Int): HandObservation {
        val halfW = max(1, (frameW * 0.4f).toInt())
        val halfH = max(1, (frameH * 0.4f).toInt())
        val left = (frameW - halfW) / 2
        val top = (frameH - halfH) / 2
        val roiPx = Rect(left, top, min(frameW, left + halfW), min(frameH, top + halfH))
        val roiNorm =
            RectF(
                roiPx.left.toFloat() / frameW.toFloat(),
                roiPx.top.toFloat() / frameH.toFloat(),
                roiPx.right.toFloat() / frameW.toFloat(),
                roiPx.bottom.toFloat() / frameH.toFloat(),
            )
        return HandObservation(
            roiNormalized = roiNorm,
            roiPixel = roiPx,
            confidence = 0f,
            hasHand = false,
        )
    }

    private fun clampInt(value: Int, minValue: Int, maxValue: Int): Int {
        if (minValue >= maxValue) return minValue
        return value.coerceIn(minValue, maxValue)
    }
}
