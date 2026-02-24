package com.resources.handmeasure.sdk.internal.tracking

import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt

class FakeHandTracker : HandTracker {
    override fun observe(image: ImageProxy): HandObservation {
        val frameW = image.width
        val frameH = image.height
        val roiW = (0.55f * frameW).roundToInt().coerceAtLeast(1)
        val roiH = (0.55f * frameH).roundToInt().coerceAtLeast(1)

        val left = ((frameW - roiW) / 2f).roundToInt().coerceIn(0, frameW - 1)
        val top = ((frameH - roiH) / 2f).roundToInt().coerceIn(0, frameH - 1)
        val right = (left + roiW).coerceAtMost(frameW)
        val bottom = (top + roiH).coerceAtMost(frameH)

        val roiPx = Rect(left, top, right, bottom)
        val roiNorm =
            RectF(
                roiPx.left.toFloat() / frameW,
                roiPx.top.toFloat() / frameH,
                roiPx.right.toFloat() / frameW,
                roiPx.bottom.toFloat() / frameH,
            )

        return HandObservation(
            roiNormalized = roiNorm,
            roiPixel = roiPx,
            confidence = 1.0f,
            hasHand = true,
        )
    }
}

