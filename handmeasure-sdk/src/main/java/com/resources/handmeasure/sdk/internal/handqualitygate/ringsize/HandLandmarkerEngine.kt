package com.resources.handmeasure.sdk.internal.ringsize

import android.graphics.PointF

data class HandDetection(
    val landmarks2dPx: List<PointF>,
    val landmarkConfidences: List<Float>,
    val handedness: String,
    val confidence: Float,
)

interface HandLandmarkerEngine {
    fun detect(frame: FramePacket): HandDetection?
    fun detectLive(frame: FramePacket, timestampMs: Long): HandDetection? = detect(frame)
    fun getLatestDetection(): HandDetection? = null
}
