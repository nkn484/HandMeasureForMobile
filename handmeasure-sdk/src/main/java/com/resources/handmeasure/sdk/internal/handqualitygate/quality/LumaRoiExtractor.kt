package com.resources.handmeasure.sdk.internal.quality

import android.graphics.Rect
import androidx.camera.core.ImageProxy

object LumaRoiExtractor {
    fun downsampleToSquare(
        image: ImageProxy,
        roiPx: Rect,
        outSize: Int,
    ): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val frameW = image.width
        val frameH = image.height

        val left = roiPx.left.coerceIn(0, frameW - 1)
        val top = roiPx.top.coerceIn(0, frameH - 1)
        val right = roiPx.right.coerceIn(left + 1, frameW)
        val bottom = roiPx.bottom.coerceIn(top + 1, frameH)

        val roiW = (right - left).coerceAtLeast(1)
        val roiH = (bottom - top).coerceAtLeast(1)

        val out = ByteArray(outSize * outSize)
        var outIdx = 0
        for (y in 0 until outSize) {
            val srcY = top + (y * roiH) / outSize
            val srcRow = srcY * rowStride
            for (x in 0 until outSize) {
                val srcX = left + (x * roiW) / outSize
                val srcIdx = srcRow + srcX * pixelStride
                out[outIdx++] = buffer.get(srcIdx)
            }
        }
        return out
    }
}

