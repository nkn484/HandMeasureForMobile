package com.resources.handmeasure.sdk.internal.ringsize

import androidx.camera.core.ImageProxy
import com.resources.handmeasure.sdk.internal.camera.ImageUtils

data class FramePacket(
    val timestampMs: Long,
    val qualityScore: Float,
    val jpegBytes: ByteArray? = null,
    val imageProxy: ImageProxy? = null,
) {
    @Volatile
    private var cachedJpeg: ByteArray? = null

    fun toJpegBytes(): ByteArray? {
        jpegBytes?.let { return it }
        cachedJpeg?.let { return it }
        val proxy = imageProxy ?: return null
        val encoded = ImageUtils.imageProxyToJpeg(proxy, quality = 90)
        cachedJpeg = encoded
        return encoded
    }
}

