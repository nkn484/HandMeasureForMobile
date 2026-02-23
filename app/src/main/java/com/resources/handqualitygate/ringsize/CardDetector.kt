package com.resources.handqualitygate.ringsize

import android.graphics.PointF

data class CardDetection(
    val cornersPx: List<PointF>,
    val aspectScore: Float,
    val angleScore: Float,
    val areaScore: Float,
    val confidence: Float,
)

interface CardDetector {
    fun detect(frame: FramePacket): CardDetection?
}

