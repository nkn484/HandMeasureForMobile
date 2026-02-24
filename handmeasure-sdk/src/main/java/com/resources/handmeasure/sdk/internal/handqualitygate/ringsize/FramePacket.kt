package com.resources.handmeasure.sdk.internal.ringsize

import androidx.camera.core.ImageProxy
import com.resources.handmeasure.sdk.internal.camera.ImageUtils

data class FramePacket(
    val timestampMs: Long,
    val qualityScore: Float,
    val jpegBytes: ByteArray? = null,
    val imageProxy: ImageProxy? = null,
) {
    fun toJpegBytes(): ByteArray? {
        jpegBytes?.let { return it }
        val proxy = imageProxy ?: return null
        return ImageUtils.imageProxyToJpeg(proxy, quality = 90)
    }
}

