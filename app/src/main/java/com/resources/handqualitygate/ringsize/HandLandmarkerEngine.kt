package com.resources.handqualitygate.ringsize

import android.graphics.PointF

data class HandDetection(
    val landmarks2dPx: List<PointF>,
    val landmarkConfidences: List<Float>,
    val handedness: String,
    val confidence: Float,
)

interface HandLandmarkerEngine {
    fun detect(frame: FramePacket): HandDetection?
}

