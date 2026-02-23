package com.resources.handqualitygate.quality

import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import com.resources.handqualitygate.tracking.HandObservation
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QualityGateEngineTest {
    @Test
    fun evaluate_addsNoHandReasons_whenHandMissing() {
        val engine = QualityGateEngine(QualityGateConfig(requireCardForCapture = false))
        val result =
            engine.evaluate(
                timestampMs = 1_000L,
                lumaRoi = ByteArray(160 * 160) { 128.toByte() },
                roiRectPx = Rect(100, 100, 280, 280),
                frameSize = Size(640, 480),
                observation = observation(hasHand = false, confidence = 0f),
            )

        assertTrue(result.reasonsFail.contains("NO_HAND"))
        assertTrue(result.reasonsFail.contains("LOW_CONF"))
    }

    @Test
    fun evaluate_marksMotionHigh_onLargeFrameDifference() {
        val engine = QualityGateEngine(QualityGateConfig(requireCardForCapture = false))
        val obs = observation(hasHand = true, confidence = 1f)
        val dark = ByteArray(160 * 160) { 0.toByte() }
        val bright = ByteArray(160 * 160) { 255.toByte() }

        engine.evaluate(
            timestampMs = 1_000L,
            lumaRoi = dark,
            roiRectPx = Rect(80, 60, 360, 340),
            frameSize = Size(640, 480),
            observation = obs,
        )
        val result =
            engine.evaluate(
                timestampMs = 1_070L,
                lumaRoi = bright,
                roiRectPx = Rect(80, 60, 360, 340),
                frameSize = Size(640, 480),
                observation = obs,
            )

        assertTrue(result.reasonsFail.contains("MOTION_HIGH"))
    }

    @Test
    fun evaluate_marksExposureClipHigh_whenFrameOverexposed() {
        val engine = QualityGateEngine(QualityGateConfig(requireCardForCapture = false))
        val result =
            engine.evaluate(
                timestampMs = 1_000L,
                lumaRoi = ByteArray(160 * 160) { 255.toByte() },
                roiRectPx = Rect(80, 60, 360, 340),
                frameSize = Size(640, 480),
                observation = observation(hasHand = true, confidence = 1f),
            )

        assertTrue(result.reasonsFail.contains("EXPOSURE_CLIP_HIGH"))
    }

    private fun observation(hasHand: Boolean, confidence: Float): HandObservation {
        return HandObservation(
            roiNormalized = RectF(0.1f, 0.1f, 0.6f, 0.7f),
            roiPixel = Rect(80, 60, 360, 340),
            confidence = confidence,
            hasHand = hasHand,
        )
    }
}
