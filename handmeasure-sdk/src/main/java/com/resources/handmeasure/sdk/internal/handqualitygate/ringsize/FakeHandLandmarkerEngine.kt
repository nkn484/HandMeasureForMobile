package com.resources.handmeasure.sdk.internal.ringsize

import android.graphics.BitmapFactory
import android.graphics.PointF

class FakeHandLandmarkerEngine : HandLandmarkerEngine {
    override fun detect(frame: FramePacket): HandDetection? {
        val jpeg = frame.toJpegBytes() ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return null

        val landmarks = mutableListOf<PointF>()
        for (i in 0 until 21) {
            val x = w * 0.4f + (i % 4) * (w * 0.05f)
            val y = h * 0.5f - (i / 4) * (h * 0.04f)
            landmarks.add(PointF(x, y))
        }

        // Ensure ring finger MCP/PIP (index 13,14) are aligned.
        landmarks[13] = PointF(w * 0.52f, h * 0.52f)
        landmarks[14] = PointF(w * 0.53f, h * 0.44f)
        landmarks[15] = PointF(w * 0.54f, h * 0.36f)
        landmarks[16] = PointF(w * 0.55f, h * 0.30f)

        val confidences = List(21) { 1.0f }
        return HandDetection(
            landmarks2dPx = landmarks,
            landmarkConfidences = confidences,
            handedness = "Right",
            confidence = 0.9f,
        )
    }

    override fun detectLive(frame: FramePacket, timestampMs: Long): HandDetection? = detect(frame)

    override fun getLatestDetection(): HandDetection? = null
}
